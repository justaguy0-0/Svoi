package com.example.svoi.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("svoi_wallpaper", Context.MODE_PRIVATE)

    private val _wallpaper = MutableStateFlow(load())
    val wallpaper: StateFlow<ChatWallpaper> = _wallpaper.asStateFlow()

    private val _dim = MutableStateFlow(prefs.getFloat("dim", 0f))
    val dim: StateFlow<Float> = _dim.asStateFlow()

    fun setDim(value: Float) {
        val clamped = value.coerceIn(0f, 0.75f)
        prefs.edit().putFloat("dim", clamped).apply()
        _dim.value = clamped
    }

    fun setNone() {
        prefs.edit().putString("type", "none").apply()
        _wallpaper.value = ChatWallpaper.None
    }

    fun setPreset(id: Int) {
        prefs.edit().putString("type", "preset").putInt("preset_id", id).apply()
        _wallpaper.value = ChatWallpaper.Preset(id)
    }

    /** Copies the image at [uri] into internal storage and activates it.
     *  Returns true on success. Must be called from a coroutine. */
    suspend fun setCustom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "wallpapers").also { it.mkdirs() }
            val dest = File(dir, "custom_wallpaper.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.edit()
                .putString("type", "custom")
                .putString("custom_path", dest.absolutePath)
                .apply()
            _wallpaper.value = ChatWallpaper.Custom(dest.absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun load(): ChatWallpaper = when (prefs.getString("type", "none")) {
        "preset" -> ChatWallpaper.Preset(prefs.getInt("preset_id", 1))
        "custom" -> {
            val path = prefs.getString("custom_path", null)
            if (path != null && File(path).exists()) ChatWallpaper.Custom(path)
            else ChatWallpaper.None
        }
        else -> ChatWallpaper.None
    }
}
