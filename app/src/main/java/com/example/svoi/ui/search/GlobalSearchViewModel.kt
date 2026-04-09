package com.example.svoi.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.MessageSearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val messageRepo = (application as SvoiApp).messageRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<MessageSearchResult>>(emptyList())
    val results: StateFlow<List<MessageSearchResult>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    init {
        viewModelScope.launch {
            _query
                .debounce(350)
                .distinctUntilChanged()
                .collect { q ->
                    val trimmed = q.trim()
                    if (trimmed.length < 2) {
                        _results.value = emptyList()
                        _isSearching.value = false
                    } else {
                        _isSearching.value = true
                        _results.value = messageRepo.searchMessages(trimmed)
                        _isSearching.value = false
                    }
                }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }
}
