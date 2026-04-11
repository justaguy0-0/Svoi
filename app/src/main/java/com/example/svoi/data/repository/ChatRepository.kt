package com.example.svoi.data.repository

import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.ChatMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.theme.groupAvatarColor
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class ChatMemberInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("history_from") val historyFrom: String? = null
)

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

        // 1. Get chat memberships (exclude chats the user has soft-deleted via left_at)
        val memberships = try {
            supabase.from("chat_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMember>()
                .filter { it.leftAt == null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepo", "getChatsForUser: failed to load memberships", e)
            return emptyList()
        }

        Log.d("ChatRepo", "getChatsForUser: found ${memberships.size} memberships")
        if (memberships.isEmpty()) return emptyList()
        val chatIds = memberships.map { it.chatId }

        // Parallel fetch: chats, members, and messages are independent
        val (chats, allMembers, allMessages) = coroutineScope {
            val chatsDeferred = async {
                try {
                    supabase.from("chats")
                        .select { filter { isIn("id", chatIds) } }
                        .decodeList<Chat>()
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Log.e("ChatRepo", "getChatsForUser: failed to load chats", e)
                    emptyList()
                }
            }
            val membersDeferred = async {
                try {
                    supabase.from("chat_members")
                        .select { filter { isIn("chat_id", chatIds) } }
                        .decodeList<ChatMember>()
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Log.e("ChatRepo", "getChatsForUser: failed to load allMembers", e)
                    emptyList()
                }
            }
            val messagesDeferred = async {
                try {
                    supabase.from("messages")
                        .select {
                            filter { isIn("chat_id", chatIds); eq("deleted_for_all", false) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Message>()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            }
            Triple(chatsDeferred.await(), membersDeferred.await(), messagesDeferred.await())
        }

        Log.d("ChatRepo", "getChatsForUser: found ${chats.size} chats")

        val otherUserIds = allMembers
            .filter { it.userId != userId }
            .map { it.userId }
            .distinct()

        // Parallel fetch: profiles, presence, and read receipts are independent
        val lastMessages: Map<String, Message> = allMessages
            .groupBy { it.chatId }
            .mapValues { (_, msgs) -> msgs.first() }
        val allOtherMessages = allMessages.filter { it.senderId != userId }
        val allOtherIds = allOtherMessages.map { it.id }

        val myOwnLastMessageIds = lastMessages.values
            .filter { it.senderId == userId }
            .map { it.id }

        val profileMap: Map<String, Profile>
        val presenceMap: Map<String, UserPresence>
        val readMessageIds: Set<String>
        val readOwnMessageIds: Set<String>
        coroutineScope {
            val profilesDeferred = async {
                try {
                    if (otherUserIds.isEmpty()) emptyMap()
                    else supabase.from("profiles")
                        .select { filter { isIn("id", otherUserIds) } }
                        .decodeList<Profile>()
                        .associateBy { it.id }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap<String, Profile>() }
            }
            val presenceDeferred = async {
                try {
                    if (otherUserIds.isEmpty()) emptyMap()
                    else supabase.from("user_presence_view")
                        .select { filter { isIn("user_id", otherUserIds) } }
                        .decodeList<UserPresence>()
                        .associateBy { it.userId }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap<String, UserPresence>() }
            }
            val readsDeferred = async {
                if (allOtherIds.isEmpty()) emptySet()
                else try {
                    supabase.from("message_reads")
                        .select { filter { isIn("message_id", allOtherIds); eq("user_id", userId) } }
                        .decodeList<com.example.svoi.data.model.MessageRead>()
                        .map { it.messageId }.toSet()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet<String>() }
            }
            val myOwnReadsDeferred = async {
                if (myOwnLastMessageIds.isEmpty()) emptySet()
                else try {
                    supabase.from("message_reads")
                        .select { filter { isIn("message_id", myOwnLastMessageIds) } }
                        .decodeList<com.example.svoi.data.model.MessageRead>()
                        .filter { it.userId != userId }
                        .map { it.messageId }.toSet()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet<String>() }
            }
            profileMap = profilesDeferred.await()
            presenceMap = presenceDeferred.await()
            readMessageIds = readsDeferred.await()
            readOwnMessageIds = myOwnReadsDeferred.await()
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
                groupAvatarColor(chat.id)
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
                lastMsg.type == "photo" -> {
                    val caption = lastMsg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    val label = "📷 Фото$caption"
                    if (senderName != null) "$senderName: $label" else label
                }
                lastMsg.type == "album" -> {
                    val count = lastMsg.photoUrls?.size ?: 0
                    val caption = lastMsg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    val label = if (count > 1) "📷 $count фото$caption" else "📷 Фото$caption"
                    if (senderName != null) "$senderName: $label" else label
                }
                lastMsg.type == "video" -> {
                    val caption = lastMsg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    val label = "🎥 Видео$caption"
                    if (senderName != null) "$senderName: $label" else label
                }
                lastMsg.type == "file" -> if (senderName != null) "$senderName: 📎 ${lastMsg.fileName ?: "Файл"}" else "📎 ${lastMsg.fileName ?: "Файл"}"
                lastMsg.type == "voice" -> if (senderName != null) "$senderName: 🎤 Голосовое сообщение" else "🎤 Голосовое сообщение"
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
                isOtherOnline = isOtherOnline,
                lastMessageIsOwn = lastMsg?.senderId == userId,
                lastMessageIsRead = lastMsg?.id?.let { it in readOwnMessageIds } ?: false,
                isMuted = membership?.muted == true,
                lastMessageIsForwarded = lastMsg?.forwardedFromId != null
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    /** Set left_at = now() for current user in a personal chat (soft delete for the requester) */
    suspend fun markAsLeft(chatId: String): Boolean {
        val userId = currentUserId()
        return try {
            supabase.from("chat_members").update({
                set("left_at", java.time.Instant.now().toString())
            }) {
                filter {
                    eq("chat_id", chatId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("ChatRepo", "markAsLeft FAILED: chatId=$chatId", e)
            false
        }
    }

    /** Find existing personal chat between two users, or null if none exists */
    suspend fun findPersonalChat(otherUserId: String): String? {
        val userId = currentUserId()
        return try {
            // Get chats where both users are active members (leftAt == null for current user)
            val myChats = supabase.from("chat_members")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMember>()
                .filter { it.leftAt == null }
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

    @kotlinx.serialization.Serializable
    private data class PinnedMessageInsert(
        @kotlinx.serialization.SerialName("chat_id") val chatId: String,
        @kotlinx.serialization.SerialName("message_id") val messageId: String,
        @kotlinx.serialization.SerialName("pinned_by") val pinnedBy: String
    )

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId()
        return try {
            supabase.from("pinned_messages").upsert(
                PinnedMessageInsert(chatId = chatId, messageId = messageId, pinnedBy = userId)
            )
            Log.d("Pin", "pinMessage SUCCESS chatId=$chatId messageId=$messageId")
            true
        } catch (e: Exception) {
            Log.e("Pin", "pinMessage FAILED chatId=$chatId", e)
            false
        }
    }

    suspend fun unpinMessage(chatId: String): Boolean {
        return try {
            supabase.from("pinned_messages")
                .delete { filter { eq("chat_id", chatId) } }
            Log.d("Pin", "unpinMessage SUCCESS chatId=$chatId")
            true
        } catch (e: Exception) {
            Log.e("Pin", "unpinMessage FAILED chatId=$chatId", e)
            false
        }
    }

    /** Hard delete — используется только внутренне или из тестов */
    suspend fun deleteChat(chatId: String): Boolean {
        return try {
            supabase.from("chats")
                .delete { filter { eq("id", chatId) } }
            true
        } catch (e: Exception) { false }
    }

    /**
     * Soft delete через RPC: архивирует чат в deleted_chats,
     * пишет запись в audit_log, затем удаляет из chats.
     * Доступно только для admin чата.
     */
    suspend fun softDeleteChat(chatId: String): Boolean {
        return try {
            supabase.postgrest.rpc(
                "soft_delete_chat",
                buildJsonObject { put("p_chat_id", chatId) }
            )
            Log.d("ChatRepo", "softDeleteChat OK: chatId=$chatId")
            true
        } catch (e: Exception) {
            Log.e("ChatRepo", "softDeleteChat FAILED: chatId=$chatId", e)
            false
        }
    }

    suspend fun clearChatHistory(chatId: String): Boolean {
        return try {
            supabase.from("messages")
                .delete { filter { eq("chat_id", chatId) } }
            true
        } catch (e: Exception) { false }
    }

    suspend fun addMember(chatId: String, userId: String, historyFrom: String? = null): Boolean {
        return try {
            supabase.from("chat_members").insert(
                ChatMemberInsert(chatId = chatId, userId = userId, historyFrom = historyFrom)
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
