package com.example.svoi.data.repository

import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun setOnline(online: Boolean) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        try {
            supabase.from("user_presence").upsert(
                UserPresence(userId = userId, online = online)
            )
        } catch (_: Exception) {}
    }

    suspend fun getPresence(userId: String): UserPresence? {
        return try {
            supabase.from("user_presence")
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<UserPresence>()
        } catch (e: Exception) {
            null
        }
    }

    fun presenceFlow(userId: String): Flow<UserPresence> {
        val channel = supabase.channel("presence-$userId")
        val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "user_presence"
            filter = "user_id=eq.$userId"
        }
        channel.subscribe()
        return flow.map { it.decodeRecord<UserPresence>() }
    }
}
