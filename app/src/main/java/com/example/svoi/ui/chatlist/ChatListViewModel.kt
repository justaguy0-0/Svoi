package com.example.svoi.ui.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.ChatListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val chatRepo = app.chatRepository
    private val messageRepo = app.messageRepository

    private val _chats = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chats: StateFlow<List<ChatListItem>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadChats()
        observeNewMessages()
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _chats.value = chatRepo.getChatsForUser()
            _isLoading.value = false
        }
    }

    private fun observeNewMessages() {
        // Realtime: when any new message arrives, refresh chat list
        // We subscribe to a broad channel and refresh on any insert
        viewModelScope.launch {
            try {
                messageRepo.messageInsertFlow("").collect {
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
