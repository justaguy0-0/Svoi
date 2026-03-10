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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TypingInfo(val userId: String, val displayName: String)

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

    // Incremented to signal scroll
    private val _scrollToBottomEvent = MutableStateFlow(0)
    val scrollToBottomEvent: StateFlow<Int> = _scrollToBottomEvent

    // Index of first unread message for the separator (-1 = none)
    private val _firstUnreadIndex = MutableStateFlow(-1)
    val firstUnreadIndex: StateFlow<Int> = _firstUnreadIndex

    // Currently typing users (excluding self)
    private val _typingUsers = MutableStateFlow<List<TypingInfo>>(emptyList())
    val typingUsers: StateFlow<List<TypingInfo>> = _typingUsers

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val currentUserId get() = authRepo.currentUserId() ?: ""

    private var chatId: String = ""
    private val profileCache = mutableMapOf<String, Profile>()
    private var otherUserId: String? = null
    private var lastKnownMessageId: String? = null
    private var typingJob: Job? = null

    fun init(chatId: String) {
        if (this.chatId == chatId) return
        this.chatId = chatId

        viewModelScope.launch {
            // 1. Show cached data instantly
            val cachedInfo = cache.loadChatInfo(chatId)
            val cachedMessages = cache.loadMessages(chatId)
            val cachedProfiles = cache.loadProfileMap()

            if (cachedInfo != null) {
                _chatName.value = cachedInfo.name
                _isGroup.value = cachedInfo.isGroup
                _memberCount.value = cachedInfo.memberCount
                otherUserId = cachedInfo.otherUserId
            }
            if (cachedMessages != null) {
                cachedProfiles.forEach { profileCache[it.key] = it.value }
                _messages.value = buildUiItems(cachedMessages)
                lastKnownMessageId = cachedMessages.lastOrNull()?.id
                _isLoading.value = false
                _scrollToBottomEvent.value++
            } else {
                _isLoading.value = cachedInfo == null
            }

            // 2. Load fresh from network
            val hasFullCache = cachedInfo != null && cachedMessages != null
            if (!hasFullCache) _isUpdating.value = true
            loadChatInfo()
            loadPinnedMessage()   // before loadMessages so banner height is stable when messages appear
            loadMessages()
            markAsRead()
            _isUpdating.value = false
            _isLoading.value = false

            // Clear separator after 5s — user has had time to read the context
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
        }
    }

    private fun buildUiItems(raw: List<Message>): List<MessageUiItem> {
        val myId = currentUserId
        val messageMap = raw.associateBy { it.id }
        return raw.map { msg ->
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = msg.senderId == myId,
                isRead = false,
                replyToMessage = msg.replyToId?.let { messageMap[it] },
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] }
            )
        }
    }

    private suspend fun loadChatInfo() {
        val chat = chatRepo.getChat(chatId) ?: return
        _chat.value = chat
        _isGroup.value = chat.type == "group"

        val members = chatRepo.getChatMembers(chatId)
        _memberCount.value = members.size
        val profiles = userRepo.getProfiles(members.map { it.userId })
        profiles.forEach { profileCache[it.id] = it }

        val myId = currentUserId
        if (chat.type == "personal") {
            val other = profiles.firstOrNull { it.id != myId }
            _chatName.value = other?.displayName ?: "Пользователь"
            other?.let { otherProfile ->
                otherUserId = otherProfile.id
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
            otherUserId = otherUserId
        ))
    }

    private fun startPresencePolling(userId: String) {
        viewModelScope.launch {
            // Initial load
            _otherUserPresence.value = userRepo.getPresence(userId)
            // Realtime updates
            try {
                userRepo.presenceUpdateFlow(userId).collect { presence ->
                    Log.d("Presence", "realtime update for $userId: $presence")
                    _otherUserPresence.value = presence
                }
            } catch (_: Exception) {
                // Realtime unavailable — fall back to polling
                while (true) {
                    delay(15_000L)
                    val presence = userRepo.getPresence(userId)
                    Log.d("Presence", "poll fallback for $userId: $presence")
                    _otherUserPresence.value = presence
                }
            }
        }
    }

    private suspend fun loadMessages() {
        val raw = messageRepo.getMessages(chatId, limit = 50)
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
        _scrollToBottomEvent.value++

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
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] }
            )
        }
    }

    private suspend fun markAsRead() {
        messageRepo.markMessagesAsRead(chatId)
    }

    private suspend fun loadPinnedMessage() {
        val pinned = chatRepo.getPinnedMessage(chatId)
        _pinnedMessage.value = pinned
        _pinnedMessageContent.value = pinned?.let { messageRepo.getMessage(it.messageId) }
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
                    forwardedFromProfile = forwardedFromProfile
                )
                val updated = _messages.value + item
                _messages.value = updated
                lastKnownMessageId = newMsg.id
                _scrollToBottomEvent.value++
                markAsRead()
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
                delay(2_000L)
                if (chatId.isNotEmpty()) {
                    val typing = messageRepo.getTypingUsers(chatId, currentUserId)
                    _typingUsers.value = typing.map { TypingInfo(it.userId, it.displayName) }
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
                messageRepo.sendTextMessage(chatId, content.trim(), replyId)
                _replyTo.value = null
            }
            _isSending.value = false
        }
    }

    fun sendPhoto(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isSending.value = true
            try {
                val bytes = compressImage(uri, context)
                if (bytes == null) {
                    _error.value = "Не удалось прочитать изображение"
                    return@launch
                }
                val fileName = "photo_${System.currentTimeMillis()}.jpg"
                val url = messageRepo.uploadFile(chatId, fileName, bytes)
                if (url != null) {
                    messageRepo.sendPhotoMessage(chatId, url, _replyTo.value?.id)
                    _replyTo.value = null
                } else {
                    _error.value = "Ошибка загрузки. Проверьте Storage bucket."
                }
            } catch (e: Exception) {
                _error.value = "Ошибка: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun compressImage(uri: Uri, context: Context): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null
            val maxDim = 1280
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                android.graphics.Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt(),
                    (original.height * ratio).toInt(),
                    true
                )
            } else original
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled != original) scaled.recycle()
            original.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
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
            val older = messageRepo.getMessages(chatId, limit = 30, offset = current.size)
            if (older.isNotEmpty()) {
                _messages.value = enrichMessages(older) + current
            }
        }
    }
}
