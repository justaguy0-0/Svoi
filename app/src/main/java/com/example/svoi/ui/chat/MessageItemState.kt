package com.example.svoi.ui.chat

import androidx.compose.runtime.Immutable
import com.example.svoi.data.model.MessageUiItem

@Immutable
data class MessageItemState(
    val item: MessageUiItem,
    val isGroup: Boolean,
    val videoAspectRatio: Float = 16f / 9f,
    val imageRatio: Float? = null,
    val ogData: OgData? = null,
)

@Immutable
data class MessageItemRuntimeState(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false,
    val isSelectionMode: Boolean = false,
    val uploadProgresses: List<Float> = emptyList(),
    val isActiveVideo: Boolean = false,
    val isVideoMuted: Boolean = true,
    val voicePlayState: VoicePlayState? = null,
    val isVoiceCached: Boolean = false,
)
