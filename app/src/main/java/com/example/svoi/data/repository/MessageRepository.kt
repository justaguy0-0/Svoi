package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageRead
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.TypingStatus
import com.example.svoi.data.model.MessageReaction
import com.example.svoi.data.model.VoiceListen
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Serializable
private data class MessageReadInsert(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
private data class TextMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    val type: String,
    @SerialName("reply_to_id") val replyToId: String? = null,
    val silent: Boolean = false,
    @SerialName("mentioned_user_ids") val mentionedUserIds: List<String> = emptyList(),
)

@Serializable
private data class PhotoMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val type: String,
    @SerialName("file_url") val fileUrl: String,
    val content: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    val silent: Boolean = false,
)

@Serializable
private data class AlbumMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val type: String,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    val content: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    val silent: Boolean = false,
)

@Serializable
private data class VideoMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val type: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    val content: String? = null,
    val silent: Boolean = false,
)

@Serializable
private data class FileMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val type: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
)

@Serializable
private data class ForwardMessageInsert(
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String? = null,
    val type: String,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    @SerialName("forwarded_from_id") val forwardedFromId: String,
    @SerialName("forwarded_from_user_id") val forwardedFromUserId: String? = null
)

class MessageRepository(private val supabase: SupabaseClient) {

    private fun currentUserId() = supabase.auth.currentUserOrNull()?.id ?: ""

    suspend fun getMessages(chatId: String, limit: Int = 50, offset: Int = 0, historyFrom: String? = null): List<Message> {
        return try {
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                        eq("deleted_for_all", false)
                        if (historyFrom != null) gte("created_at", historyFrom)
                    }
                    order("created_at", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<Message>()
                .reversed()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getMessage(messageId: String): Message? {
        return try {
            supabase.from("messages")
                .select { filter { eq("id", messageId) } }
                .decodeSingleOrNull<Message>()
        } catch (e: Exception) { null }
    }

    /**
     * [senderId] must be provided explicitly by the caller (e.g. from ChatViewModel.currentUserId)
     * so that RLS condition `auth.uid() = sender_id` is satisfied even during the brief window
     * when the Supabase SDK has loaded the JWT token but currentUserOrNull() still returns null.
     */
    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        content: String,
        replyToId: String? = null,
        silent: Boolean = false,
        mentionedUserIds: List<String> = emptyList()
    ): Boolean {
        return try {
            supabase.from("messages").insert(
                TextMessageInsert(
                    chatId = chatId, senderId = senderId, content = content,
                    type = "text", replyToId = replyToId, silent = silent,
                    mentionedUserIds = mentionedUserIds
                )
            )
            true
        } catch (_: Exception) { false }
    }

    suspend fun sendPhotoMessage(chatId: String, fileUrl: String, replyToId: String? = null, content: String? = null, silent: Boolean = false) {
        val userId = currentUserId()
        try {
            supabase.from("messages").insert(
                PhotoMessageInsert(
                    chatId = chatId, senderId = userId, type = "photo",
                    fileUrl = fileUrl, content = content,
                    replyToId = replyToId, silent = silent
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun sendFileMessage(
        chatId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String? = null,
        replyToId: String? = null
    ) {
        val userId = currentUserId()
        try {
            supabase.from("messages").insert(
                FileMessageInsert(
                    chatId = chatId, senderId = userId, type = "file",
                    fileUrl = fileUrl, fileName = fileName, fileSize = fileSize,
                    mimeType = mimeType, replyToId = replyToId
                )
            )
        } catch (e: Exception) {
            Log.e("FileMsg", "sendFileMessage FAILED: ${e.message}", e)
        }
    }

    suspend fun sendVideoMessage(
        chatId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        replyToId: String? = null,
        content: String? = null,
        silent: Boolean = false
    ) {
        val userId = currentUserId()
        try {
            supabase.from("messages").insert(
                VideoMessageInsert(
                    chatId = chatId, senderId = userId, type = "video",
                    fileUrl = fileUrl, fileName = fileName, fileSize = fileSize,
                    mimeType = mimeType, replyToId = replyToId, content = content,
                    silent = silent
                )
            )
        } catch (e: Exception) {
            Log.e("FileMsg", "sendVideoMessage FAILED: ${e.message}", e)
        }
    }

    suspend fun sendSystemMessage(chatId: String, content: String, replyToId: String? = null): Message? {
        val userId = currentUserId()
        return try {
            val result = supabase.from("messages").insert(
                buildMap {
                    put("chat_id", chatId)
                    put("sender_id", userId)
                    put("content", content)
                    put("type", "system")
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ).decodeSingle<Message>()
            android.util.Log.d("SystemMsg", "sendSystemMessage OK: $content")
            result
        } catch (e: Exception) {
            android.util.Log.e("SystemMsg", "sendSystemMessage FAILED: $content", e)
            null
        }
    }

    @Serializable
    private data class VoiceListenInsert(
        @SerialName("message_id") val messageId: String,
        @SerialName("user_id") val userId: String
    )

    @Serializable
    private data class VoiceMessageInsert(
        @SerialName("chat_id") val chatId: String,
        @SerialName("sender_id") val senderId: String,
        val type: String,
        @SerialName("file_url") val fileUrl: String,
        val duration: Int,
        @SerialName("reply_to_id") val replyToId: String? = null,
        val silent: Boolean = false
    )

    suspend fun sendVoiceMessage(
        chatId: String,
        fileUrl: String,
        durationSec: Int,
        replyToId: String? = null,
        silent: Boolean = false
    ) {
        val userId = currentUserId()
        try {
            supabase.from("messages").insert(
                VoiceMessageInsert(
                    chatId = chatId,
                    senderId = userId,
                    type = "voice",
                    fileUrl = fileUrl,
                    duration = durationSec,
                    replyToId = replyToId,
                    silent = silent
                )
            )
        } catch (e: Exception) {
            Log.e("MessageRepo", "sendVoiceMessage FAILED: ${e.message}", e)
        }
    }

    /** Records that the current user has listened to a voice message. */
    suspend fun markVoiceListened(messageId: String) {
        val userId = currentUserId()
        try {
            supabase.from("voice_listens").upsert(
                VoiceListenInsert(messageId = messageId, userId = userId)
            )
        } catch (e: Exception) {
            Log.e("VoiceListen", "markVoiceListened failed: ${e.message}")
        }
    }

    /** Returns the subset of [messageIds] that the current user has listened to. */
    suspend fun getMyListenedVoiceIds(messageIds: List<String>): Set<String> {
        if (messageIds.isEmpty()) return emptySet()
        val userId = currentUserId()
        return try {
            supabase.from("voice_listens")
                .select { filter { isIn("message_id", messageIds); eq("user_id", userId) } }
                .decodeList<VoiceListen>()
                .map { it.messageId }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /** Returns the subset of [messageIds] that have been listened to by someone other than the current user. */
    suspend fun getOtherListenedVoiceIds(messageIds: List<String>): Set<String> {
        if (messageIds.isEmpty()) return emptySet()
        val userId = currentUserId()
        return try {
            supabase.from("voice_listens")
                .select { filter { isIn("message_id", messageIds); neq("user_id", userId) } }
                .decodeList<VoiceListen>()
                .map { it.messageId }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /** Realtime flow — any voice_listens insert (for sender to see when their message was listened to). */
    fun voiceListenInsertFlow(): Flow<VoiceListen> = channelFlow {
        val channel = supabase.channel("voice-listens-insert-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "voice_listens"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    // ── Reactions ──────────────────────────────────────────────────────────────

    @Serializable
    private data class ReactionInsert(
        @SerialName("message_id") val messageId: String,
        @SerialName("user_id") val userId: String,
        val emoji: String
    )

    /** Toggle a reaction: if the user already reacted with this emoji — remove it, otherwise — add. */
    suspend fun toggleReaction(messageId: String, emoji: String): Boolean {
        val userId = currentUserId()
        return try {
            val existing = supabase.from("message_reactions")
                .select { filter { eq("message_id", messageId); eq("user_id", userId); eq("emoji", emoji) } }
                .decodeList<MessageReaction>()
            if (existing.isNotEmpty()) {
                supabase.from("message_reactions").delete {
                    filter { eq("message_id", messageId); eq("user_id", userId); eq("emoji", emoji) }
                }
            } else {
                // Max 3 reactions per user per message
                val myCount = supabase.from("message_reactions")
                    .select { filter { eq("message_id", messageId); eq("user_id", userId) } }
                    .decodeList<MessageReaction>().size
                if (myCount >= 3) return false
                supabase.from("message_reactions").insert(
                    ReactionInsert(messageId = messageId, userId = userId, emoji = emoji)
                )
            }
            true
        } catch (e: Exception) {
            Log.e("Reactions", "toggleReaction failed: ${e.message}")
            false
        }
    }

    /** Get all reactions for a list of messages. */
    suspend fun getReactions(messageIds: List<String>): List<MessageReaction> {
        if (messageIds.isEmpty()) return emptyList()
        return try {
            supabase.from("message_reactions")
                .select { filter { isIn("message_id", messageIds) } }
                .decodeList<MessageReaction>()
        } catch (e: Exception) {
            Log.e("Reactions", "getReactions failed: ${e.message}")
            emptyList()
        }
    }

    /** Realtime flow for reaction inserts in this chat's messages. */
    fun reactionInsertFlow(): Flow<MessageReaction> = channelFlow {
        val channel = supabase.channel("reactions-insert-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "message_reactions"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Realtime flow for reaction deletes. */
    fun reactionDeleteFlow(): Flow<Unit> = channelFlow {
        val channel = supabase.channel("reactions-delete-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "message_reactions"
        }.onEach { trySend(Unit) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Marks all messages in [chatId] created before [beforeTimestamp] as read for [userId].
     *  Uses SECURITY DEFINER RPC so admin can insert reads on behalf of the new member. */
    suspend fun markHistoryRead(chatId: String, userId: String, beforeTimestamp: String) {
        try {
            supabase.postgrest.rpc(
                "mark_history_read",
                buildJsonObject {
                    put("p_chat_id", chatId)
                    put("p_user_id", userId)
                    put("p_before_ts", beforeTimestamp)
                }
            )
        } catch (e: Exception) {
            Log.e("MessageRepo", "markHistoryRead failed: ${e.message}")
        }
    }

    suspend fun forwardMessage(fromMessageId: String, toChatId: String): Message? {
        val userId = currentUserId()
        val original = getMessage(fromMessageId) ?: return null

        val dto = ForwardMessageInsert(
            chatId = toChatId,
            senderId = userId,
            content = original.content,
            type = original.type,
            fileUrl = original.fileUrl,
            fileName = original.fileName,
            fileSize = original.fileSize,
            mimeType = original.mimeType,
            photoUrls = original.photoUrls,
            forwardedFromId = fromMessageId,
            forwardedFromUserId = original.senderId
        )

        return try {
            supabase.from("messages").insert(dto)
            Log.d("Forward", "forwardMessage OK")
            null
        } catch (e: Exception) {
            Log.e("Forward", "forwardMessage FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun editMessage(messageId: String, newContent: String): Boolean {
        return try {
            supabase.from("messages").update({
                set("content", newContent)
                set("edited_at", java.time.Instant.now().toString())
            }) {
                filter { eq("id", messageId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteMessageForAll(messageId: String): Boolean {
        return try {
            supabase.from("messages").update({
                set("deleted_for_all", true)
            }) {
                filter { eq("id", messageId) }
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun markMessagesAsRead(chatId: String) {
        val userId = currentUserId()
        if (userId.isEmpty()) {
            Log.w("ReadReceipts", "markMessagesAsRead: userId empty, session not ready — skipping")
            return
        }
        try {
            val messages = supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                        neq("sender_id", userId)
                        eq("deleted_for_all", false)
                    }
                }
                .decodeList<Message>()

            Log.d("ReadReceipts", "markMessagesAsRead: chatId=$chatId, found ${messages.size} messages to mark, userId=$userId")
            if (messages.isEmpty()) return

            val reads = messages.map { MessageReadInsert(messageId = it.id, userId = userId) }
            supabase.from("message_reads").upsert(reads)
            Log.d("ReadReceipts", "markMessagesAsRead: upsert SUCCESS for ${reads.size} rows")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // never swallow CancellationException
        } catch (e: Exception) {
            Log.e("ReadReceipts", "markMessagesAsRead FAILED: ${e.message}", e)
        }
    }

    /** Returns set of message IDs (from the given list) that have been read by someone */
    suspend fun getReadMessageIds(messageIds: List<String>): Set<String> {
        if (messageIds.isEmpty()) return emptySet()
        return try {
            val rows = supabase.from("message_reads")
                .select {
                    filter { isIn("message_id", messageIds) }
                }
                .decodeList<MessageRead>()
            Log.d("ReadReceipts", "getReadMessageIds(${messageIds.size} ids) → ${rows.size} rows: $rows")
            rows.map { it.messageId }.toSet()
        } catch (e: Exception) {
            Log.e("ReadReceipts", "getReadMessageIds FAILED: ${e.message}", e)
            emptySet()
        }
    }

    /** Returns set of message IDs (from the given list) that have been read by a specific user */
    suspend fun getReadMessageIdsByUser(messageIds: List<String>, userId: String): Set<String> {
        if (messageIds.isEmpty()) return emptySet()
        return try {
            supabase.from("message_reads")
                .select { filter { isIn("message_id", messageIds); eq("user_id", userId) } }
                .decodeList<MessageRead>()
                .map { it.messageId }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    suspend fun getReadUserIds(messageId: String): List<String> {
        return try {
            supabase.from("message_reads")
                .select { filter { eq("message_id", messageId) } }
                .decodeList<MessageRead>()
                .map { it.userId }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun isMessageRead(messageId: String, byUserId: String): Boolean {
        return try {
            val result = supabase.from("message_reads")
                .select {
                    filter {
                        eq("message_id", messageId)
                        eq("user_id", byUserId)
                    }
                }
                .decodeList<MessageRead>()
            result.isNotEmpty()
        } catch (e: Exception) { false }
    }

    /** Realtime flow — any message_reads insert (to refresh unread badges in chat list) */
    fun messageReadInsertFlowAll(): Flow<MessageRead> = channelFlow {
        val channel = supabase.channel("message-reads-insert-all-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "message_reads"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Supabase Realtime flow — any new message (no chat filter, for chat list refresh) */
    fun messageInsertFlowAll(): Flow<Message> = channelFlow {
        val channel = supabase.channel("messages-insert-all-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Supabase Realtime flow — new messages in a chat */
    fun messageInsertFlow(chatId: String): Flow<Message> = channelFlow {
        val channel = supabase.channel("messages-insert-$chatId-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("chat_id", FilterOperator.EQ, chatId)
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Supabase Realtime flow — updated messages in a chat (edits, deletes) */
    fun messageUpdateFlow(chatId: String): Flow<Message> = channelFlow {
        val channel = supabase.channel("messages-update-$chatId-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("chat_id", FilterOperator.EQ, chatId)
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Supabase Realtime flow for message_reads (to show read receipts) */
    fun messageReadFlow(chatId: String): Flow<MessageRead> = channelFlow {
        val channel = supabase.channel("message-reads-$chatId-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "message_reads"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    // ── Typing status ──────────────────────────────────────────────────────────

    suspend fun setTyping(chatId: String, userId: String, displayName: String, status: String = "typing") {
        try {
            supabase.from("typing_status").upsert(
                TypingStatus(
                    chatId = chatId,
                    userId = userId,
                    displayName = displayName,
                    status = status,
                    updatedAt = java.time.Instant.now().toString()
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun clearTyping(chatId: String, userId: String) {
        try {
            supabase.from("typing_status").delete {
                filter { eq("chat_id", chatId); eq("user_id", userId) }
            }
        } catch (_: Exception) {}
    }

    /** Returns users currently typing in chatId, excluding self, max 5 seconds stale */
    suspend fun getTypingUsers(chatId: String, excludeUserId: String): List<TypingStatus> {
        return try {
            supabase.from("typing_status")
                .select { filter { eq("chat_id", chatId); neq("user_id", excludeUserId) } }
                .decodeList<TypingStatus>()
                .filter { status ->
                    status.updatedAt?.let { ts ->
                        runCatching {
                            val updated = java.time.Instant.parse(ts)
                            val ageSeconds = java.time.Instant.now().epochSecond - updated.epochSecond
                            ageSeconds < 5
                        }.getOrDefault(false)
                    } ?: false
                }
        } catch (_: Exception) { emptyList() }
    }

    /** Returns typing users grouped by chat_id for multiple chats at once */
    suspend fun getTypingForChats(chatIds: List<String>, excludeUserId: String): Map<String, List<TypingStatus>> {
        if (chatIds.isEmpty()) return emptyMap()
        return try {
            supabase.from("typing_status")
                .select { filter { isIn("chat_id", chatIds); neq("user_id", excludeUserId) } }
                .decodeList<TypingStatus>()
                .filter { status ->
                    status.updatedAt?.let { ts ->
                        runCatching {
                            val updated = java.time.Instant.parse(ts)
                            val ageSeconds = java.time.Instant.now().epochSecond - updated.epochSecond
                            ageSeconds < 5
                        }.getOrDefault(false)
                    } ?: false
                }
                .groupBy { it.chatId }
        } catch (_: Exception) { emptyMap() }
    }

    /** Upload a file to Supabase Storage and return the public URL.
     *  Timeout scales with file size (min 90s, +1s per 8KB, max 5min).
     *  [onProgress] is called with 1.0 on completion; use [uploadFileWithSimulatedProgress]
     *  for smooth intermediate progress on slow connections. */
    suspend fun uploadFile(
        chatId: String,
        fileName: String,
        bytes: ByteArray,
        onProgress: ((Float) -> Unit)? = null
    ): String? {
        val timeoutMs = minOf(maxOf(90_000L, bytes.size / 8L + 30_000L), 300_000L)
        return try {
            withTimeout(timeoutMs) {
                val path = "$chatId/${java.util.UUID.randomUUID()}/$fileName"
                supabase.storage.from("chat-media").upload(path, bytes)
                onProgress?.invoke(1f)
                supabase.storage.from("chat-media").publicUrl(path)
            }
        } catch (_: Exception) { null }
    }

    /** Like [uploadFile] but fires [onProgress] continuously 0→0.9 during the upload
     *  (estimated from file size at 20 KB/s), then snaps to 1.0 on completion.
     *  Gives users real visual feedback on slow connections. */
    suspend fun uploadFileWithSimulatedProgress(
        chatId: String,
        fileName: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit
    ): String? = coroutineScope {
        var done = false
        val estimatedMs = maxOf(2_000L, bytes.size / 20L)
        val timer = launch {
            val start = System.currentTimeMillis()
            while (!done) {
                val fraction = ((System.currentTimeMillis() - start).toFloat() / estimatedMs)
                    .coerceIn(0f, 0.9f)
                onProgress(fraction)
                delay(200)
            }
        }
        val url = try {
            uploadFile(chatId, fileName, bytes)
        } finally {
            done = true
            timer.cancel()
        }
        if (url != null) onProgress(1f)
        url
    }

    /** Send a message with one or more photos (and optional text caption). */
    suspend fun sendAlbumMessage(
        chatId: String,
        photoUrls: List<String>,
        content: String?,
        replyToId: String? = null,
        silent: Boolean = false
    ): Message? {
        val userId = currentUserId() ?: return null
        return try {
            supabase.from("messages").insert(
                AlbumMessageInsert(
                    chatId = chatId,
                    senderId = userId,
                    type = if (photoUrls.size == 1) "photo" else "album",
                    fileUrl = if (photoUrls.size == 1) photoUrls.first() else null,
                    photoUrls = if (photoUrls.size > 1) photoUrls else null,
                    content = content,
                    replyToId = replyToId,
                    silent = silent
                )
            )
            null // Message arrives via realtime
        } catch (e: Exception) { null }
    }
}
