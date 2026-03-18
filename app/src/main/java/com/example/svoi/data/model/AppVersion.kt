package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppVersion(
    val id: Int = 0,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val changelog: String = "",
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("created_at") val createdAt: String? = null
)
