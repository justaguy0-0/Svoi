package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String = "",
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("sender_id") val senderId: String? = null,
    val content: String? = null,
    val type: String = "text", // "text" | "photo" | "file"
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("forwarded_from_id") val forwardedFromId: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_for_all") val deletedForAll: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class MessageRead(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class PinnedMessage(
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("message_id") val messageId: String = "",
    @SerialName("pinned_by") val pinnedBy: String? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null
)

/** UI model combining message + sender profile + read status */
data class MessageUiItem(
    val message: Message,
    val senderProfile: Profile?,
    val isOwn: Boolean,
    val isRead: Boolean,
    val replyToMessage: Message? = null
)
