package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageRead
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Serializable
private data class MessageReadInsert(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String
)

class MessageRepository(private val supabase: SupabaseClient) {

    private fun currentUserId() = supabase.auth.currentUserOrNull()?.id ?: ""

    suspend fun getMessages(chatId: String, limit: Int = 50, offset: Int = 0): List<Message> {
        return try {
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                        eq("deleted_for_all", false)
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

    suspend fun sendTextMessage(chatId: String, content: String, replyToId: String? = null): Message? {
        val userId = currentUserId()
        return try {
            supabase.from("messages").insert(
                buildMap {
                    put("chat_id", chatId)
                    put("sender_id", userId)
                    put("content", content)
                    put("type", "text")
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ).decodeSingle<Message>()
        } catch (e: Exception) { null }
    }

    suspend fun sendPhotoMessage(chatId: String, fileUrl: String, replyToId: String? = null): Message? {
        val userId = currentUserId()
        return try {
            supabase.from("messages").insert(
                buildMap {
                    put("chat_id", chatId)
                    put("sender_id", userId)
                    put("type", "photo")
                    put("file_url", fileUrl)
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ).decodeSingle<Message>()
        } catch (e: Exception) { null }
    }

    suspend fun sendFileMessage(
        chatId: String,
        fileUrl: String,
        fileName: String,
        fileSize: Long,
        replyToId: String? = null
    ): Message? {
        val userId = currentUserId()
        return try {
            supabase.from("messages").insert(
                buildMap {
                    put("chat_id", chatId)
                    put("sender_id", userId)
                    put("type", "file")
                    put("file_url", fileUrl)
                    put("file_name", fileName)
                    put("file_size", fileSize)
                    if (replyToId != null) put("reply_to_id", replyToId)
                }
            ).decodeSingle<Message>()
        } catch (e: Exception) { null }
    }

    suspend fun forwardMessage(fromMessageId: String, toChatId: String): Message? {
        val userId = currentUserId()
        val original = getMessage(fromMessageId) ?: return null
        return try {
            supabase.from("messages").insert(
                buildMap {
                    put("chat_id", toChatId)
                    put("sender_id", userId)
                    put("content", original.content)
                    put("type", original.type)
                    put("file_url", original.fileUrl)
                    put("file_name", original.fileName)
                    put("file_size", original.fileSize)
                    put("forwarded_from_id", fromMessageId)
                }
            ).decodeSingle<Message>()
        } catch (e: Exception) { null }
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

    /** Supabase Realtime flow — any new message (no chat filter, for chat list refresh) */
    fun messageInsertFlowAll(): Flow<Message> = channelFlow {
        val channel = supabase.channel("messages-insert-all")
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
        val channel = supabase.channel("messages-insert-$chatId")
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
        val channel = supabase.channel("messages-update-$chatId")
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
        val channel = supabase.channel("message-reads-$chatId")
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

    /** Upload a file/image to Supabase Storage and return the public URL */
    suspend fun uploadFile(chatId: String, fileName: String, bytes: ByteArray): String? {
        return try {
            val path = "$chatId/${java.util.UUID.randomUUID()}/$fileName"
            supabase.storage.from("chat-media").upload(path, bytes)
            supabase.storage.from("chat-media").publicUrl(path)
        } catch (e: Exception) { null }
    }
}
