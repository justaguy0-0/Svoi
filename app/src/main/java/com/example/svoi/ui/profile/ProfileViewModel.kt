package com.example.svoi.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.HiddenOnlineStyle
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

    private val _isSendingPasswordReset = MutableStateFlow(false)
    val isSendingPasswordReset: StateFlow<Boolean> = _isSendingPasswordReset

    private val _isSavingHiddenFromSearch = MutableStateFlow(false)
    val isSavingHiddenFromSearch: StateFlow<Boolean> = _isSavingHiddenFromSearch

    private val _isSavingOnlinePrivacy = MutableStateFlow(false)
    val isSavingOnlinePrivacy: StateFlow<Boolean> = _isSavingOnlinePrivacy

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

    private var passwordResetEmail: String? = null

    fun preparePasswordReset(onConfirmationReady: () -> Unit) {
        Log.d("PasswordChange", "profile request clicked")
        if (_isSendingPasswordReset.value) return
        viewModelScope.launch {
            val email = authRepo.currentUserEmail()
            if (email.isNullOrBlank()) {
                Log.w("PasswordChange", "missing email")
                _error.value = "У аккаунта нет email для восстановления пароля"
                return@launch
            }
            passwordResetEmail = email
            Log.d("PasswordChange", "confirmation shown")
            onConfirmationReady()
        }
    }

    fun sendPasswordResetEmail(onSent: () -> Unit) {
        if (_isSendingPasswordReset.value) return
        val email = passwordResetEmail
        if (email.isNullOrBlank()) {
            Log.w("PasswordChange", "missing email")
            _error.value = "У аккаунта нет email для восстановления пароля"
            return
        }
        viewModelScope.launch {
            _isSendingPasswordReset.value = true
            Log.d("PasswordChange", "reset email requested")
            val err = authRepo.sendPasswordResetEmail(email)
            if (err == null) {
                Log.d("PasswordChange", "reset email sent")
                _successMessage.value = "Письмо для смены пароля отправлено"
                passwordResetEmail = null
                onSent()
            } else {
                Log.w("PasswordChange", "send failed")
                _error.value = "Не удалось отправить письмо. Проверьте интернет и попробуйте снова."
            }
            _isSendingPasswordReset.value = false
        }
    }

    /**
     * Toggles the "hidden from search" flag. Uses an optimistic update:
     * the UI changes immediately and reverts if the server call fails.
     */
    fun setHiddenFromSearch(hidden: Boolean) {
        val updated = _profile.value?.copy(hiddenFromSearch = hidden) ?: return
        _profile.value = updated
        cache.saveOwnProfile(updated)
        viewModelScope.launch {
            _isSaving.value = true
            _isSavingHiddenFromSearch.value = true
            val err = userRepo.updateHiddenFromSearch(hidden)
            if (err != null) {
                // Revert on failure
                val reverted = _profile.value?.copy(hiddenFromSearch = !hidden)
                if (reverted != null) {
                    _profile.value = reverted
                    cache.saveOwnProfile(reverted)
                }
                _error.value = "Не удалось сохранить настройку"
            }
            _isSavingHiddenFromSearch.value = false
            _isSaving.value = false
        }
    }

    fun setOnlinePrivacy(hideOnlineStatus: Boolean, style: HiddenOnlineStyle? = null) {
        val current = _profile.value ?: return
        val nextStyle = style?.dbValue ?: HiddenOnlineStyle.fromDb(current.hiddenOnlineStyle).dbValue
        val updated = current.copy(
            hideOnlineStatus = hideOnlineStatus,
            hiddenOnlineStyle = nextStyle
        )
        _profile.value = updated
        cache.saveOwnProfile(updated)
        viewModelScope.launch {
            _isSaving.value = true
            _isSavingOnlinePrivacy.value = true
            val err = userRepo.updateOnlinePrivacy(hideOnlineStatus, nextStyle)
            if (err != null) {
                _profile.value = current
                cache.saveOwnProfile(current)
                _error.value = "Не удалось сохранить настройку"
            }
            _isSavingOnlinePrivacy.value = false
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
