package com.example.svoi

object ActiveChatTracker {
    @Volatile
    var activeChatId: String? = null
}
