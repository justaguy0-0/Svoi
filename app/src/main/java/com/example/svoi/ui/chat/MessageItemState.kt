package com.example.svoi.ui.chat

import androidx.compose.runtime.Immutable
import com.example.svoi.data.model.MessageUiItem

@Immutable
data class MessageItemState(
    val item: MessageUiItem,
    val isGroup: Boolean,
    val isHighlighted: Boolean,
    val isSelected: Boolean = false,
    val isSelectionMode: Boolean = false,
    val uploadProgresses: List<Float> = emptyList(),
    val activeVideoUrl: String? = null,
    val isMuted: Boolean = true,
    val videoAspectRatio: Float = 16f / 9f,
    val imageRatio: Float? = null,
    val voicePlayState: VoicePlayState? = null,
    val cachedVoiceIds: Set<String> = emptySet(),
    val ogData: OgData? = null,
)
