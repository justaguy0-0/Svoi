package com.example.svoi.data.repository

import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.ChatMember
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class ChatRepository(private val supabase: SupabaseClient) {

    private fun currentUserId() = supabase.auth.currentUserOrNull()?.id ?: ""

    /** Returns all chats for the current user, enriched with last message + unread count */
    suspend fun getChatsForUser(): List<ChatListItem> {
        val userId = currentUserId()

        // 1. Get chat memberships
        val memberships = try {
            supabase.from("chat_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMember>()
        } catch (e: Exception) { return emptyList() }

        if (memberships.isEmpty()) return emptyList()
        val chatIds = memberships.map { it.chatId }

        // 2. Get chat details
        val chats = try {
            supabase.from("chats")
                .select { filter { isIn("id", chatIds) } }
                .decodeList<Chat>()
        } catch (e: Exception) { return emptyList() }

        // 3. Get all chat members for personal chats (to find the other user)
        val allMembers = try {
            supabase.from("chat_members")
                .select { filter { isIn("chat_id", chatIds) } }
                .decodeList<ChatMember>()
        } catch (e: Exception) { emptyList() }

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
        } catch (e: Exception) { emptyList() }
        val profileMap = profiles.associateBy { it.id }

        // 5. Get last message for each chat
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

        // 6. Get unread counts
        val unreadCounts = mutableMapOf<String, Int>()
        for (chatId in chatIds) {
            try {
                val count = supabase.from("messages")
                    .select {
                        filter {
                            eq("chat_id", chatId)
                            neq("sender_id", userId)
                            eq("deleted_for_all", false)
                        }
                    }
                    .decodeList<Message>()
                    .count { msg ->
                        // Simple in-memory unread check - ideally would join with message_reads
                        true // TODO: join with message_reads for accurate count
                    }
                // A simpler approach: count messages where ID not in message_reads for this user
                // For now use a basic count of non-own messages as approximation
                unreadCounts[chatId] = 0 // Will be refined
            } catch (_: Exception) {}
        }

        val membershipMap = memberships.associateBy { it.chatId }

        // 7. Assemble ChatListItems
        return chats.map { chat ->
            val lastMsg = lastMessages[chat.id]
            val membership = membershipMap[chat.id]
            val chatMembers = allMembers.filter { it.chatId == chat.id }
            val otherMember = chatMembers.firstOrNull { it.userId != userId }
            val otherProfile = otherMember?.userId?.let { profileMap[it] }

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

            val lastMessageText = when {
                lastMsg == null -> ""
                lastMsg.deletedForAll -> "Сообщение удалено"
                lastMsg.type == "photo" -> "📷 Фото"
                lastMsg.type == "file" -> "📎 ${lastMsg.fileName ?: "Файл"}"
                else -> lastMsg.content ?: ""
            }

            ChatListItem(
                chatId = chat.id,
                type = chat.type,
                displayName = displayName,
                emoji = emoji,
                bgColor = bgColor,
                isGroup = chat.type == "group",
                lastMessageText = lastMessageText,
                lastMessageTime = lastMsg?.createdAt ?: chat.createdAt,
                unreadCount = 0, // TODO: implement proper unread counting
                otherUserId = otherMember?.userId,
                myRole = membership?.role ?: "member"
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
        return try {
            val chat = supabase.from("chats")
                .insert(mapOf("type" to "personal", "created_by" to userId))
                .decodeSingle<Chat>()

            supabase.from("chat_members").insert(
                listOf(
                    ChatMember(chatId = chat.id, userId = userId, role = "admin"),
                    ChatMember(chatId = chat.id, userId = otherUserId, role = "member")
                )
            )
            chat.id
        } catch (e: Exception) { null }
    }

    /** Creates a group chat */
    suspend fun createGroupChat(name: String, memberIds: List<String>): String? {
        val userId = currentUserId()
        return try {
            val chat = supabase.from("chats")
                .insert(mapOf("type" to "group", "name" to name, "created_by" to userId))
                .decodeSingle<Chat>()

            val members = (memberIds + userId).distinct().map { memberId ->
                ChatMember(
                    chatId = chat.id,
                    userId = memberId,
                    role = if (memberId == userId) "admin" else "member"
                )
            }
            supabase.from("chat_members").insert(members)
            chat.id
        } catch (e: Exception) { null }
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
