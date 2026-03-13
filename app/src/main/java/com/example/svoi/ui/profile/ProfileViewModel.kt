package com.example.svoi.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val userRepo = app.userRepository
    private val authRepo = app.authRepository
    private val cache = app.cacheManager

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            // Show cached profile immediately
            val cached = cache.loadOwnProfile()
            if (cached != null) {
                _profile.value = cached
                _isLoading.value = false
            } else {
                _isLoading.value = true
            }
            // Fetch fresh from network
            val fresh = userRepo.getCurrentProfile()
            if (fresh != null) {
                _profile.value = fresh
                cache.saveOwnProfile(fresh)
            }
            _isLoading.value = false
        }
    }

    fun saveNameAndAbout(displayName: String, statusText: String) {
        if (displayName.isBlank()) {
            _error.value = "Имя не может быть пустым"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val err = userRepo.updateNameAndAbout(displayName.trim(), statusText.trim())
            if (err == null) {
                val updated = _profile.value?.copy(
                    displayName = displayName.trim(),
                    statusText = statusText.trim()
                )
                if (updated != null) {
                    _profile.value = updated
                    cache.saveOwnProfile(updated)
                }
                _successMessage.value = "Профиль сохранён"
            } else {
                _error.value = err
            }
            _isSaving.value = false
        }
    }

    fun saveAvatar(emoji: String, bgColor: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val err = userRepo.updateAvatar(emoji, bgColor)
            if (err == null) {
                val updated = _profile.value?.copy(emoji = emoji, bgColor = bgColor)
                if (updated != null) {
                    _profile.value = updated
                    cache.saveOwnProfile(updated)
                }
                _successMessage.value = "Аватар сохранён"
            } else {
                _error.value = err
            }
            _isSaving.value = false
        }
    }

    fun changePassword(current: String, newPassword: String, confirm: String) {
        if (newPassword.length < 6) {
            _error.value = "Пароль должен быть не менее 6 символов"
            return
        }
        if (newPassword != confirm) {
            _error.value = "Пароли не совпадают"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val err = authRepo.changePassword(newPassword)
            if (err == null) _successMessage.value = "Пароль изменён"
            else _error.value = err
            _isSaving.value = false
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            app.logout()
            onDone()
        }
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}
