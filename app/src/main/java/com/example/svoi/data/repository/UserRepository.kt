package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class PresenceUpdate(
    @SerialName("user_id") val userId: String,
    val online: Boolean,
    @SerialName("last_seen") val lastSeen: String? = null
)

class UserRepository(private val supabase: SupabaseClient) {

    suspend fun getProfile(userId: String): Profile? {
        return try {
            supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCurrentProfile(): Profile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return getProfile(userId)
    }

    suspend fun searchUsers(query: String): List<Profile> {
        return try {
            supabase.from("profiles")
                .select {
                    filter {
                        ilike("display_name", "%$query%")
                    }
                    limit(20)
                }
                .decodeList<Profile>()
                .filter { it.id != supabase.auth.currentUserOrNull()?.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateProfile(
        displayName: String,
        statusText: String,
        emoji: String,
        bgColor: String
    ): String? {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return "Не авторизован"
            supabase.from("profiles").update({
                set("display_name", displayName)
                set("status_text", statusText)
                set("emoji", emoji)
                set("bg_color", bgColor)
            }) {
                filter { eq("id", userId) }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    suspend fun getProfiles(userIds: List<String>): List<Profile> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            supabase.from("profiles")
                .select { filter { isIn("id", userIds) } }
                .decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPresences(userIds: List<String>): List<UserPresence> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            supabase.from("user_presence")
                .select { filter { isIn("user_id", userIds) } }
                .decodeList<UserPresence>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setOnline(online: Boolean) {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w("Presence", "setOnline($online): no authenticated user")
            return
        }
        Log.d("Presence", "setOnline($online) userId=$userId")
        try {
            val data = PresenceUpdate(
                userId = userId,
                online = online,
                lastSeen = if (!online) java.time.Instant.now().toString() else null
            )
            supabase.from("user_presence").upsert(data)
            Log.d("Presence", "setOnline($online) SUCCESS")
        } catch (e: Exception) {
            Log.e("Presence", "setOnline($online) FAILED: ${e.message}", e)
        }
    }

    suspend fun getPresence(userId: String): UserPresence? {
        return try {
            val result = supabase.from("user_presence")
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<UserPresence>()
            Log.d("Presence", "getPresence($userId) = $result")
            result
        } catch (e: Exception) {
            Log.e("Presence", "getPresence($userId) FAILED: ${e.message}")
            null
        }
    }
}
