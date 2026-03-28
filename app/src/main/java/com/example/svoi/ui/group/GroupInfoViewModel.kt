package com.example.svoi.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatMember
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MemberUiItem(
    val member: ChatMember,
    val profile: Profile?,
    val presence: UserPresence?
)

class GroupInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val chatRepo = app.chatRepository
    private val userRepo = app.userRepository
    private val messageRepo = app.messageRepository

    private val _chat = MutableStateFlow<Chat?>(null)
    val chat: StateFlow<Chat?> = _chat

    private val _members = MutableStateFlow<List<MemberUiItem>>(emptyList())
    val members: StateFlow<List<MemberUiItem>> = _members

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId

    /** Becomes true when the chat is externally deleted — screen should close */
    private val _chatDeleted = MutableStateFlow(false)
    val chatDeleted: StateFlow<Boolean> = _chatDeleted

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    private var chatId: String = ""
    private var ownProfile: Profile? = null
    private var pollJob: Job? = null

    fun init(chatId: String) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        val userId = app.supabase.auth.currentUserOrNull()?.id ?: ""
        _currentUserId.value = userId
        viewModelScope.launch {
            ownProfile = userRepo.getProfile(userId)
            load()
            startPolling()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            refreshData()
            _isLoading.value = false
        }
    }

    private suspend fun refreshData() {
        val chat = chatRepo.getChat(chatId)
        if (chat == null) {
            // Chat was deleted by someone else
            _chatDeleted.value = true
            return
        }
        _chat.value = chat

        val userId = _currentUserId.value
        val rawMembers = chatRepo.getChatMembers(chatId)

        val myMember = rawMembers.firstOrNull { it.userId == userId }
        _isAdmin.value = myMember?.role == "admin" || chat.createdBy == userId

        val memberIds = rawMembers.map { it.userId }
        val profiles = if (memberIds.isEmpty()) emptyList()
        else userRepo.getProfiles(memberIds)
        val profileMap = profiles.associateBy { it.id }

        val presences = runCatching { userRepo.getPresences(memberIds) }
            .getOrDefault(emptyList())
            .associateBy { it.userId }

        _members.value = rawMembers.map { member ->
            MemberUiItem(
                member = member,
                profile = profileMap[member.userId],
                presence = presences[member.userId]
            )
        }.sortedWith(compareBy(
            { it.member.role != "admin" },
            { it.profile?.displayName ?: "" }
        ))
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(8_000L)
                runCatching { refreshData() }
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            val removed = _members.value.firstOrNull { it.member.userId == userId }
            val removedName = removed?.profile?.displayName ?: "Участник"
            val myName = ownProfile?.displayName ?: "Администратор"

            if (chatRepo.removeMember(chatId, userId)) {
                messageRepo.sendSystemMessage(chatId, "$myName исключил(а) $removedName")
                _successMessage.value = "$removedName исключён из группы"
            } else {
                _error.value = "Не удалось исключить участника"
            }
            refreshData()
        }
    }

    fun renameGroup(newName: String) {
        if (newName.isBlank()) return
        val trimmed = newName.trim()
        viewModelScope.launch {
            val myName = ownProfile?.displayName ?: "Администратор"
            if (chatRepo.renameGroup(chatId, trimmed)) {
                messageRepo.sendSystemMessage(chatId, "$myName изменил(а) название на «$trimmed»")
                _successMessage.value = "Группа переименована"
            } else {
                _error.value = "Не удалось переименовать группу"
            }
            refreshData()
        }
    }

    fun deleteGroup(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val myName = ownProfile?.displayName ?: "Администратор"
            // Send system message before deleting so members see it in realtime
            messageRepo.sendSystemMessage(chatId, "$myName удалил(а) группу")
            // Small delay so realtime can propagate the message
            delay(300)
            // Soft delete: archives to deleted_chats + writes audit_log
            chatRepo.softDeleteChat(chatId)
            onDeleted()
        }
    }

    fun addMember(userId: String, showHistory: Boolean) {
        viewModelScope.launch {
            val addedProfile = userRepo.getProfile(userId)
            val addedName = addedProfile?.displayName ?: "Пользователь"
            val myName = ownProfile?.displayName ?: "Администратор"

            val historyFrom: String? = if (showHistory) {
                // Fetch last 100 messages — if fewer than 100 exist, show all (null)
                val messages = messageRepo.getMessages(chatId, limit = 100, offset = 0)
                if (messages.size < 100) null else messages.firstOrNull()?.createdAt
            } else {
                // Show only future messages — restrict to messages after now
                java.time.Instant.now().toString()
            }

            if (chatRepo.addMember(chatId, userId, historyFrom)) {
                val sysMsg = messageRepo.sendSystemMessage(chatId, "$myName добавил(а) $addedName")
                // Mark all history messages as read for the new member (up to the system message)
                if (showHistory) {
                    val beforeTs = sysMsg?.createdAt ?: java.time.Instant.now().toString()
                    messageRepo.markHistoryRead(chatId, userId, beforeTs)
                }
                _successMessage.value = "$addedName добавлен в группу"
            } else {
                _error.value = "Не удалось добавить участника"
            }
            refreshData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
