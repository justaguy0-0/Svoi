package com.example.svoi.ui.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.TypingStatus
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

    private val _currentProfile = MutableStateFlow<com.example.svoi.data.model.Profile?>(null)
    val currentProfile: StateFlow<com.example.svoi.data.model.Profile?> = _currentProfile

    // Track whether we need to show "Обновление..." on next refresh
    private var initialLoad = true
    private var wasOffline = false

    // Mutex ensures only one getChatsForUser() runs at a time — prevents partial/stale overwrites
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var loadJob: Job? = null
    // Cooldown: prevent silentRefresh more than once per 5s (protects against rapid Realtime events)
    private var lastRefreshMs = 0L

    init {
        // Watch for going offline so we show "Обновление..." when reconnecting
        viewModelScope.launch {
            isOnline.collect { online ->
                if (!online) wasOffline = true
                else if (wasOffline) {
                    // Just came back online — do a visible refresh
                    wasOffline = false
                    loadChats(showUpdating = true)
                }
            }
        }
        loadChats(showUpdating = true)  // initial load
        viewModelScope.launch { _currentProfile.value = app.userRepository.getCurrentProfile() }
        observeNewMessages()
        observeReadReceipts()
        observePresenceUpdates()
        startTypingPolling()
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

            // 3. Fetch fresh from server — mutex ensures no parallel fetch overwrites this
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

    // Debounced silent refresh — skips if a full loadChats() is already running.
    // Cooldown: at most once per 5s to avoid hammering the DB on rapid Realtime events.
    fun silentRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(500) // debounce: ignore rapid-fire events
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefreshMs
            if (elapsed < 5_000L) {
                delay(5_000L - elapsed) // wait out cooldown
            }
            if (!refreshMutex.tryLock()) return@launch  // loadChats in progress — it will have fresh data
            try {
                lastRefreshMs = System.currentTimeMillis()
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
                messageRepo.messageInsertFlowAll().collect { silentRefresh() }
            } catch (_: Exception) {}
        }
    }

    // Refresh when someone reads messages (unread badge update)
    private fun observeReadReceipts() {
        viewModelScope.launch {
            try {
                messageRepo.messageReadInsertFlowAll().collect { silentRefresh() }
            } catch (_: Exception) {}
        }
    }

    // Refresh when any user's presence changes (online dot update)
    private fun observePresenceUpdates() {
        viewModelScope.launch {
            try {
                userRepo.presenceUpdateFlowAll().collect { silentRefresh() }
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
            app.authRepository.signOut()
            onDone()
        }
    }

    private fun startTypingPolling() {
        viewModelScope.launch {
            while (true) {
                delay(5_000L)
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
        return if (!isGroup) "Печатает..."
        else when (users.size) {
            1 -> "${users[0].displayName} печатает..."
            2 -> "${users[0].displayName} и ${users[1].displayName} печатают..."
            else -> "${users[0].displayName}, ${users[1].displayName} и ещё ${users.size - 2} печатают..."
        }
    }
}
