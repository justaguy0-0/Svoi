package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: String = "",
    val type: String = "personal", // "personal" | "group"
    val name: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatMember(
    @SerialName("chat_id") val chatId: String = "",
    @SerialName("user_id") val userId: String = "",
    val role: String = "member", // "member" | "admin"
    val muted: Boolean = false,
    @SerialName("joined_at") val joinedAt: String? = null
)

/** UI model combining chat + last message + unread + other user info */
data class ChatListItem(
    val chatId: String,
    val type: String,
    val displayName: String,      // group name OR other user's name
    val emoji: String,            // personal: other user emoji; group: first letter
    val bgColor: String,
    val isGroup: Boolean,
    val lastMessageText: String,
    val lastMessageTime: String,
    val unreadCount: Int,
    val otherUserId: String? = null,
    val myRole: String = "member"
)
