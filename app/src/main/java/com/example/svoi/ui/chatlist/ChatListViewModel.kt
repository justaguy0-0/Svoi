package com.example.svoi.ui.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.TypingStatus
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.isTrulyOnline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val chatRepo = app.chatRepository
    private val messageRepo = app.messageRepository
    private val userRepo = app.userRepository
    private val cache = app.cacheManager

    private val _chats = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chats: StateFlow<List<ChatListItem>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _chatTyping = MutableStateFlow<Map<String, String>>(emptyMap())
    val chatTyping: StateFlow<Map<String, String>> = _chatTyping

    private val currentUserId get() = app.authRepository.currentUserId() ?: ""

    // true only during initial app load or after reconnect — shown as "Обновление..."
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val isReachable: StateFlow<Boolean> = app.supabaseChecker.isReachable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _currentProfile = MutableStateFlow<com.example.svoi.data.model.Profile?>(null)
    val currentProfile: StateFlow<com.example.svoi.data.model.Profile?> = _currentProfile

    // Track whether we need to show "Обновление..." on next refresh
    private var initialLoad = true
    private var wasOffline = false

    // Mutex ensures only one getChatsForUser() runs at a time — prevents partial/stale overwrites
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var loadJob: Job? = null

    init {
        // Watch for going offline so we show "Обновление..." when reconnecting.
        // On reconnect, also wait for Supabase to become reachable before fetching.
        viewModelScope.launch {
            isOnline.collect { online ->
                if (!online) wasOffline = true
                else if (wasOffline) {
                    wasOffline = false
                    // Probe Supabase — only reload if it's actually reachable
                    val reachable = app.supabaseChecker.checkNow(force = true)
                    if (reachable) loadChats(showUpdating = true)
                    // If still blocked: cache is already shown; isReachable observer below
                    // will trigger loadChats once connectivity is fully restored
                }
            }
        }
        // Reload automatically when Supabase transitions from blocked → reachable
        viewModelScope.launch {
            app.supabaseChecker.isReachable.collect { reachable ->
                if (reachable && wasOffline) {
                    wasOffline = false
                    loadChats(showUpdating = true)
                }
            }
        }
        loadChats(showUpdating = true)  // initial load
        viewModelScope.launch {
            // Show cached profile immediately, then refresh from network
            cache.loadOwnProfile()?.let { _currentProfile.value = it }
            val fresh = app.userRepository.getCurrentProfile()
            if (fresh != null) _currentProfile.value = fresh
        }
        observeNewMessages()
        observeReadReceipts()
        observePresenceUpdates()
        startTypingPolling()
    }

    fun refreshCurrentProfile() {
        viewModelScope.launch {
            cache.loadOwnProfile()?.let { _currentProfile.value = it }
        }
    }

    fun loadChats(showUpdating: Boolean = false) {
        loadJob?.cancel()
        refreshJob?.cancel()  // cancel any pending silent refresh — we're doing a full load
        loadJob = viewModelScope.launch {
            // 1. Show cache immediately
            val cached = cache.loadChatList()
            if (cached != null) {
                _chats.value = cached
                _isLoading.value = false
            } else {
                _isLoading.value = true
            }

            // 2. Show "Обновление..." only on initial load or explicit refresh
            val animate = showUpdating && (initialLoad || _chats.value.isEmpty())
            if (animate) _isUpdating.value = true

            // 3. Guard: skip server fetch if Supabase is blocked (internet up but service blocked)
            val supabaseReachable = app.supabaseChecker.checkNow()
            if (!supabaseReachable) {
                initialLoad = false
                _isUpdating.value = false
                _isLoading.value = false
                return@launch
            }

            // 4. Fetch fresh from server — mutex ensures no parallel fetch overwrites this
            refreshMutex.withLock {
                val fresh = chatRepo.getChatsForUser()
                if (fresh.isNotEmpty()) {
                    _chats.value = fresh
                    cache.saveChatList(fresh)
                }
            }

            initialLoad = false
            _isUpdating.value = false
            _isLoading.value = false
        }
    }

    // Debounced silent refresh — skips if a full loadChats() is already running or Supabase blocked
    fun silentRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(150) // debounce: ignore rapid-fire events
            if (!app.supabaseChecker.isReachable.value) return@launch
            if (!refreshMutex.tryLock()) return@launch  // loadChats in progress — it will have fresh data
            try {
                val fresh = chatRepo.getChatsForUser()
                if (fresh.isNotEmpty()) {
                    _chats.value = fresh
                    cache.saveChatList(fresh)
                }
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            try {
                messageRepo.messageInsertFlowAll().collect { msg ->
                    // Instant optimistic update — no network round-trip
                    applyNewMessageOptimistic(msg)
                    // Background full refresh for accurate unread counts, etc.
                    silentRefresh()
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyNewMessageOptimistic(msg: Message) {
        val current = _chats.value
        if (current.isEmpty()) return
        val chatId = msg.chatId
        val existing = current.find { it.chatId == chatId } ?: return

        val myId = currentUserId
        val isOwn = msg.senderId == myId
        val senderPrefix = if (existing.isGroup) {
            if (isOwn) "Вы: " else ""  // full name will come from silentRefresh
        } else ""

        val lastText = when (msg.type) {
            "photo" -> {
                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                "${senderPrefix}📷 Фото$caption"
            }
            "album" -> {
                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                "${senderPrefix}📷 ${msg.photoUrls?.size ?: 0} фото$caption"
            }
            "video" -> {
                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                "${senderPrefix}🎥 Видео$caption"
            }
            "file" -> "${senderPrefix}📎 ${msg.fileName ?: "Файл"}"
            "voice" -> "${senderPrefix}🎤 Голосовое сообщение"
            else -> "$senderPrefix${msg.content ?: ""}"
        }

        val updated = existing.copy(
            lastMessageText = lastText,
            lastMessageTime = msg.createdAt ?: existing.lastMessageTime,
            unreadCount = if (isOwn) existing.unreadCount else existing.unreadCount + 1,
            lastMessageIsOwn = isOwn,
            lastMessageIsRead = false
        )
        // Move chat to top
        _chats.value = listOf(updated) + current.filter { it.chatId != chatId }
    }

    // Refresh when someone reads messages (unread badge update)
    private fun observeReadReceipts() {
        viewModelScope.launch {
            try {
                messageRepo.messageReadInsertFlowAll().collect { silentRefresh() }
            } catch (_: Exception) {}
        }
    }

    // Update online indicator in-memory when presence changes — no server roundtrip.
    // Previously called silentRefresh() here, which fired getChatsForUser every ~1.5s
    // (two users × 3s heartbeat = one presence event per 1.5s).
    private fun observePresenceUpdates() {
        viewModelScope.launch {
            try {
                userRepo.presenceUpdateFlowAll().collect { presence ->
                    val online = presence.isTrulyOnline()
                    _chats.value = _chats.value.map { chat ->
                        if (!chat.isGroup && chat.otherUserId == presence.userId)
                            chat.copy(isOtherOnline = online)
                        else chat
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteChat(chatId: String) {
        chatRepo.deleteChat(chatId)
        silentRefresh()
    }

    suspend fun clearHistory(chatId: String) {
        chatRepo.clearChatHistory(chatId)
        silentRefresh()
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            app.logout()
            onDone()
        }
    }

    private fun startTypingPolling() {
        viewModelScope.launch {
            while (true) {
                delay(3_000L)
                val chats = _chats.value
                if (chats.isNotEmpty()) {
                    val chatIds = chats.map { it.chatId }
                    val typingByChat = messageRepo.getTypingForChats(chatIds, currentUserId)
                    val result = mutableMapOf<String, String>()
                    for (chat in chats) {
                        val users = typingByChat[chat.chatId] ?: continue
                        val text = typingText(users, chat.isGroup) ?: continue
                        result[chat.chatId] = text
                    }
                    _chatTyping.value = result
                }
            }
        }
    }

    private fun typingText(users: List<TypingStatus>, isGroup: Boolean): String? {
        if (users.isEmpty()) return null
        val uploading = users.filter { it.status == "uploading_media" }
        if (uploading.isNotEmpty()) {
            return if (!isGroup) "Загружает медиа..."
            else when (uploading.size) {
                1 -> "${uploading[0].displayName} загружает медиа..."
                else -> "${uploading[0].displayName} и ещё ${uploading.size - 1} загружают медиа..."
            }
        }
        return if (!isGroup) "Печатает..."
        else when (users.size) {
            1 -> "${users[0].displayName} печатает..."
            2 -> "${users[0].displayName} и ${users[1].displayName} печатают..."
            else -> "${users[0].displayName}, ${users[1].displayName} и ещё ${users.size - 2} печатают..."
        }
    }
}
