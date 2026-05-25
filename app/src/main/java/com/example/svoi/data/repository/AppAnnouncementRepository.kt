package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.model.AppAnnouncement
import com.example.svoi.data.model.AppAnnouncementRead
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

class AppAnnouncementRepository(private val supabase: SupabaseClient) {

    suspend fun fetchLatestActiveAnnouncements(
        versionCode: Int,
        limit: Int = 5
    ): List<AppAnnouncement> {
        val queryLimit = (limit * 4).coerceAtLeast(limit)
        return supabase.from("app_announcements")
            .select {
                filter {
                    eq("is_active", true)
                }
                order("priority", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(queryLimit.toLong())
            }
            .decodeList<AppAnnouncement>()
            .filter { it.matchesVersion(versionCode) && !it.isExpired() }
            .take(limit)
    }

    suspend fun fetchActiveAnnouncements(versionCode: Int): List<AppAnnouncement> {
        return try {
            supabase.from("app_announcements")
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("priority", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<AppAnnouncement>()
                .filter { it.matchesVersion(versionCode) && !it.isExpired() }
        } catch (e: Exception) {
            Log.w("AppAnnouncements", "fetchActiveAnnouncements failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchUnreadModalAnnouncements(
        currentUserId: String,
        versionCode: Int
    ): List<AppAnnouncement> {
        if (currentUserId.isBlank()) return emptyList()

        return try {
            val announcements = supabase.from("app_announcements")
                .select {
                    filter {
                        eq("is_active", true)
                        eq("show_as_modal", true)
                    }
                    order("priority", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<AppAnnouncement>()
                .filter { it.matchesVersion(versionCode) && !it.isExpired() }

            if (announcements.isEmpty()) return emptyList()

            val readIds = supabase.from("app_announcement_reads")
                .select {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<AppAnnouncementRead>()
                .map { it.announcementId }
                .toSet()

            announcements.filterNot { it.id in readIds }
        } catch (e: Exception) {
            Log.w("AppAnnouncements", "fetchUnreadModalAnnouncements failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun markAsRead(announcementId: String, userId: String) {
        if (announcementId.isBlank() || userId.isBlank()) return

        try {
            supabase.from("app_announcement_reads").upsert(
                AppAnnouncementReadInsert(
                    announcementId = announcementId,
                    userId = userId
                )
            ) {
                ignoreDuplicates = true
            }
        } catch (e: Exception) {
            Log.w("AppAnnouncements", "markAsRead failed: ${e.message}")
        }
    }

    private fun AppAnnouncement.matchesVersion(versionCode: Int): Boolean {
        val minOk = minVersionCode == null || minVersionCode <= versionCode
        val maxOk = maxVersionCode == null || maxVersionCode >= versionCode
        return minOk && maxOk
    }

    private fun AppAnnouncement.isExpired(): Boolean {
        val expires = expiresAt ?: return false
        return runCatching { Instant.parse(expires).isBefore(Instant.now()) }
            .getOrDefault(false)
    }

    @Serializable
    private data class AppAnnouncementReadInsert(
        @SerialName("announcement_id") val announcementId: String,
        @SerialName("user_id") val userId: String
    )
}
