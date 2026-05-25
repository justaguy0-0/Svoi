package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppAnnouncement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "normal",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("show_as_modal") val showAsModal: Boolean = false,
    val priority: Int = 0,
    @SerialName("min_version_code") val minVersionCode: Int? = null,
    @SerialName("max_version_code") val maxVersionCode: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

enum class AppAnnouncementType(val label: String) {
    IMPORTANT("Важное"),
    NORMAL("Обычное"),
    TECHNICAL("Техническое");

    companion object {
        fun from(rawType: String?): AppAnnouncementType {
            return when (rawType?.lowercase()) {
                "important", "critical" -> IMPORTANT
                "technical", "warning" -> TECHNICAL
                "normal", "info" -> NORMAL
                else -> NORMAL
            }
        }
    }
}

val AppAnnouncement.normalizedType: AppAnnouncementType
    get() = AppAnnouncementType.from(type)

val AppAnnouncement.typeLabel: String
    get() = normalizedType.label

@Serializable
data class AppAnnouncementRead(
    @SerialName("announcement_id") val announcementId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("read_at") val readAt: String? = null
)
