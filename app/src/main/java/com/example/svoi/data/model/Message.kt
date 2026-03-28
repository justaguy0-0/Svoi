package com.example.svoi.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String = "",
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("sender_id") val senderId: String? = null,
    val content: String? = null,
    val type: String = "text", // "text" | "photo" | "file" | "system" | "album" | "video" | "voice"
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("forwarded_from_id") val forwardedFromId: String? = null,
    @SerialName("forwarded_from_user_id") val forwardedFromUserId: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_for_all") val deletedForAll: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val duration: Int? = null, // voice message duration in seconds
    val silent: Boolean = false,
    @SerialName("mentioned_user_ids") val mentionedUserIds: List<String> = emptyList()
)

@Serializable
data class MessageRead(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class VoiceListen(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = ""
)

@Serializable
data class MessageReaction(
    val id: String = "",
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = "",
    val emoji: String = ""
)

/** Aggregated reaction for display: emoji + count + whether current user reacted */
@Immutable
data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val hasMyReaction: Boolean
)

@Serializable
data class PinnedMessage(
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("message_id") val messageId: String = "",
    @SerialName("pinned_by") val pinnedBy: String? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null
)

@Serializable
data class TypingStatus(
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String = "typing"
)

/** UI model combining message + sender profile + read status */
@Immutable
data class MessageUiItem(
    val message: Message,
    val senderProfile: Profile?,
    val isOwn: Boolean,
    val isRead: Boolean,
    /**
     * Stable key for LazyColumn. Normally equals [message.id], but for pending messages that are
     * later replaced by a confirmed message the key stays the same (the original localId).
     * This prevents LazyColumn from treating the replacement as remove+insert and re-animating.
     */
    val stableKey: String = message.id,
    /** For own voice messages: true when the recipient has listened.
     *  For received voice messages: true when the current user has listened. */
    val isListened: Boolean = false,
    val replyToMessage: Message? = null,
    val replyToSenderProfile: Profile? = null,
    val forwardedFromProfile: Profile? = null,
    /** True while the message is being sent (network request in flight) */
    val isPending: Boolean = false,
    /** True when the send attempt failed — message is saved in OutboxManager for retry */
    val isFailed: Boolean = false,
    /** Content-URI strings of locally-staged images (used while isPending=true) */
    val pendingLocalUris: List<String> = emptyList(),
    /** Aggregated emoji reactions on this message */
    val reactions: List<ReactionGroup> = emptyList()
)
