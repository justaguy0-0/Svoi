package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InviteKey(
    val id: String = "",
    val key: String = "",
    val used: Boolean = false,
    @SerialName("used_by") val usedBy: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
