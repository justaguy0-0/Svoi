package com.example.svoi.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val userRepo = app.userRepository
    private val chatRepo = app.chatRepository

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private val _presence = MutableStateFlow<UserPresence?>(null)
    val presence: StateFlow<UserPresence?> = _presence

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var loadedUserId: String = ""
    private var pollJob: Job? = null

    fun load(userId: String) {
        if (loadedUserId == userId) return
        loadedUserId = userId
        pollJob?.cancel()
        viewModelScope.launch {
            _isLoading.value = true
            _profile.value = userRepo.getProfile(userId)
            _presence.value = userRepo.getPresence(userId)
            _isLoading.value = false
        }
        pollJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                _presence.value = userRepo.getPresence(userId)
            }
        }
    }

    suspend fun getOrCreateChat(userId: String): String? {
        val existing = chatRepo.findPersonalChat(userId)
        if (existing != null) return existing
        return chatRepo.createPersonalChat(userId)
    }
}
