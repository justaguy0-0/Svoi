package com.example.svoi.data.local

import android.content.Context
import android.util.Log
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.ReactionGroup
import com.example.svoi.ui.chat.OgData
import kotlinx.serialization.Serializable
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

    // ── Chat info (name, isGroup, memberCount) ────────────────────────────────

    @Serializable
    data class CachedChatInfo(
        val chatId: String,
        val name: String,
        val isGroup: Boolean,
        val memberCount: Int,
        val otherUserId: String? = null
    )

    fun saveChatInfo(info: CachedChatInfo) = save("chat_${info.chatId}.json", json.encodeToString(info))

    fun loadChatInfo(chatId: String): CachedChatInfo? = load("chat_$chatId.json") {
        json.decodeFromString<CachedChatInfo>(it)
    }

    // ── Pinned message ────────────────────────────────────────────────────────
    //   Wrapper so we can distinguish "never cached" (null return) from
    //   "cached but no pinned message" (CachedPinned(null)).

    @Serializable
    data class CachedPinned(
        val pinnedMessage: PinnedMessage? = null,
        val messageContent: Message? = null
    )

    fun savePinnedContent(chatId: String, pinned: PinnedMessage?, content: Message?) =
        save("pinned_$chatId.json", json.encodeToString(CachedPinned(pinned, content)))

    fun loadPinnedContent(chatId: String): CachedPinned? = load("pinned_$chatId.json") {
        json.decodeFromString<CachedPinned>(it)
    }

    // ── Own profile ───────────────────────────────────────────────────────────

    fun saveOwnProfile(profile: Profile) = save("own_profile.json", json.encodeToString(profile))

    fun loadOwnProfile(): Profile? = load("own_profile.json") {
        json.decodeFromString<Profile>(it)
    }

    // ── Reactions (per chat) ──────────────────────────────────────────────────

    fun saveReactions(chatId: String, reactions: Map<String, List<ReactionGroup>>) {
        if (reactions.values.all { it.isEmpty() }) return
        save("reactions_$chatId.json", json.encodeToString(reactions))
    }

    fun loadReactions(chatId: String): Map<String, List<ReactionGroup>>? = load("reactions_$chatId.json") {
        json.decodeFromString<Map<String, List<ReactionGroup>>>(it)
    }

    // ── OG preview cache (global, keyed by URL) ───────────────────────────────

    fun saveOgData(data: Map<String, OgData>) {
        if (data.isEmpty()) return
        // Cap at 500 entries to prevent unbounded growth
        val trimmed = if (data.size > 500) data.entries.toList().takeLast(500).associate { it.toPair() } else data
        save("og_cache.json", json.encodeToString(trimmed))
    }

    fun loadOgData(): Map<String, OgData>? = load("og_cache.json") {
        json.decodeFromString<Map<String, OgData>>(it)
    }

    // ── Voice listen state (per chat) ─────────────────────────────────────────

    @Serializable
    data class CachedVoiceListens(
        val myListened: Set<String> = emptySet(),
        val otherListened: Set<String> = emptySet()
    )

    fun saveVoiceListens(chatId: String, myListened: Set<String>, otherListened: Set<String>) {
        if (myListened.isEmpty() && otherListened.isEmpty()) return
        save("voice_listens_$chatId.json", json.encodeToString(CachedVoiceListens(myListened, otherListened)))
    }

    fun loadVoiceListens(chatId: String): CachedVoiceListens? = load("voice_listens_$chatId.json") {
        json.decodeFromString<CachedVoiceListens>(it)
    }

    // ── Clear all ─────────────────────────────────────────────────────────────

    fun clearAll() {
        dir.walkBottomUp().forEach { if (it != dir) it.delete() }
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
