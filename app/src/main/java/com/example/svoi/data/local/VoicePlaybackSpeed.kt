package com.example.svoi.data.local

enum class VoicePlaybackSpeed(
    val key: String,
    val value: Float,
    val label: String
) {
    HALF("half", 0.5f, "0.5x"),
    NORMAL("normal", 1.0f, "1x"),
    ONE_AND_HALF("one_and_half", 1.5f, "1.5x"),
    DOUBLE("double", 2.0f, "2x");

    companion object {
        fun fromKey(key: String?): VoicePlaybackSpeed =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
