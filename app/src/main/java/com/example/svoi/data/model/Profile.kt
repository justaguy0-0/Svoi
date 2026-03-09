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

/**
 * True only if online=true AND last heartbeat was within 60 seconds.
 * Handles crashes/sleep: if app dies without calling setOnline(false),
 * the user will appear offline after 60s of no heartbeat.
 */
fun UserPresence.isTrulyOnline(): Boolean {
    if (!online) return false
    val ts = lastSeen ?: return false
    return runCatching {
        val ageSeconds = java.time.Instant.now().epochSecond -
            java.time.Instant.parse(ts).epochSecond
        ageSeconds < 60
    }.getOrDefault(false)
}
