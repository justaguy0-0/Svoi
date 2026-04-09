package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageSearchResult(
    @SerialName("message_id")  val messageId:  String,
    @SerialName("chat_id")     val chatId:     String,
    @SerialName("chat_type")   val chatType:   String,
    @SerialName("chat_name")   val chatName:   String,
    val content:               String?,
    @SerialName("sender_name") val senderName: String,
    @SerialName("created_at")  val createdAt:  String?,
    val emoji:                 String,
    @SerialName("bg_color")    val bgColor:    String,
    @SerialName("is_own")      val isOwn:      Boolean
)
