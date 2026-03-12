package com.example.svoi.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

class PushTokenRepository(private val supabase: SupabaseClient) {

    @Serializable
    private data class PushToken(val user_id: String, val token: String)

    suspend fun saveToken(userId: String, token: String) {
        try {
            supabase.from("push_tokens").upsert(PushToken(user_id = userId, token = token))
        } catch (e: Exception) {
            Log.e("PushToken", "saveToken failed: ${e.message}")
        }
    }

    suspend fun deleteToken(userId: String, token: String) {
        try {
            supabase.from("push_tokens").delete {
                filter {
                    eq("user_id", userId)
                    eq("token", token)
                }
            }
        } catch (e: Exception) {
            Log.e("PushToken", "deleteToken failed: ${e.message}")
        }
    }
}
