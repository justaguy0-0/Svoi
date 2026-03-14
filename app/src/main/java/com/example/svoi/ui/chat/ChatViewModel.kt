package com.example.svoi.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.CacheManager.CachedChatInfo
import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TypingInfo(val userId: String, val displayName: String, val status: String = "typing")
data class StagedMedia(val uri: Uri, val isVideo: Boolean)

sealed class VoiceRecordState {
    object Idle : VoiceRecordState()
    data class Recording(val isLocked: Boolean = false) : VoiceRecordState()
}

data class VoicePlayState(
    val messageId: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val messageRepo = app.messageRepository
    private val chatRepo = app.chatRepository
    private val userRepo = app.userRepository
    private val authRepo = app.authRepository
    private val cache = app.cacheManager

    private val _messages = MutableStateFlow<List<MessageUiItem>>(emptyList())
    val messages: StateFlow<List<MessageUiItem>> = _messages

    private val _chat = MutableStateFlow<Chat?>(null)
    val chat: StateFlow<Chat?> = _chat

    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup

    private val _chatName = MutableStateFlow("")
    val chatName: StateFlow<String> = _chatName

    private val _memberCount = MutableStateFlow(0)
    val memberCount: StateFlow<Int> = _memberCount

    private val _otherUserPresence = MutableStateFlow<UserPresence?>(null)
    val otherUserPresence: StateFlow<UserPresence?> = _otherUserPresence

    private val _pinnedMessage = MutableStateFlow<PinnedMessage?>(null)
    val pinnedMessage: StateFlow<PinnedMessage?> = _pinnedMessage

    private val _pinnedMessageContent = MutableStateFlow<Message?>(null)
    val pinnedMessageContent: StateFlow<Message?> = _pinnedMessageContent

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId

    private val _scrollToMessageEvent = MutableStateFlow<String?>(null)
    val scrollToMessageEvent: StateFlow<String?> = _scrollToMessageEvent

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    /** Media (photos+videos) staged before sending */
    private val _stagedMedia = MutableStateFlow<List<StagedMedia>>(emptyList())
    val stagedMedia: StateFlow<List<StagedMedia>> = _stagedMedia

    /** Upload progress (0..1) for each staged item while uploading */
    private val _uploadProgresses = MutableStateFlow<List<Float>>(emptyList())
    val uploadProgresses: StateFlow<List<Float>> = _uploadProgresses

    private val _chatsForForward = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chatsForForward: StateFlow<List<ChatListItem>> = _chatsForForward

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // true while fetching from network (for subtitle "Обновление...")
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Incremented to signal scroll (initial entry — may go to unread divider)
    private val _scrollToBottomEvent = MutableStateFlow(0)
    val scrollToBottomEvent: StateFlow<Int> = _scrollToBottomEvent

    // Incremented when user sends own message — always scrolls to absolute bottom
    private val _scrollToOwnMessageEvent = MutableStateFlow(0)
    val scrollToOwnMessageEvent: StateFlow<Int> = _scrollToOwnMessageEvent

    // Index of first unread message for the separator (-1 = none)
    private val _firstUnreadIndex = MutableStateFlow(-1)
    val firstUnreadIndex: StateFlow<Int> = _firstUnreadIndex

    // Currently typing users (excluding self)
    private val _typingUsers = MutableStateFlow<List<TypingInfo>>(emptyList())
    val typingUsers: StateFlow<List<TypingInfo>> = _typingUsers

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val currentUserId get() = authRepo.currentUserId() ?: ""

    /** Becomes true when the group chat is deleted externally — screen must close */
    private val _isChatDeleted = MutableStateFlow(false)
    val isChatDeleted: StateFlow<Boolean> = _isChatDeleted

    /** Количество сообщений, которые пользователь видел в момент последнего markAsRead */
    private val _lastSeenMsgCount = MutableStateFlow(0)
    val lastSeenMsgCount: StateFlow<Int> = _lastSeenMsgCount

    /** Mute state for this chat (notifications) */
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    /** True when partner left the personal chat (only current user remains) */
    private val _isPartnerLeft = MutableStateFlow(false)
    val isPartnerLeft: StateFlow<Boolean> = _isPartnerLeft

    /** IDs of messages currently playing their entrance animation */
    private val _animatingMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val animatingMessageIds: StateFlow<Set<String>> = _animatingMessageIds

    private var chatId: String = ""
    private val profileCache = mutableMapOf<String, Profile>()
    private var otherUserIdVal: String? = null
    private val _otherUserId = MutableStateFlow<String?>(null)
    val otherUserId: StateFlow<String?> = _otherUserId
    private var lastKnownMessageId: String? = null
    private var typingJob: Job? = null
    private val activeUploadCount = AtomicInteger(0)
    private var historyFrom: String? = null  // null = see all; timestamp = restricted to messages after join

    // ── Voice recording ───────────────────────────────────────────────────────
    private val voiceRecorder = VoiceRecorder(getApplication())
    private val _voiceRecordState = MutableStateFlow<VoiceRecordState>(VoiceRecordState.Idle)
    val voiceRecordState: StateFlow<VoiceRecordState> = _voiceRecordState
    private val _voiceElapsedMs = MutableStateFlow(0L)
    val voiceElapsedMs: StateFlow<Long> = _voiceElapsedMs
    private var voiceTimerJob: Job? = null

    // ── Voice playback ────────────────────────────────────────────────────────
    private var voiceMediaPlayer: android.media.MediaPlayer? = null
    private val _voicePlayState = MutableStateFlow<VoicePlayState?>(null)
    val voicePlayState: StateFlow<VoicePlayState?> = _voicePlayState
    private var voiceProgressJob: Job? = null

    private fun dismissChatNotification(chatId: String) {
        val nm = getApplication<SvoiApp>()
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(com.example.svoi.SvoiFirebaseMessagingService.notificationIdForChat(chatId))
        // Cancel group summary if no other chat notifications remain
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val remaining = nm.activeNotifications.filter {
                it.id != com.example.svoi.SvoiFirebaseMessagingService.SUMMARY_ID
            }
            if (remaining.isEmpty()) {
                nm.cancel(com.example.svoi.SvoiFirebaseMessagingService.SUMMARY_ID)
            }
        }
    }

    fun init(chatId: String) {
        if (this.chatId == chatId) return
        this.chatId = chatId

        dismissChatNotification(chatId)

        viewModelScope.launch {
            val cachedInfo     = cache.loadChatInfo(chatId)
            val cachedMessages = cache.loadMessages(chatId)
            val cachedProfiles = cache.loadProfileMap()
            val cachedPinned   = cache.loadPinnedContent(chatId)

            // "Full cache" = we have everything needed to show a stable, final-looking screen.
            // cachedPinned != null means pinned state was saved previously (even if null = no pinned).
            val hasFullCache = cachedInfo != null && cachedMessages != null && cachedPinned != null

            if (hasFullCache) {
                // ── FAST PATH: show everything at once, no jumps ──────────────────
                cachedProfiles.forEach { profileCache[it.key] = it.value }

                _chatName.value    = cachedInfo!!.name
                _isGroup.value     = cachedInfo.isGroup
                _memberCount.value = cachedInfo.memberCount
                otherUserIdVal     = cachedInfo.otherUserId
                _otherUserId.value = cachedInfo.otherUserId

                // Set pinned BEFORE messages so the banner height is already stable
                // when LazyColumn does its first layout pass.
                _pinnedMessage.value        = cachedPinned!!.pinnedMessage
                _pinnedMessageContent.value = cachedPinned.messageContent

                _messages.value = buildUiItems(cachedMessages!!)
                lastKnownMessageId = cachedMessages.lastOrNull()?.id

                _isLoading.value = false
                _scrollToBottomEvent.value++   // single, stable scroll

                // ── Silent background refresh — NO extra scroll ───────────────────
                loadChatInfo()
                loadPinnedMessage()
                loadMessages(scrollAfter = false)
                // Initialize badge: count from first unread (don't mark all as seen yet)
                _lastSeenMsgCount.value = if (_firstUnreadIndex.value >= 0) _firstUnreadIndex.value else _messages.value.size
                sendReadReceipts()
            } else {
                // ── SLOW PATH: first visit or incomplete cache — show spinner ─────
                if (cachedInfo != null) {
                    // At least show the chat name in the TopAppBar while loading
                    _chatName.value    = cachedInfo.name
                    _isGroup.value     = cachedInfo.isGroup
                    _memberCount.value = cachedInfo.memberCount
                }
                _isLoading.value = true

                loadChatInfo()
                loadPinnedMessage()
                loadMessages(scrollAfter = false)
                // Initialize badge: count from first unread (don't mark all as seen yet)
                _lastSeenMsgCount.value = if (_firstUnreadIndex.value >= 0) _firstUnreadIndex.value else _messages.value.size
                sendReadReceipts()

                _isLoading.value = false
                _scrollToBottomEvent.value++   // single, stable scroll after full load
            }

            // Clear unread separator after 5 s
            if (_firstUnreadIndex.value >= 0) {
                viewModelScope.launch {
                    delay(5_000L)
                    _firstUnreadIndex.value = -1
                }
            }

            observeNewMessages()
            observeUpdatedMessages()
            observeReadReceipts()
            startTypingPolling()
            startChatDeletionWatch()
        }
    }

    /** Poll every 8s to detect group deletion or partner leaving a personal chat */
    private fun startChatDeletionWatch() {
        viewModelScope.launch {
            while (true) {
                delay(8_000L)
                if (_isGroup.value) {
                    val exists = chatRepo.getChat(chatId) != null
                    if (!exists) {
                        _isChatDeleted.value = true
                        break
                    }
                } else {
                    // Personal chat: check if partner soft-deleted the chat (left_at set)
                    val members = chatRepo.getChatMembers(chatId)
                    val otherMember = members.firstOrNull { it.userId != currentUserId }
                    if (otherMember?.leftAt != null) {
                        _isPartnerLeft.value = true
                    }
                }
            }
        }
    }

    private fun buildUiItems(raw: List<Message>): List<MessageUiItem> {
        val myId = currentUserId
        val messageMap = raw.associateBy { it.id }
        return raw.map { msg ->
            val replyMsg = msg.replyToId?.let { messageMap[it] }
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = msg.senderId == myId,
                isRead = false,
                replyToMessage = replyMsg,
                replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] }
            )
        }
    }

    private suspend fun loadChatInfo() {
        val chat = chatRepo.getChat(chatId) ?: return
        _chat.value = chat
        _isGroup.value = chat.type == "group"

        val members = chatRepo.getChatMembers(chatId)
        val profiles = userRepo.getProfiles(members.map { it.userId })
        profiles.forEach { profileCache[it.id] = it }

        val myId = currentUserId
        // Fetch historyFrom for the current user (only once — historyFrom never changes after join)
        if (historyFrom == null) {
            historyFrom = members.firstOrNull { it.userId == myId }?.historyFrom
        }

        // Load mute state from DB and sync to local prefs
        val myMembership = members.firstOrNull { it.userId == myId }
        val mutedInDb = myMembership?.muted == true
        _isMuted.value = mutedInDb
        app.themeManager.setChatMuted(chatId, mutedInDb)

        // Count only active (not left) members
        _memberCount.value = members.count { it.leftAt == null }

        if (chat.type == "personal") {
            // Partner left if their left_at is set
            val otherMember = members.firstOrNull { it.userId != myId }
            if (otherMember?.leftAt != null) {
                _isPartnerLeft.value = true
            }
            val other = profiles.firstOrNull { it.id != myId }
            _chatName.value = other?.displayName ?: "Пользователь"
            other?.let { otherProfile ->
                otherUserIdVal = otherProfile.id
                _otherUserId.value = otherProfile.id
                startPresencePolling(otherProfile.id)
            }
        } else {
            _chatName.value = chat.name ?: "Группа"
        }

        cache.saveChatInfo(CachedChatInfo(
            chatId = chatId,
            name = _chatName.value,
            isGroup = _isGroup.value,
            memberCount = _memberCount.value,
            otherUserId = otherUserIdVal
        ))
    }

    private fun startPresencePolling(userId: String) {
        // Always-on polling: guarantees UI refresh even when no Realtime events arrive
        // (e.g. when the other user crashes — no more heartbeats = no events = stale UI without this)
        viewModelScope.launch {
            _otherUserPresence.value = userRepo.getPresence(userId)
            while (true) {
                delay(10_000L)
                val presence = userRepo.getPresence(userId)
                if (presence != null) _otherUserPresence.value = presence
            }
        }
        // Realtime on top: instant updates when presence changes
        viewModelScope.launch {
            runCatching {
                userRepo.presenceUpdateFlow(userId).collect { presence ->
                    Log.d("Presence", "realtime update for $userId: $presence")
                    _otherUserPresence.value = presence
                }
            }
        }
    }

    private suspend fun loadMessages(scrollAfter: Boolean = true) {
        val raw = messageRepo.getMessages(chatId, limit = 50, historyFrom = historyFrom)
        if (raw.isEmpty()) return  // offline — keep cached messages shown

        val enriched = enrichMessages(raw)
        val newLastId = raw.lastOrNull()?.id

        // Find first unread incoming message (before markAsRead runs) — set only once on initial load
        if (_firstUnreadIndex.value < 0) {
            val myId = currentUserId
            val incomingIds = raw.filter { it.senderId != myId }.map { it.id }
            val alreadyReadByMe = messageRepo.getReadMessageIdsByUser(incomingIds, myId)
            val idx = raw.indexOfFirst { it.senderId != myId && it.id !in alreadyReadByMe }
            _firstUnreadIndex.value = idx
            Log.d("UnreadSep", "firstUnreadIndex=$idx, incoming=${incomingIds.size}, alreadyRead=${alreadyReadByMe.size}")
        }

        // Smart merge: only replace items that actually changed
        val current = _messages.value
        val merged = if (current.isNotEmpty() && current.map { it.message.id } == raw.map { it.id }) {
            val enrichedById = enriched.associateBy { it.message.id }
            current.map { old ->
                val updated = enrichedById[old.message.id]
                if (updated != null && updated != old) updated else old
            }
        } else {
            enriched
        }
        if (merged != current) {
            _messages.value = merged
        }

        lastKnownMessageId = newLastId
        if (scrollAfter) _scrollToBottomEvent.value++

        cache.saveMessages(chatId, raw)
        cache.saveProfiles(profileCache.values)
    }

    private suspend fun enrichMessages(raw: List<Message>): List<MessageUiItem> {
        val senderIds = raw.mapNotNull { it.senderId }.distinct()
        val forwardedFromIds = raw.mapNotNull { it.forwardedFromUserId }.distinct()
        val allIds = (senderIds + forwardedFromIds).distinct()
        val missing = allIds.filter { it !in profileCache }
        if (missing.isNotEmpty()) {
            userRepo.getProfiles(missing).forEach { profileCache[it.id] = it }
        }

        val myId = currentUserId
        val myMessageIds = raw.filter { it.senderId == myId }.map { it.id }
        val readIds = messageRepo.getReadMessageIds(myMessageIds)

        val messageMap = raw.associateBy { it.id }
        return raw.map { msg ->
            val replyMsg = msg.replyToId?.let { messageMap[it] ?: messageRepo.getMessage(it) }
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = msg.senderId == myId,
                isRead = msg.id in readIds,
                replyToMessage = replyMsg,
                replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] }
            )
        }
    }

    /** Server-side only: sends read receipts without updating the badge counter */
    private fun sendReadReceipts() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                messageRepo.markMessagesAsRead(chatId)
            }
        }
    }

    /** Called by ChatScreen when user reaches the bottom — clears the unread badge */
    fun markAsRead() {
        _lastSeenMsgCount.value = _messages.value.size
        sendReadReceipts()
    }

    private suspend fun loadPinnedMessage() {
        val pinned = chatRepo.getPinnedMessage(chatId)
        val content = pinned?.let { messageRepo.getMessage(it.messageId) }
        _pinnedMessage.value = pinned
        _pinnedMessageContent.value = content
        cache.savePinnedContent(chatId, pinned, content)
    }

    fun pinMessage(messageId: String) {
        viewModelScope.launch {
            val success = chatRepo.pinMessage(chatId, messageId)
            if (success) {
                val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
                messageRepo.sendSystemMessage(chatId, "$myName закрепил(а) сообщение", messageId)
                loadPinnedMessage()
            }
        }
    }

    fun unpinMessage() {
        viewModelScope.launch {
            val oldPinnedId = _pinnedMessage.value?.messageId
            val success = chatRepo.unpinMessage(chatId)
            if (success) {
                val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
                messageRepo.sendSystemMessage(chatId, "$myName открепил(а) сообщение", oldPinnedId)
                _pinnedMessage.value = null
                _pinnedMessageContent.value = null
            }
        }
    }

    fun scrollToMessage(messageId: String) {
        _scrollToMessageEvent.value = messageId
        _highlightedMessageId.value = messageId
        viewModelScope.launch {
            delay(2_000L)
            _highlightedMessageId.value = null
        }
    }

    fun clearScrollToMessageEvent() {
        _scrollToMessageEvent.value = null
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            messageRepo.messageInsertFlow(chatId).collect { newMsg ->
                // New incoming message — clear typing indicator for that user
                if (newMsg.senderId != currentUserId) {
                    _typingUsers.value = _typingUsers.value.filter { it.userId != newMsg.senderId }
                }

                val profile = newMsg.senderId?.let { id ->
                    profileCache.getOrPut(id) {
                        userRepo.getProfile(id) ?: Profile(id = id)
                    }
                }
                val forwardedFromProfile = newMsg.forwardedFromUserId?.let { id ->
                    profileCache.getOrPut(id) {
                        userRepo.getProfile(id) ?: Profile(id = id)
                    }
                }
                val replyMsg = newMsg.replyToId?.let { messageRepo.getMessage(it) }
                val item = MessageUiItem(
                    message = newMsg,
                    senderProfile = profile,
                    isOwn = newMsg.senderId == currentUserId,
                    isRead = false,
                    replyToMessage = replyMsg,
                    replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                    forwardedFromProfile = forwardedFromProfile
                )
                val updated = _messages.value + item
                _messages.value = updated
                lastKnownMessageId = newMsg.id
                // Скролл вниз управляется из ChatScreen: только если пользователь уже внизу

                // Trigger entrance animation; clean up after it finishes
                val msgId = newMsg.id
                _animatingMessageIds.value = _animatingMessageIds.value + msgId
                viewModelScope.launch {
                    delay(900L)
                    _animatingMessageIds.value = _animatingMessageIds.value - msgId
                }

                // Не помечаем прочитанным здесь — это делает ChatScreen когда пользователь внизу
                cache.saveMessages(chatId, updated.map { it.message })
            }
        }
    }

    private fun observeUpdatedMessages() {
        viewModelScope.launch {
            messageRepo.messageUpdateFlow(chatId).collect { updated ->
                _messages.value = _messages.value.map { item ->
                    if (item.message.id == updated.id) item.copy(message = updated) else item
                }
            }
        }
    }

    private fun observeReadReceipts() {
        viewModelScope.launch {
            try {
                messageRepo.messageReadFlow(chatId).collect { read ->
                    // Only update if it's one of our messages being read
                    val idx = _messages.value.indexOfFirst { it.message.id == read.messageId && it.isOwn }
                    if (idx >= 0 && !_messages.value[idx].isRead) {
                        Log.d("ReadReceipts", "realtime: message ${read.messageId} marked read by ${read.userId}")
                        _messages.value = _messages.value.toMutableList().also {
                            it[idx] = it[idx].copy(isRead = true)
                        }
                    }
                }
            } catch (_: Exception) {
                // Realtime unavailable — fall back to polling
                while (true) {
                    delay(10_000L)
                    val unreadOwnIds = _messages.value.filter { it.isOwn && !it.isRead }.map { it.message.id }
                    if (unreadOwnIds.isNotEmpty()) {
                        val readIds = messageRepo.getReadMessageIds(unreadOwnIds)
                        if (readIds.isNotEmpty()) {
                            _messages.value = _messages.value.map { item ->
                                if (item.message.id in readIds) item.copy(isRead = true) else item
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startTypingPolling() {
        viewModelScope.launch {
            while (true) {
                delay(3_000L)
                if (chatId.isNotEmpty()) {
                    val typing = messageRepo.getTypingUsers(chatId, currentUserId)
                    _typingUsers.value = typing.map { TypingInfo(it.userId, it.displayName, it.status) }
                }
            }
        }
    }

    /** Called when the user changes input text — sends/debounces typing indicator */
    fun onInputTextChanged(text: String) {
        typingJob?.cancel()
        if (text.isBlank()) {
            viewModelScope.launch { messageRepo.clearTyping(chatId, currentUserId) }
            return
        }
        typingJob = viewModelScope.launch {
            val displayName = profileCache[currentUserId]?.displayName ?: ""
            messageRepo.setTyping(chatId, currentUserId, displayName)
            delay(4_000L)
            messageRepo.clearTyping(chatId, currentUserId)
        }
    }

    fun clearUnreadSeparator() {
        _firstUnreadIndex.value = -1
    }

    override fun onCleared() {
        super.onCleared()
        if (chatId.isNotEmpty()) {
            viewModelScope.launch { messageRepo.clearTyping(chatId, currentUserId) }
        }
        voiceRecorder.cancel()
        stopVoice()
    }

    fun setReplyTo(message: Message?) { _replyTo.value = message }
    fun setEditing(message: Message?) {
        _editingMessage.value = message
        if (message != null) _replyTo.value = null
    }

    fun sendText(content: String) {
        if (content.isBlank()) return
        val replyId = _replyTo.value?.id
        val editing = _editingMessage.value

        viewModelScope.launch {
            _isSending.value = true
            // Clear typing indicator immediately on send
            messageRepo.clearTyping(chatId, currentUserId)
            typingJob?.cancel()

            if (editing != null) {
                messageRepo.editMessage(editing.id, content.trim())
                _editingMessage.value = null
            } else {
                _scrollToOwnMessageEvent.value++
                messageRepo.sendTextMessage(chatId, content.trim(), replyId)
                _replyTo.value = null
            }
            _isSending.value = false
        }
    }

    // ── Staged media & files ──────────────────────────────────────────────────

    fun addStagedMedia(uris: List<Uri>, context: Context) {
        val newItems = uris.map { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: ""
            StagedMedia(uri, isVideo = mimeType.startsWith("video/"))
        }
        _stagedMedia.value = (_stagedMedia.value + newItems).take(10)
    }

    fun removeStagedMedia(index: Int) {
        _stagedMedia.value = _stagedMedia.value.filterIndexed { i, _ -> i != index }
    }

    fun clearStagedMedia() {
        _stagedMedia.value = emptyList()
        _uploadProgresses.value = emptyList()
    }

    private fun getVideoNameFromUri(uri: Uri, context: Context): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (!c.moveToFirst()) return@use null
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) c.getString(nameIdx) else null
            } ?: "video_${System.currentTimeMillis()}.mp4"
        } catch (e: Exception) { "video_${System.currentTimeMillis()}.mp4" }
    }

    private fun getUriFileSize(uri: Uri, context: Context): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) { 0L }
    }

    /** Send all staged content (photos as album, videos individually, + optional text).
     *  Clears staged state immediately, then uploads in background. */
    fun sendWithAttachments(text: String, media: List<StagedMedia>, context: Context) {
        val replyId = _replyTo.value?.id
        val myId = currentUserId
        val myProfile = profileCache[myId]

        val photos = media.filter { !it.isVideo }
        val videos = media.filter { it.isVideo }

        _stagedMedia.value = emptyList()
        _replyTo.value = null

        // ── Photos (album or single) ───────────────────────────────────────────
        if (photos.isNotEmpty()) {
            val pendingId = "pending_${java.util.UUID.randomUUID()}"
            val now = java.time.Instant.now().toString()
            val pendingMsg = Message(
                id = pendingId, chatId = chatId, senderId = myId,
                content = text.trim().ifBlank { null },
                type = if (photos.size == 1) "photo" else "album",
                createdAt = now
            )
            val pendingItem = MessageUiItem(
                message = pendingMsg, senderProfile = myProfile, isOwn = true, isRead = false,
                isPending = true, pendingLocalUris = photos.map { it.uri.toString() }
            )
            _messages.value = _messages.value + pendingItem
            _scrollToOwnMessageEvent.value++
            _uploadProgresses.value = List(photos.size) { 0f }

            val displayName = myProfile?.displayName ?: ""
            if (activeUploadCount.incrementAndGet() == 1) {
                viewModelScope.launch { messageRepo.setTyping(chatId, myId, displayName, "uploading_media") }
            }

            viewModelScope.launch(Dispatchers.IO) {
                _isSending.value = true
                try {
                    val uploadedUrls = mutableListOf<String>()
                    photos.forEachIndexed { idx, staged ->
                        val bytes = compressImage(staged.uri, context)
                        if (bytes == null) {
                            _error.value = "Не удалось прочитать изображение"
                            _messages.value = _messages.value.filter { it.message.id != pendingId }
                            _uploadProgresses.value = emptyList()
                            return@launch
                        }
                        val fileName = "photo_${System.currentTimeMillis()}.jpg"
                        val url = messageRepo.uploadFile(chatId, fileName, bytes) { progress ->
                            val progs = _uploadProgresses.value.toMutableList()
                            if (idx < progs.size) { progs[idx] = progress; _uploadProgresses.value = progs }
                        }
                        if (url == null) {
                            _error.value = "Ошибка загрузки изображения"
                            _messages.value = _messages.value.filter { it.message.id != pendingId }
                            _uploadProgresses.value = emptyList()
                            return@launch
                        }
                        uploadedUrls.add(url)
                    }
                    if (photos.size == 1) {
                        messageRepo.sendPhotoMessage(chatId, uploadedUrls[0], replyId, text.trim().ifBlank { null })
                    } else {
                        messageRepo.sendAlbumMessage(chatId, uploadedUrls, text.trim().ifBlank { null }, replyId)
                    }
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _error.value = "Ошибка: ${e.message}"
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                } finally {
                    _isSending.value = false
                    _uploadProgresses.value = emptyList()
                    if (activeUploadCount.decrementAndGet() == 0) {
                        messageRepo.clearTyping(chatId, myId)
                    }
                }
            }
        }

        // ── Videos (each as separate message) ─────────────────────────────────
        val caption = text.trim().ifBlank { null }
        videos.forEach { staged -> sendVideoInternal(staged, context, replyId, caption) }
    }

    private fun sendVideoInternal(staged: StagedMedia, context: Context, replyId: String?, caption: String? = null) {
        val myId = currentUserId
        val mimeType = context.contentResolver.getType(staged.uri) ?: "video/mp4"
        val name = getVideoNameFromUri(staged.uri, context)

        // Проверка лимита через openAssetFileDescriptor (надёжнее, чем OpenableColumns.SIZE)
        val fileSize = getUriFileSize(staged.uri, context)
        if (fileSize > 0 && fileSize > 50 * 1024 * 1024) {
            _error.value = "Видео слишком большое. Максимальный размер — 50 МБ."
            return
        }

        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()
        val pendingMsg = Message(id = pendingId, chatId = chatId, senderId = myId, type = "video",
            fileName = name, mimeType = mimeType, createdAt = now)
        val pendingItem = MessageUiItem(message = pendingMsg, senderProfile = profileCache[myId],
            isOwn = true, isRead = false, isPending = true)
        _messages.value = _messages.value + pendingItem
        _scrollToOwnMessageEvent.value++

        val displayName = profileCache[myId]?.displayName ?: ""
        if (activeUploadCount.incrementAndGet() == 1) {
            viewModelScope.launch { messageRepo.setTyping(chatId, myId, displayName, "uploading_media") }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(staged.uri)?.readBytes()
                if (bytes == null) {
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                    _error.value = "Не удалось прочитать видео"
                    return@launch
                }
                // Дополнительная проверка после чтения (если pre-check вернул 0)
                if (bytes.size > 50 * 1024 * 1024) {
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                    _error.value = "Видео слишком большое. Максимальный размер — 50 МБ."
                    return@launch
                }
                val ext = name.substringAfterLast('.', "mp4")
                val fileName = "video_${System.currentTimeMillis()}.$ext"
                val url = messageRepo.uploadFile(chatId, fileName, bytes)
                if (url != null) {
                    messageRepo.sendVideoMessage(chatId, url, name, bytes.size.toLong(), mimeType, replyId, caption)
                } else {
                    _error.value = "Ошибка загрузки видео"
                }
                _messages.value = _messages.value.filter { it.message.id != pendingId }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.value = _messages.value.filter { it.message.id != pendingId }
                _error.value = "Ошибка загрузки видео"
            } finally {
                if (activeUploadCount.decrementAndGet() == 0) {
                    messageRepo.clearTyping(chatId, myId)
                }
            }
        }
    }

    private fun compressImage(uri: Uri, context: Context): ByteArray? {
        return try {
            // Read bytes once — reuse for both EXIF and bitmap decoding
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null

            // Read EXIF orientation
            val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            // Apply rotation if needed
            val rotated = if (rotation != 0f) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
                android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    .also { if (it != original) original.recycle() }
            } else original

            val maxDim = 1280
            val scaled = if (rotated.width > maxDim || rotated.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / rotated.width, maxDim.toFloat() / rotated.height)
                android.graphics.Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * ratio).toInt(),
                    (rotated.height * ratio).toInt(),
                    true
                ).also { if (it != rotated) rotated.recycle() }
            } else rotated

            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled != rotated) scaled.recycle()
            out.toByteArray()
        } catch (e: Exception) { null }
    }

    fun clearError() { _error.value = null }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                messageRepo.deleteMessageForAll(messageId)
            } else {
                _messages.value = _messages.value.filter { it.message.id != messageId }
            }
        }
    }

    fun forwardMessage(messageId: String, toChatId: String) {
        viewModelScope.launch {
            messageRepo.forwardMessage(messageId, toChatId)
        }
    }

    // ── Selection mode ────────────────────────────────────────────────────────

    fun toggleSelection(messageId: String) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (messageId in current) current.remove(messageId) else current.add(messageId)
        _selectedMessageIds.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun loadChatsForForward() {
        viewModelScope.launch {
            _chatsForForward.value = chatRepo.getChatsForUser()
        }
    }

    fun forwardSelectedMessages(toChatId: String) {
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            ids.forEach { messageId -> messageRepo.forwardMessage(messageId, toChatId) }
        }
        clearSelection()
    }

    fun forwardSingleMessage(messageId: String, toChatId: String) {
        viewModelScope.launch {
            messageRepo.forwardMessage(messageId, toChatId)
        }
    }

    fun deleteSelectedMessages(forEveryone: Boolean) {
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            if (forEveryone) {
                ids.forEach { messageRepo.deleteMessageForAll(it) }
            } else {
                _messages.value = _messages.value.filter { it.message.id !in ids }
            }
        }
        clearSelection()
    }

    fun loadMoreMessages() {
        viewModelScope.launch {
            val current = _messages.value
            val older = messageRepo.getMessages(chatId, limit = 30, offset = current.size, historyFrom = historyFrom)
            if (older.isNotEmpty()) {
                _messages.value = enrichMessages(older) + current
            }
        }
    }

    // ── Voice recording methods ───────────────────────────────────────────────

    fun startVoiceRecording() {
        if (_voiceRecordState.value != VoiceRecordState.Idle) return
        pauseVoice() // stop any playing voice
        val started = voiceRecorder.start()
        if (!started) { _error.value = "Не удалось начать запись"; return }
        _voiceRecordState.value = VoiceRecordState.Recording()
        voiceTimerJob?.cancel()
        voiceTimerJob = viewModelScope.launch {
            while (voiceRecorder.isRecording) {
                _voiceElapsedMs.value = voiceRecorder.elapsedMs
                delay(100)
            }
        }
    }

    fun lockRecording() {
        val s = _voiceRecordState.value
        if (s is VoiceRecordState.Recording && !s.isLocked) {
            _voiceRecordState.value = VoiceRecordState.Recording(isLocked = true)
        }
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        voiceRecorder.cancel()
        _voiceRecordState.value = VoiceRecordState.Idle
        _voiceElapsedMs.value = 0L
    }

    fun sendVoiceRecording(context: android.content.Context) {
        if (voiceRecorder.elapsedMs < 500L) { cancelVoiceRecording(); return }
        voiceTimerJob?.cancel()
        _voiceRecordState.value = VoiceRecordState.Idle
        _voiceElapsedMs.value = 0L
        val result = voiceRecorder.stop() ?: return
        val (file, durationSec) = result
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        val myId = currentUserId
        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()
        val pendingMsg = Message(id = pendingId, chatId = chatId, senderId = myId,
            type = "voice", duration = durationSec, createdAt = now)
        _messages.value = _messages.value + MessageUiItem(
            message = pendingMsg, senderProfile = profileCache[myId],
            isOwn = true, isRead = false, isPending = true)
        _scrollToOwnMessageEvent.value++
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes(); file.delete()
                val url = messageRepo.uploadFile(chatId, "voice_${System.currentTimeMillis()}.m4a", bytes)
                if (url != null) {
                    messageRepo.sendVoiceMessage(chatId, url, durationSec, replyId)
                } else {
                    _error.value = "Ошибка загрузки голосового"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ChatVM", "sendVoiceRecording exception: ${e.message}", e)
                _error.value = "Ошибка: ${e.message}"
            } finally {
                _messages.value = _messages.value.filter { it.message.id != pendingId }
            }
        }
    }

    // ── Voice playback methods ────────────────────────────────────────────────

    fun playVoice(messageId: String, url: String, durationSec: Int) {
        val cur = _voicePlayState.value
        if (cur?.messageId == messageId) {
            if (cur.isPlaying) pauseVoice() else resumeVoice(); return
        }
        stopVoice()
        val player = android.media.MediaPlayer()
        voiceMediaPlayer = player
        _voicePlayState.value = VoicePlayState(messageId, false, 0, durationSec * 1000)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                player.setDataSource(url)
                player.prepare()
                val dur = player.duration.takeIf { it > 0 } ?: (durationSec * 1000)
                withContext(Dispatchers.Main) {
                    _voicePlayState.value = VoicePlayState(messageId, true, 0, dur)
                    player.start()
                    startVoiceProgressUpdates(messageId)
                    player.setOnCompletionListener {
                        _voicePlayState.value = _voicePlayState.value?.copy(isPlaying = false, positionMs = 0)
                        voiceProgressJob?.cancel()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) { _voicePlayState.value = null; voiceMediaPlayer = null }
            }
        }
    }

    private fun startVoiceProgressUpdates(messageId: String) {
        voiceProgressJob?.cancel()
        voiceProgressJob = viewModelScope.launch {
            while (_voicePlayState.value?.isPlaying == true) {
                _voicePlayState.value = _voicePlayState.value?.copy(
                    positionMs = voiceMediaPlayer?.currentPosition ?: break
                )
                delay(100)
            }
        }
    }

    fun pauseVoice() {
        try { voiceMediaPlayer?.pause() } catch (_: Exception) {}
        voiceProgressJob?.cancel()
        _voicePlayState.value = _voicePlayState.value?.copy(isPlaying = false)
    }

    fun resumeVoice() {
        val id = _voicePlayState.value?.messageId ?: return
        voiceMediaPlayer?.start()
        _voicePlayState.value = _voicePlayState.value?.copy(isPlaying = true)
        startVoiceProgressUpdates(id)
    }

    fun seekVoice(positionMs: Int) {
        voiceMediaPlayer?.seekTo(positionMs)
        _voicePlayState.value = _voicePlayState.value?.copy(positionMs = positionMs)
    }

    fun stopVoice() {
        voiceProgressJob?.cancel()
        try { voiceMediaPlayer?.stop(); voiceMediaPlayer?.release() } catch (_: Exception) {}
        voiceMediaPlayer = null
        _voicePlayState.value = null
    }

    // ── Notifications mute ────────────────────────────────────────────────────

    fun toggleMute() {
        viewModelScope.launch {
            val newMuted = !_isMuted.value
            _isMuted.value = newMuted
            app.themeManager.setChatMuted(chatId, newMuted)
            chatRepo.setMuted(chatId, newMuted)
        }
    }

    // ── Leave group / Delete personal chat ────────────────────────────────────

    fun leaveGroup(onLeft: () -> Unit) {
        viewModelScope.launch {
            val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
            messageRepo.sendSystemMessage(chatId, "$myName вышел(а) из чата")
            chatRepo.removeMember(chatId, currentUserId)
            onLeft()
        }
    }

    fun deletePersonalChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            chatRepo.markAsLeft(chatId)
            onDeleted()
        }
    }

}
