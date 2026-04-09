package com.example.svoi.ui.newchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class NewChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val userRepo = app.userRepository
    private val chatRepo = app.chatRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Profile>>(emptyList())
    val searchResults: StateFlow<List<Profile>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _selectedUsers = MutableStateFlow<List<Profile>>(emptyList())
    val selectedUsers: StateFlow<List<Profile>> = _selectedUsers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** All users the current user shares at least one chat with (loaded once on init). */
    private val _contacts = MutableStateFlow<List<Profile>>(emptyList())

    /** True while the initial contacts list is loading. */
    private val _contactsLoading = MutableStateFlow(false)
    val contactsLoading: StateFlow<Boolean> = _contactsLoading

    /** Contacts filtered by the current search query — for CreateGroupScreen. */
    val filteredContacts: StateFlow<List<Profile>> = _searchQuery
        .combine(_contacts) { query, contacts ->
            if (query.isBlank()) contacts
            else contacts.filter { it.displayName.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _contactsLoading.value = true
            try {
                _contacts.value = userRepo.getMyContacts()
            } catch (_: Exception) {
                // empty list handled in UI
            } finally {
                _contactsLoading.value = false
            }
        }
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                        _error.value = null
                    } else {
                        _isSearching.value = true
                        _error.value = null
                        try {
                            _searchResults.value = userRepo.searchUsers(query)
                        } catch (_: Exception) {
                            _searchResults.value = emptyList()
                            _error.value = "Ошибка поиска. Проверьте соединение."
                        } finally {
                            _isSearching.value = false
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleUserSelection(profile: Profile) {
        val current = _selectedUsers.value.toMutableList()
        if (current.any { it.id == profile.id }) {
            current.removeAll { it.id == profile.id }
        } else {
            current.add(profile)
        }
        _selectedUsers.value = current
    }

    fun isSelected(profileId: String) = _selectedUsers.value.any { it.id == profileId }

    /** Opens an existing personal chat, or signals that a draft should be opened (no DB write). */
    fun openPersonalChat(userId: String, onExisting: (String) -> Unit, onDraft: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = chatRepo.findPersonalChat(userId)
                if (existing != null) onExisting(existing) else onDraft()
            } catch (_: Exception) {
                _error.value = "Не удалось открыть чат. Проверьте соединение."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        val memberIds = _selectedUsers.value.map { it.id }
        if (memberIds.isEmpty()) {
            _error.value = "Выберите хотя бы одного участника"
            return
        }
        if (name.isBlank()) {
            _error.value = "Введите название группы"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val chatId = chatRepo.createGroupChat(name.trim(), memberIds)
                if (chatId != null) onCreated(chatId)
                else _error.value = "Не удалось создать группу. Проверьте соединение."
            } catch (_: Exception) {
                _error.value = "Не удалось создать группу. Проверьте соединение."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
