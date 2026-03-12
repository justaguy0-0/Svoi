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
                app.userRepository.setOnline(true)
                app.registerFcmToken()
                onSuccess()
            } else {
                _error.value = err
            }
            _isLoading.value = false
        }
    }
}
