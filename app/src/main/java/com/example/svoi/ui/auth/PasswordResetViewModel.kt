package com.example.svoi.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasswordResetUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val recoveryReady: Boolean = false
)

class PasswordResetViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val authRepo = app.authRepository

    private val _uiState = MutableStateFlow(PasswordResetUiState())
    val uiState: StateFlow<PasswordResetUiState> = _uiState

    fun clearMessages() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    fun sendPasswordResetEmail(email: String) {
        val normalizedEmail = email.trim().lowercase()
        when {
            normalizedEmail.isBlank() -> {
                _uiState.update { it.copy(error = "Введите email", message = null) }
                return
            }
            !isValidEmail(normalizedEmail) -> {
                _uiState.update { it.copy(error = "Введите корректный email", message = null) }
                return
            }
            _uiState.value.isLoading -> return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            val err = authRepo.sendPasswordResetEmail(normalizedEmail)
            if (err == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Если аккаунт с таким email существует, мы отправили ссылку для восстановления."
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = err) }
            }
        }
    }

    fun waitForRecoverySession() {
        viewModelScope.launch {
            delay(3_000L)
            if (authRepo.isLoggedIn()) {
                Log.d("PasswordReset", "recovery session handled")
                _uiState.update { it.copy(recoveryReady = true, error = null) }
            } else {
                Log.w("PasswordReset", "invalid recovery link")
                _uiState.update {
                    it.copy(error = "Ссылка недействительна или устарела", recoveryReady = false)
                }
            }
        }
    }

    fun markRecoverySessionHandled() {
        if (authRepo.isLoggedIn()) {
            Log.d("PasswordReset", "recovery session handled")
            _uiState.update { it.copy(recoveryReady = true, error = null) }
        } else {
            _uiState.update { it.copy(recoveryReady = false) }
        }
    }

    fun saveNewPassword(
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        when {
            password.isBlank() -> {
                _uiState.update { it.copy(error = "Введите новый пароль", message = null) }
                return
            }
            password.length < 6 -> {
                _uiState.update { it.copy(error = "Пароль должен быть не менее 6 символов", message = null) }
                return
            }
            password != confirmPassword -> {
                _uiState.update { it.copy(error = "Пароли не совпадают", message = null) }
                return
            }
            !authRepo.isLoggedIn() -> {
                Log.w("PasswordReset", "invalid recovery link")
                _uiState.update { it.copy(error = "Ссылка недействительна или устарела", message = null) }
                return
            }
            _uiState.value.isLoading -> return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            val err = authRepo.changePassword(password)
            if (err == null) {
                _uiState.update { it.copy(isLoading = false, message = "Пароль изменён") }
                delay(700L)
                app.logout()
                onSuccess()
            } else {
                _uiState.update { it.copy(isLoading = false, error = err) }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") &&
            email.substringBefore("@").isNotBlank() &&
            email.substringAfter("@").contains(".") &&
            email.substringAfterLast(".").length >= 2
    }
}
