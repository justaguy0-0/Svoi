package com.example.svoi.ui.newchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        _isSearching.value = true
                        _searchResults.value = userRepo.searchUsers(query)
                        _isSearching.value = false
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

    /** Open or create a personal chat with the given user */
    fun openPersonalChat(userId: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val existing = chatRepo.findPersonalChat(userId)
            val chatId = existing ?: chatRepo.createPersonalChat(userId)
            if (chatId != null) onOpened(chatId)
            else _error.value = "Не удалось открыть чат"
            _isLoading.value = false
        }
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        val memberIds = _selectedUsers.value.map { it.id }
        if (memberIds.isEmpty()) {
            _error.value = "Выберите участников"
            return
        }
        if (name.isBlank()) {
            _error.value = "Введите название группы"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val chatId = chatRepo.createGroupChat(name.trim(), memberIds)
            if (chatId != null) onCreated(chatId)
            else _error.value = "Не удалось создать группу"
            _isLoading.value = false
        }
    }

    fun clearError() { _error.value = null }
}
