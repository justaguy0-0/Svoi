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

    // true while the initial spinner should show (no cached data yet)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // true while fetching fresh data from server (Telegram-style "Обновление")
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        loadChats()
        observeNewMessages()
    }

    fun loadChats() {
        viewModelScope.launch {
            // 1. Show cache instantly if available
            val cached = cache.loadChatList()
            if (cached != null) {
                _chats.value = cached
                _isLoading.value = false
            } else {
                _isLoading.value = true
            }

            // 2. Fetch fresh from server
            _isUpdating.value = true
            val fresh = chatRepo.getChatsForUser()
            if (fresh.isNotEmpty()) {
                _chats.value = fresh
                cache.saveChatList(fresh)
            }
            _isUpdating.value = false
            _isLoading.value = false
        }
    }

    private fun observeNewMessages() {
        viewModelScope.launch {
            try {
                messageRepo.messageInsertFlowAll().collect {
                    loadChats()
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteChat(chatId: String) {
        chatRepo.deleteChat(chatId)
        loadChats()
    }

    suspend fun clearHistory(chatId: String) {
        chatRepo.clearChatHistory(chatId)
        loadChats()
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            app.authRepository.signOut()
            onDone()
        }
    }
}
