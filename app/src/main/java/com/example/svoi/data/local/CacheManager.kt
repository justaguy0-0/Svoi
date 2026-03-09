package com.example.svoi.data.local

import android.content.Context
import android.util.Log
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.Profile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CacheManager(context: Context) {

    private val dir = File(context.filesDir, "cache").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    // ── Chat list ─────────────────────────────────────────────────────────────

    fun saveChatList(chats: List<ChatListItem>) = save("chats.json", json.encodeToString(chats))

    fun loadChatList(): List<ChatListItem>? = load("chats.json") {
        json.decodeFromString<List<ChatListItem>>(it)
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun saveMessages(chatId: String, messages: List<Message>) {
        // keep only the latest 100 per chat
        val trimmed = if (messages.size > 100) messages.takeLast(100) else messages
        save("msg_$chatId.json", json.encodeToString(trimmed))
    }

    fun loadMessages(chatId: String): List<Message>? = load("msg_$chatId.json") {
        json.decodeFromString<List<Message>>(it)
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    fun saveProfiles(profiles: Collection<Profile>) {
        val existing = loadProfileMap().toMutableMap()
        profiles.forEach { existing[it.id] = it }
        save("profiles.json", json.encodeToString(existing.values.toList()))
    }

    fun loadProfileMap(): Map<String, Profile> {
        val list = load("profiles.json") { json.decodeFromString<List<Profile>>(it) }
        return list?.associateBy { it.id } ?: emptyMap()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun save(name: String, content: String) {
        try { File(dir, name).writeText(content) } catch (e: Exception) {
            Log.w("Cache", "save $name failed: ${e.message}")
        }
    }

    private fun <T> load(name: String, decode: (String) -> T): T? {
        val file = File(dir, name)
        if (!file.exists()) return null
        return try { decode(file.readText()) } catch (e: Exception) {
            Log.w("Cache", "load $name failed: ${e.message}")
            null
        }
    }
}
