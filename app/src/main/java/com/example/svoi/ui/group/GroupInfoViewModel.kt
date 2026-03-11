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

    private var chatId: String = ""
    private var pollJob: Job? = null

    fun init(chatId: String) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        val userId = app.supabase.auth.currentUserOrNull()?.id ?: ""
        _currentUserId.value = userId
        load()
        startPolling()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            refreshData()
            _isLoading.value = false
        }
    }

    private suspend fun refreshData() {
        val chat = chatRepo.getChat(chatId) ?: return
        _chat.value = chat

        val userId = _currentUserId.value
        val rawMembers = chatRepo.getChatMembers(chatId)

        // Determine if current user is admin
        val myMember = rawMembers.firstOrNull { it.userId == userId }
        _isAdmin.value = myMember?.role == "admin" || chat.createdBy == userId

        val memberIds = rawMembers.map { it.userId }
        val profiles = if (memberIds.isEmpty()) emptyList()
        else userRepo.getProfiles(memberIds)
        val profileMap = profiles.associateBy { it.id }

        val presences = memberIds.mapNotNull { id ->
            runCatching { userRepo.getPresence(id) }.getOrNull()?.let { id to it }
        }.toMap()

        _members.value = rawMembers.map { member ->
            MemberUiItem(
                member = member,
                profile = profileMap[member.userId],
                presence = presences[member.userId]
            )
        }.sortedWith(compareBy(
            { it.member.role != "admin" }, // admins first
            { it.profile?.displayName ?: "" }
        ))
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(15_000L)
                runCatching { refreshData() }
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            chatRepo.removeMember(chatId, userId)
            refreshData()
        }
    }

    fun renameGroup(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            chatRepo.renameGroup(chatId, newName.trim())
            refreshData()
        }
    }

    fun deleteGroup(onDeleted: () -> Unit) {
        viewModelScope.launch {
            chatRepo.deleteChat(chatId)
            onDeleted()
        }
    }

    fun addMember(userId: String) {
        viewModelScope.launch {
            chatRepo.addMember(chatId, userId)
            refreshData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
