package com.example.svoi.ui.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── UI models ──────────────────────────────────────────────────────────────

data class MediaSection(
    val monthLabel: String,
    val yearLabel: String,
    val items: List<MediaItem>
)

sealed class MediaItem {
    data class Photo(val url: String, val messageId: String) : MediaItem()
    data class Video(
        val url: String,
        val fileName: String?,
        val duration: Int?,
        val messageId: String
    ) : MediaItem()
    data class Voice(
        val url: String,
        val duration: Int?,
        val createdAt: String,
        val messageId: String
    ) : MediaItem()
}

// ── ViewModel ──────────────────────────────────────────────────────────────

class ChatMediaViewModel(application: Application) : AndroidViewModel(application) {

    private val messageRepo = (application as SvoiApp).messageRepository

    private val _photos = MutableStateFlow<List<MediaSection>>(emptyList())
    val photos: StateFlow<List<MediaSection>> = _photos

    private val _videos = MutableStateFlow<List<MediaSection>>(emptyList())
    val videos: StateFlow<List<MediaSection>> = _videos

    private val _voices = MutableStateFlow<List<MediaSection>>(emptyList())
    val voices: StateFlow<List<MediaSection>> = _voices

    private val _totalPhotos = MutableStateFlow(0)
    val totalPhotos: StateFlow<Int> = _totalPhotos

    private val _totalVideos = MutableStateFlow(0)
    val totalVideos: StateFlow<Int> = _totalVideos

    private val _totalVoices = MutableStateFlow(0)
    val totalVoices: StateFlow<Int> = _totalVoices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var loadedChatId = ""

    fun load(chatId: String) {
        if (loadedChatId == chatId) return
        loadedChatId = chatId
        viewModelScope.launch {
            _isLoading.value = true
            val messages = messageRepo.getMediaMessages(chatId)

            // ── Photos (type=photo or album) ──────────────────────────────
            val photoMessages = messages.filter { it.type == "photo" || it.type == "album" }
            val allPhotoItems: List<Pair<MediaItem.Photo, String>> = photoMessages.flatMap { msg ->
                val createdAt = msg.createdAt ?: return@flatMap emptyList()
                when (msg.type) {
                    "album" -> (msg.photoUrls ?: emptyList()).map { url ->
                        Pair(MediaItem.Photo(url, msg.id), createdAt)
                    }
                    else -> listOfNotNull(
                        msg.fileUrl?.let { Pair(MediaItem.Photo(it, msg.id), createdAt) }
                    )
                }
            }
            _totalPhotos.value = allPhotoItems.size
            _photos.value = allPhotoItems.groupIntoSections { it.second }
                .map { (key, pairs) ->
                    MediaSection(
                        monthLabel = monthName(key),
                        yearLabel = key.substring(0, 4),
                        items = pairs.map { it.first }
                    )
                }

            // ── Videos ────────────────────────────────────────────────────
            val videoMessages = messages.filter { it.type == "video" }
            val allVideoItems: List<Pair<MediaItem.Video, String>> = videoMessages.mapNotNull { msg ->
                val createdAt = msg.createdAt ?: return@mapNotNull null
                msg.fileUrl?.let { url ->
                    Pair(MediaItem.Video(url, msg.fileName, msg.duration, msg.id), createdAt)
                }
            }
            _totalVideos.value = allVideoItems.size
            _videos.value = allVideoItems.groupIntoSections { it.second }
                .map { (key, pairs) ->
                    MediaSection(
                        monthLabel = monthName(key),
                        yearLabel = key.substring(0, 4),
                        items = pairs.map { it.first }
                    )
                }

            // ── Voice messages ────────────────────────────────────────────
            val voiceMessages = messages.filter { it.type == "voice" }
            val allVoiceItems: List<Pair<MediaItem.Voice, String>> = voiceMessages.mapNotNull { msg ->
                val createdAt = msg.createdAt ?: return@mapNotNull null
                msg.fileUrl?.let { url ->
                    Pair(MediaItem.Voice(url, msg.duration, createdAt, msg.id), createdAt)
                }
            }
            _totalVoices.value = allVoiceItems.size
            _voices.value = allVoiceItems.groupIntoSections { it.second }
                .map { (key, pairs) ->
                    MediaSection(
                        monthLabel = monthName(key),
                        yearLabel = key.substring(0, 4),
                        items = pairs.map { it.first }
                    )
                }

            _isLoading.value = false
        }
    }

    // Groups items by "YYYY-MM", sorted newest-first.
    private fun <T> List<T>.groupIntoSections(
        createdAtFn: (T) -> String
    ): List<Map.Entry<String, List<T>>> {
        return groupBy { item ->
            // ISO timestamp starts "YYYY-MM-..." — take first 7 chars
            runCatching { createdAtFn(item).substring(0, 7) }.getOrDefault("0000-00")
        }.entries.sortedByDescending { it.key }
    }

    private fun monthName(yearMonth: String): String {
        return runCatching {
            val instant = Instant.parse("${yearMonth}-01T00:00:00Z")
            DateTimeFormatter.ofPattern("LLLL", Locale("ru"))
                .withZone(ZoneId.systemDefault())
                .format(instant)
                .replaceFirstChar { it.uppercase() }
        }.getOrDefault(yearMonth)
    }
}
