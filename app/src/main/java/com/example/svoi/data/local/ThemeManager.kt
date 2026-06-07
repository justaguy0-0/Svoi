package com.example.svoi.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        title = "РљРѕРјРїР°РєС‚РЅС‹Р№",
        description = "Р‘РѕР»СЊС€Рµ С‚РµРєСЃС‚Р° РЅР° СЌРєСЂР°РЅРµ",
        scale = 0.92f
    ),
    NORMAL(
        key = "normal",
        title = "РћР±С‹С‡РЅС‹Р№",
        description = "РЎС‚Р°РЅРґР°СЂС‚РЅС‹Р№ СЂР°Р·РјРµСЂ",
        scale = 1.00f
    ),
    COMFORTABLE(
        key = "comfortable",
        title = "РљРѕРјС„РѕСЂС‚РЅС‹Р№",
        description = "Р§СѓС‚СЊ РєСЂСѓРїРЅРµРµ РѕР±С‹С‡РЅРѕРіРѕ",
        scale = 1.08f
    ),
    LARGE(
        key = "large",
        title = "РљСЂСѓРїРЅС‹Р№",
        description = "Р”Р»СЏ Р»СѓС‡С€РµР№ С‡РёС‚Р°РµРјРѕСЃС‚Рё",
        scale = 1.16f
    ),
    EXTRA_LARGE(
        key = "extra_large",
        title = "РћС‡РµРЅСЊ РєСЂСѓРїРЅС‹Р№",
        description = "РњР°РєСЃРёРјР°Р»СЊРЅС‹Р№ СЂР°Р·РјРµСЂ",
        scale = 1.25f
    );

    companion object {
        fun fromKey(key: String?): AppTextSizePreset =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

enum class ChatSwipeLeftAction(
    val key: String,
    val title: String
) {
    MARK_AS_READ(
        key = "mark_as_read",
        title = "Пометить прочитанным"
    ),
    TOGGLE_MUTE(
        key = "toggle_mute",
        title = "Выключить уведомления"
    ),
    TOGGLE_PIN(
        key = "toggle_pin",
        title = "Закрепить чат"
    );

    companion object {
        fun fromKey(key: String?): ChatSwipeLeftAction =
            entries.firstOrNull { it.key == key } ?: MARK_AS_READ
    }
}

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("svoi_settings", Context.MODE_PRIVATE)
    private val _chatSwipeLeftAction = MutableStateFlow(
        ChatSwipeLeftAction.fromKey(prefs.getString("chat_swipe_left_action", ChatSwipeLeftAction.MARK_AS_READ.key))
    )
    val chatSwipeLeftAction: StateFlow<ChatSwipeLeftAction> = _chatSwipeLeftAction

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

    fun getChatSwipeLeftAction(): ChatSwipeLeftAction = _chatSwipeLeftAction.value

    fun setChatSwipeLeftAction(action: ChatSwipeLeftAction) {
        prefs.edit().putString("chat_swipe_left_action", action.key).apply()
        _chatSwipeLeftAction.value = action
    }

    fun isChatMuted(chatId: String): Boolean = prefs.getBoolean("chat_muted_$chatId", false)

    fun setChatMuted(chatId: String, muted: Boolean) {
        prefs.edit().putBoolean("chat_muted_$chatId", muted).apply()
    }

}
