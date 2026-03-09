package com.example.svoi.data.repository

import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.ChatMember
import kotlinx.coroutines.CancellationException
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import com.example.svoi.data.model.isTrulyOnline
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class ChatRepository(private val supabase: SupabaseClient) {

    private fun currentUserId() = supabase.auth.currentUserOrNull()?.id ?: ""

    /** Returns all chats for the current user, enriched with last message + unread count */
    suspend fun getChatsForUser(): List<ChatListItem> {
        val userId = currentUserId()
        Log.d("ChatRepo", "getChatsForUser: userId='$userId'")
        if (userId.isEmpty()) {
            Log.w("ChatRepo", "getChatsForUser: userId is empty, session not ready yet")
            return emptyList()
        }

        // 1. Get chat memberships
        val memberships = try {
            supabase.from("chat_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMember>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepo", "getChatsForUser: failed to load memberships", e)
            return emptyList()
        }

        Log.d("ChatRepo", "getChatsForUser: found ${memberships.size} memberships")
        if (memberships.isEmpty()) return emptyList()
        val chatIds = memberships.map { it.chatId }

        // 2. Get chat details
        val chats = try {
            supabase.from("chats")
                .select { filter { isIn("id", chatIds) } }
                .decodeList<Chat>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepo", "getChatsForUser: failed to load chats", e)
            return emptyList()
        }

        Log.d("ChatRepo", "getChatsForUser: found ${chats.size} chats")

        // 3. Get all chat members for personal chats (to find the other user)
        val allMembers = try {
            supabase.from("chat_members")
                .select { filter { isIn("chat_id", chatIds) } }
                .decodeList<ChatMember>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepo", "getChatsForUser: failed to load allMembers", e)
            emptyList()
        }

        // 4. Get profiles of other users in personal chats
        val otherUserIds = allMembers
            .filter { it.userId != userId }
            .map { it.userId }
            .distinct()
        val profiles = try {
            if (otherUserIds.isEmpty()) emptyList()
            else supabase.from("profiles")
                .select { filter { isIn("id", otherUserIds) } }
                .decodeList<Profile>()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        val profileMap = profiles.associateBy { it.id }

        // 4b. Get online presence for other users
        val presenceMap: Map<String, UserPresence> = try {
            if (otherUserIds.isEmpty()) emptyMap()
            else supabase.from("user_presence")
                .select { filter { isIn("user_id", otherUserIds) } }
                .decodeList<UserPresence>()
                .associateBy { it.userId }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

        // 5. Get last message for each chat (one query per chat, but bounded by chat count)
        val lastMessages = mutableMapOf<String, Message>()
        for (chatId in chatIds) {
            try {
                val msg = supabase.from("messages")
                    .select {
                        filter {
                            eq("chat_id", chatId)
                            eq("deleted_for_all", false)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeSingleOrNull<Message>()
                if (msg != null) lastMessages[chatId] = msg
            } catch (_: Exception) {}
        }

        // 6. Unread counts — 2 queries total across all chats
        val allOtherMessages = try {
            supabase.from("messages")
                .select { filter { isIn("chat_id", chatIds); neq("sender_id", userId); eq("deleted_for_all", false) } }
                .decodeList<Message>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

        val allOtherIds = allOtherMessages.map { it.id }
        val readMessageIds: Set<String> = if (allOtherIds.isEmpty()) emptySet() else {
            try {
                supabase.from("message_reads")
                    .select { filter { isIn("message_id", allOtherIds); eq("user_id", userId) } }
                    .decodeList<com.example.svoi.data.model.MessageRead>()
                    .map { it.messageId }.toSet()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
        }

        val unreadCounts: Map<String, Int> = allOtherMessages
            .filter { it.id !in readMessageIds }
            .groupBy { it.chatId }
            .mapValues { (_, msgs) -> msgs.size }

        val membershipMap = memberships.associateBy { it.chatId }

        // 7. Assemble ChatListItems
        return chats.map { chat ->
            val lastMsg = lastMessages[chat.id]
            val membership = membershipMap[chat.id]
            val chatMembers = allMembers.filter { it.chatId == chat.id }
            val otherMember = chatMembers.firstOrNull { it.userId != userId }
            val otherProfile = otherMember?.userId?.let { profileMap[it] }
            val isOtherOnline = if (chat.type == "personal") {
                otherMember?.userId?.let { presenceMap[it]?.isTrulyOnline() } ?: false
            } else false

            val displayName = if (chat.type == "group") {
                chat.name ?: "Группа"
            } else {
                otherProfile?.displayName ?: "Пользователь"
            }

            val emoji = if (chat.type == "group") {
                (chat.name ?: "Г").first().toString()
            } else {
                otherProfile?.emoji ?: "😊"
            }

            val bgColor = if (chat.type == "group") {
                "#455A64"
            } else {
                otherProfile?.bgColor ?: "#5C6BC0"
            }

            val senderName: String? = if (chat.type == "group" && lastMsg != null) {
                if (lastMsg.senderId == userId) "Вы"
                else lastMsg.senderId?.let { profileMap[it]?.displayName } ?: ""
            } else null

            val lastMessageText = when {
                lastMsg == null -> ""
                lastMsg.deletedForAll -> "Сообщение удалено"
                lastMsg.type == "photo" -> if (senderName != null) "$senderName: 📷 Фото" else "📷 Фото"
                lastMsg.type == "file" -> if (senderName != null) "$senderName: 📎 ${lastMsg.fileName ?: "Файл"}" else "📎 ${lastMsg.fileName ?: "Файл"}"
                else -> {
                    val text = lastMsg.content ?: ""
                    if (senderName != null && text.isNotEmpty()) "$senderName: $text" else text
                }
            }

            ChatListItem(
                chatId = chat.id,
                type = chat.type,
                displayName = displayName,
                emoji = emoji,
                bgColor = bgColor,
                isGroup = chat.type == "group",
                lastMessageText = lastMessageText,
                lastMessageTime = lastMsg?.createdAt ?: chat.createdAt ?: "",
                unreadCount = unreadCounts[chat.id] ?: 0,
                otherUserId = otherMember?.userId,
                myRole = membership?.role ?: "member",
                isOtherOnline = isOtherOnline
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    /** Find existing personal chat between two users, or null if none exists */
    suspend fun findPersonalChat(otherUserId: String): String? {
        val userId = currentUserId()
        return try {
            // Get chats where both users are members
            val myChats = supabase.from("chat_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMember>()
                .map { it.chatId }

            val theirChats = supabase.from("chat_members")
                .select { filter { eq("user_id", otherUserId) } }
                .decodeList<ChatMember>()
                .map { it.chatId }

            val commonChatIds = myChats.intersect(theirChats.toSet())
            if (commonChatIds.isEmpty()) return null

            // Find a personal chat among common chats
            val personalChat = supabase.from("chats")
                .select {
                    filter {
                        isIn("id", commonChatIds.toList())
                        eq("type", "personal")
                    }
                }
                .decodeSingleOrNull<Chat>()
            personalChat?.id
        } catch (e: Exception) { null }
    }

    /** Creates a personal chat between current user and another user */
    suspend fun createPersonalChat(otherUserId: String): String? {
        val userId = currentUserId()
        val chatId = java.util.UUID.randomUUID().toString()
        return try {
            supabase.from("chats").insert(
                mapOf("id" to chatId, "type" to "personal", "created_by" to userId)
            )
            supabase.from("chat_members").insert(
                listOf(
                    mapOf("chat_id" to chatId, "user_id" to userId, "role" to "admin"),
                    mapOf("chat_id" to chatId, "user_id" to otherUserId, "role" to "member")
                )
            )
            chatId
        } catch (e: Exception) {
            Log.e("ChatRepo", "createPersonalChat FAILED", e)
            null
        }
    }

    /** Creates a group chat */
    suspend fun createGroupChat(name: String, memberIds: List<String>): String? {
        val userId = currentUserId()
        val chatId = java.util.UUID.randomUUID().toString()
        return try {
            supabase.from("chats").insert(
                mapOf("id" to chatId, "type" to "group", "name" to name, "created_by" to userId)
            )
            val members = (memberIds + userId).distinct().map { memberId ->
                mapOf(
                    "chat_id" to chatId,
                    "user_id" to memberId,
                    "role" to if (memberId == userId) "admin" else "member"
                )
            }
            supabase.from("chat_members").insert(members)
            chatId
        } catch (e: Exception) {
            Log.e("ChatRepo", "createGroupChat FAILED", e)
            null
        }
    }

    suspend fun getChat(chatId: String): Chat? {
        return try {
            supabase.from("chats")
                .select { filter { eq("id", chatId) } }
                .decodeSingleOrNull<Chat>()
        } catch (e: Exception) { null }
    }

    suspend fun getChatMembers(chatId: String): List<ChatMember> {
        return try {
            supabase.from("chat_members")
                .select { filter { eq("chat_id", chatId) } }
                .decodeList<ChatMember>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPinnedMessage(chatId: String): PinnedMessage? {
        return try {
            supabase.from("pinned_messages")
                .select { filter { eq("chat_id", chatId) } }
                .decodeSingleOrNull<PinnedMessage>()
        } catch (e: Exception) { null }
    }

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId()
        return try {
            supabase.from("pinned_messages").upsert(
                PinnedMessage(chatId = chatId, messageId = messageId, pinnedBy = userId)
            )
            true
        } catch (e: Exception) { false }
    }

    suspend fun unpinMessage(chatId: String): Boolean {
        return try {
            supabase.from("pinned_messages")
                .delete { filter { eq("chat_id", chatId) } }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteChat(chatId: String): Boolean {
        return try {
            supabase.from("chats")
                .delete { filter { eq("id", chatId) } }
            true
        } catch (e: Exception) { false }
    }

    suspend fun clearChatHistory(chatId: String): Boolean {
        return try {
            supabase.from("messages")
                .delete { filter { eq("chat_id", chatId) } }
            true
        } catch (e: Exception) { false }
    }

    suspend fun addMember(chatId: String, userId: String): Boolean {
        return try {
            supabase.from("chat_members").insert(
                ChatMember(chatId = chatId, userId = userId)
            )
            true
        } catch (e: Exception) { false }
    }

    suspend fun removeMember(chatId: String, userId: String): Boolean {
        return try {
            supabase.from("chat_members").delete {
                filter {
                    eq("chat_id", chatId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun setMemberRole(chatId: String, userId: String, role: String): Boolean {
        return try {
            supabase.from("chat_members").update({ set("role", role) }) {
                filter {
                    eq("chat_id", chatId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun renameGroup(chatId: String, newName: String): Boolean {
        return try {
            supabase.from("chats").update({ set("name", newName) }) {
                filter { eq("id", chatId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun setMuted(chatId: String, muted: Boolean): Boolean {
        val userId = currentUserId()
        return try {
            supabase.from("chat_members").update({ set("muted", muted) }) {
                filter {
                    eq("chat_id", chatId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (e: Exception) { false }
    }
}
