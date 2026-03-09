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
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // true while fetching from network (for subtitle "Обновление...")
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Incremented to signal scroll to bottom
    private val _scrollToBottomEvent = MutableStateFlow(0)
    val scrollToBottomEvent: StateFlow<Int> = _scrollToBottomEvent

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val currentUserId get() = authRepo.currentUserId() ?: ""

    private var chatId: String = ""
    private val profileCache = mutableMapOf<String, Profile>()
    private var otherUserId: String? = null
    private var lastKnownMessageId: String? = null  // for smart scroll

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
                _isLoading.value = cachedInfo == null  // spinner only if nothing cached
            }

            // 2. Load fresh from network
            _isUpdating.value = true
            loadChatInfo()
            loadMessages()
            markAsRead()
            loadPinnedMessage()
            _isUpdating.value = false
            _isLoading.value = false

            observeNewMessages()
            observeUpdatedMessages()
            startReadReceiptPolling()
        }
    }

    private fun buildUiItems(raw: List<Message>): List<MessageUiItem> {
        val myId = currentUserId
        return raw.map { msg ->
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = msg.senderId == myId,
                isRead = false
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
            while (true) {
                val presence = userRepo.getPresence(userId)
                Log.d("Presence", "poll result for $userId: $presence")
                _otherUserPresence.value = presence
                delay(10_000L)
            }
        }
    }

    private suspend fun loadMessages() {
        val raw = messageRepo.getMessages(chatId, limit = 50)
        val newLastId = raw.lastOrNull()?.id

        // Only update UI and scroll if data actually changed
        val enriched = enrichMessages(raw)
        _messages.value = enriched

        // Scroll to bottom only if newer messages arrived vs cache
        if (newLastId != null && newLastId != lastKnownMessageId) {
            lastKnownMessageId = newLastId
            _scrollToBottomEvent.value++
        }

        if (raw.isNotEmpty()) {
            cache.saveMessages(chatId, raw)
            cache.saveProfiles(profileCache.values)
        }
    }

    private suspend fun enrichMessages(raw: List<Message>): List<MessageUiItem> {
        val senderIds = raw.mapNotNull { it.senderId }.distinct()
        val missing = senderIds.filter { it !in profileCache }
        if (missing.isNotEmpty()) {
            userRepo.getProfiles(missing).forEach { profileCache[it.id] = it }
        }

        val myId = currentUserId
        val myMessageIds = raw.filter { it.senderId == myId }.map { it.id }
        val readIds = messageRepo.getReadMessageIds(myMessageIds)

        return raw.map { msg ->
            val replyMsg = msg.replyToId?.let { messageRepo.getMessage(it) }
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = msg.senderId == myId,
                isRead = msg.id in readIds,
                replyToMessage = replyMsg
            )
        }
    }

    private suspend fun markAsRead() {
        messageRepo.markMessagesAsRead(chatId)
    }

    private suspend fun loadPinnedMessage() {
        _pinnedMessage.value = chatRepo.getPinnedMessage(chatId)
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            messageRepo.messageInsertFlow(chatId).collect { newMsg ->
                val profile = newMsg.senderId?.let { id ->
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
                    replyToMessage = replyMsg
                )
                val updated = _messages.value + item
                _messages.value = updated
                lastKnownMessageId = newMsg.id
                _scrollToBottomEvent.value++
                markAsRead()
                // Update message cache
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

    private fun startReadReceiptPolling() {
        viewModelScope.launch {
            while (true) {
                delay(8_000L)
                val unreadOwnIds = _messages.value
                    .filter { it.isOwn && !it.isRead }
                    .map { it.message.id }
                Log.d("ReadReceipts", "poll: ${unreadOwnIds.size} unread own messages")
                if (unreadOwnIds.isNotEmpty()) {
                    val readIds = messageRepo.getReadMessageIds(unreadOwnIds)
                    if (readIds.isNotEmpty()) {
                        Log.d("ReadReceipts", "poll: marking ${readIds.size} as read")
                        _messages.value = _messages.value.map { item ->
                            if (item.message.id in readIds) item.copy(isRead = true) else item
                        }
                    }
                }
            }
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
