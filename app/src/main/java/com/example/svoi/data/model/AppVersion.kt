package com.example.svoi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppVersion(
    val id: Int = 0,
    @SerialName("version_code") val versionCode: Int? = null,
    @SerialName("version_name") val versionName: String,
    @SerialName("is_active") val isActive: Boolean = true,
    val changelog: String = "",
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("apk_url") val apkUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val resolvedDownloadUrl: String
        get() = apkUrl?.takeIf { it.isNotBlank() } ?: downloadUrl.orEmpty()

    val resolvedReleaseNotes: String
        get() = releaseNotes?.takeIf { it.isNotBlank() } ?: changelog
}
