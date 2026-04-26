package com.example.svoi.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class SvoiAccent {
    BLUE, ORANGE, RED, GREEN, PINK, PURPLE
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

    fun isChatMuted(chatId: String): Boolean = prefs.getBoolean("chat_muted_$chatId", false)

    fun setChatMuted(chatId: String, muted: Boolean) {
        prefs.edit().putBoolean("chat_muted_$chatId", muted).apply()
    }

    fun isProxyEnabled(): Boolean = prefs.getBoolean("proxy_enabled", true)

    fun setProxyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("proxy_enabled", enabled).apply()
    }
}
