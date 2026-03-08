package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("status_text") val statusText: String = "",
    val emoji: String = "😊",
    @SerialName("bg_color") val bgColor: String = "#5C6BC0",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserPresence(
    @SerialName("user_id") val userId: String = "",
    val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null
)
