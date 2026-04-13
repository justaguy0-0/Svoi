package com.example.svoi.data.local

sealed class ChatWallpaper {
    /** No wallpaper — default chat background */
    object None : ChatWallpaper()

    /** One of the 8 preset images bundled with the app (id = 1..8) */
    data class Preset(val id: Int) : ChatWallpaper()

    /** User-picked photo copied to internal storage */
    data class Custom(val path: String) : ChatWallpaper()
}
