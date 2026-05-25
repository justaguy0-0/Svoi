package com.example.svoi.ui.announcements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.BuildConfig
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.AppAnnouncement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val WHATS_NEW_LIMIT = 5

data class WhatsNewUiState(
    val isLoading: Boolean = false,
    val items: List<AppAnnouncement> = emptyList(),
    val error: Boolean = false
)

class WhatsNewViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val repository = app.appAnnouncementRepository

    private val _state = MutableStateFlow(WhatsNewUiState(isLoading = true))
    val state: StateFlow<WhatsNewUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = false) }
            try {
                val announcements = repository.fetchLatestActiveAnnouncements(
                    versionCode = BuildConfig.VERSION_CODE,
                    limit = WHATS_NEW_LIMIT
                )
                _state.value = WhatsNewUiState(items = announcements)
            } catch (_: Exception) {
                _state.value = WhatsNewUiState(error = true)
            }
        }
    }
}
