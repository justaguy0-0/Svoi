package com.example.svoi.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class SvoiAccent {
    BLUE, ORANGE, RED, GREEN, PINK, PURPLE
}

enum class AppTextSizePreset(
    val key: String,
    val title: String,
    val description: String,
    val scale: Float
) {
    COMPACT(
        key = "compact",
        title = "Компактный",
        description = "Больше текста на экране",
        scale = 0.92f
    ),
    NORMAL(
        key = "normal",
        title = "Обычный",
        description = "Стандартный размер",
        scale = 1.00f
    ),
    COMFORTABLE(
        key = "comfortable",
        title = "Комфортный",
        description = "Чуть крупнее обычного",
        scale = 1.08f
    ),
    LARGE(
        key = "large",
        title = "Крупный",
        description = "Для лучшей читаемости",
        scale = 1.16f
    ),
    EXTRA_LARGE(
        key = "extra_large",
        title = "Очень крупный",
        description = "Максимальный размер",
        scale = 1.25f
    );

    companion object {
        fun fromKey(key: String?): AppTextSizePreset =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("svoi_settings", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val value = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return ThemeMode.valueOf(value)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getAutoPlayVideos(): Boolean = prefs.getBoolean("auto_play_videos", true)

    fun setAutoPlayVideos(enabled: Boolean) {
        prefs.edit().putBoolean("auto_play_videos", enabled).apply()
    }

    fun isNotificationsMuted(): Boolean = prefs.getBoolean("notifications_muted", false)

    fun setNotificationsMuted(muted: Boolean) {
        prefs.edit().putBoolean("notifications_muted", muted).apply()
    }

    fun getAccent(): SvoiAccent {
        val value = prefs.getString("accent_color", SvoiAccent.BLUE.name) ?: SvoiAccent.BLUE.name
        return runCatching { SvoiAccent.valueOf(value) }.getOrDefault(SvoiAccent.BLUE)
    }

    fun setAccent(accent: SvoiAccent) {
        prefs.edit().putString("accent_color", accent.name).apply()
    }

    fun getTextSizePreset(): AppTextSizePreset {
        val value = prefs.getString("text_size_preset", AppTextSizePreset.NORMAL.key)
        return AppTextSizePreset.fromKey(value)
    }

    fun setTextSizePreset(preset: AppTextSizePreset) {
        prefs.edit().putString("text_size_preset", preset.key).apply()
    }

    fun getVoicePlaybackSpeed(): VoicePlaybackSpeed {
        val value = prefs.getString("voice_playback_speed", VoicePlaybackSpeed.NORMAL.key)
        return VoicePlaybackSpeed.fromKey(value)
    }

    fun setVoicePlaybackSpeed(speed: VoicePlaybackSpeed) {
        prefs.edit().putString("voice_playback_speed", speed.key).apply()
    }

    fun isChatMuted(chatId: String): Boolean = prefs.getBoolean("chat_muted_$chatId", false)

    fun setChatMuted(chatId: String, muted: Boolean) {
        prefs.edit().putBoolean("chat_muted_$chatId", muted).apply()
    }

}
