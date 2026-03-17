package com.example.svoi.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val authRepo = app.authRepository

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    // Registration wizard state — shared between the 3 setup screens
    internal var regInviteKey = ""
    internal var regDisplayName = ""
    internal var regAbout = ""
    internal var regEmail = ""
    internal var regPassword = ""

    fun validateStep1(displayName: String, about: String, email: String, onNext: () -> Unit) {
        clearError()
        if (displayName.isBlank()) { _error.value = "Введите имя"; return }
        if (email.isBlank() || !email.contains("@")) { _error.value = "Введите корректный email"; return }
        regDisplayName = displayName.trim()
        regAbout = about.trim()
        regEmail = email.trim().lowercase()
        onNext()
    }

    fun validateStep2(password: String, confirmPassword: String, onNext: () -> Unit) {
        clearError()
        if (password.length < 6) { _error.value = "Пароль должен быть не менее 6 символов"; return }
        if (password != confirmPassword) { _error.value = "Пароли не совпадают"; return }
        regPassword = password
        onNext()
    }

    fun finishSignUp(emoji: String, bgColor: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val err = authRepo.signUpWithInviteKey(
                inviteKey = regInviteKey,
                email = regEmail,
                password = regPassword,
                displayName = regDisplayName,
                about = regAbout,
                emoji = emoji,
                bgColor = bgColor
            )
            if (err == null) {
                app.startPresenceHeartbeat()
                app.registerFcmToken()
                onSuccess()
            } else {
                _error.value = err
            }
            _isLoading.value = false
        }
    }

    fun validateInviteKey(key: String, onValid: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val valid = authRepo.validateInviteKey(key.trim())
            if (valid) {
                onValid()
            } else {
                _error.value = "Ключ недействителен или уже использован"
            }
            _isLoading.value = false
        }
    }

    fun signUp(
        inviteKey: String,
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String,
        emoji: String,
        bgColor: String,
        onSuccess: () -> Unit
    ) {
        if (displayName.isBlank()) {
            _error.value = "Введите имя"
            return
        }
        if (email.isBlank() || !email.contains("@")) {
            _error.value = "Введите корректный email"
            return
        }
        if (password.length < 6) {
            _error.value = "Пароль должен быть не менее 6 символов"
            return
        }
        if (password != confirmPassword) {
            _error.value = "Пароли не совпадают"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val err = authRepo.signUpWithInviteKey(
                inviteKey = inviteKey,
                email = email.trim().lowercase(),
                password = password,
                displayName = displayName.trim(),
                emoji = emoji,
                bgColor = bgColor
            )
            if (err == null) {
                app.startPresenceHeartbeat()
                app.registerFcmToken()
                onSuccess()
            } else {
                _error.value = err
            }
            _isLoading.value = false
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank()) { _error.value = "Введите email"; return }
        if (password.isBlank()) { _error.value = "Введите пароль"; return }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val err = authRepo.signIn(email.trim().lowercase(), password)
            if (err == null) {
                app.startPresenceHeartbeat()
                app.registerFcmToken()
                onSuccess()
            } else {
                _error.value = err
            }
            _isLoading.value = false
        }
    }
}
