package com.example.svoi.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val userRepo = app.userRepository
    private val authRepo = app.authRepository

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

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _profile.value = userRepo.getCurrentProfile()
            _isLoading.value = false
        }
    }

    fun saveProfile(
        displayName: String,
        statusText: String,
        emoji: String,
        bgColor: String
    ) {
        if (displayName.isBlank()) {
            _error.value = "Имя не может быть пустым"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val err = userRepo.updateProfile(displayName.trim(), statusText.trim(), emoji, bgColor)
            if (err == null) {
                _successMessage.value = "Профиль сохранён"
                loadProfile()
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
            authRepo.signOut()
            onDone()
        }
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}
