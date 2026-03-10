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
    @SerialName("last_seen") val lastSeen: String? = null,
    // Populated when querying user_presence_view — server computes this with NOW()
    // so it's immune to client clock skew. Defaults to false for raw table rows (Realtime).
    @SerialName("is_truly_online") val serverComputedOnline: Boolean = false
)

/**
 * True if the user is currently online.
 *
 * Priority:
 *  1. serverComputedOnline — set by the DB view using server NOW(). Always accurate.
 *  2. Client-side fallback — used for Realtime events (raw table, no view column).
 *     May have minor clock-skew error but is close enough for push notifications.
 */
fun UserPresence.isTrulyOnline(): Boolean {
    if (serverComputedOnline) return true
    if (!online) return false
    val ts = lastSeen ?: return false
    return runCatching {
        val ageSeconds = java.time.Instant.now().epochSecond -
            java.time.Instant.parse(ts).epochSecond
        ageSeconds < 15
    }.getOrDefault(false)
}
