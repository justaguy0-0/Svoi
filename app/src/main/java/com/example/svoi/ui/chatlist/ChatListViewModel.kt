package com.example.svoi.ui.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.ChatListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val chatRepo = app.chatRepository
    private val messageRepo = app.messageRepository
    private val cache = app.cacheManager

    private val _chats = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chats: StateFlow<List<ChatListItem>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // true only during initial app load or after reconnect — shown as "Обновление..."
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Track whether we need to show "Обновление..." on next refresh
    private var initialLoad = true
    private var wasOffline = false

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
        observeNewMessages()
    }

    fun loadChats(showUpdating: Boolean = false) {
        viewModelScope.launch {
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

            // 3. Fetch fresh from server
            val fresh = chatRepo.getChatsForUser()
            if (fresh.isNotEmpty()) {
                _chats.value = fresh
                cache.saveChatList(fresh)
            }

            initialLoad = false
            _isUpdating.value = false
            _isLoading.value = false
        }
    }

    // Silent refresh — no animation, used for real-time new message events and on resume
    fun silentRefresh() {
        viewModelScope.launch {
            val fresh = chatRepo.getChatsForUser()
            if (fresh.isNotEmpty()) {
                _chats.value = fresh
                cache.saveChatList(fresh)
            }
        }
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            try {
                messageRepo.messageInsertFlowAll().collect {
                    silentRefresh()
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
            app.authRepository.signOut()
            onDone()
        }
    }
}
