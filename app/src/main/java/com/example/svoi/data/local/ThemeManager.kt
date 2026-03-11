package com.example.svoi.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { LIGHT, DARK, SYSTEM }

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
}
