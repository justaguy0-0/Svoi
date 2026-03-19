package com.example.svoi.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OutboxMessage(
    val localId: String,
    val chatId: String,
    val content: String,
    val replyToId: String? = null,
    val silent: Boolean = false,
    val createdAt: String,
    val senderId: String
)

/**
 * Persists unsent text messages across app restarts.
 * All access is from the main thread via viewModelScope coroutines — no locking needed.
 */
class OutboxManager(context: Context) {

    private val prefs = context.getSharedPreferences("svoi_outbox", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = "outbox_messages"

    // In-memory cache, loaded once at startup
    private var cache: List<OutboxMessage> = loadAll()

    fun add(msg: OutboxMessage) {
        cache = cache + msg
        persist()
    }

    fun remove(localId: String) {
        cache = cache.filter { it.localId != localId }
        persist()
    }

    fun getForChat(chatId: String): List<OutboxMessage> =
        cache.filter { it.chatId == chatId }

    fun hasForChat(chatId: String): Boolean =
        cache.any { it.chatId == chatId }

    private fun loadAll(): List<OutboxMessage> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<OutboxMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY, json.encodeToString(cache)).apply()
    }
}
