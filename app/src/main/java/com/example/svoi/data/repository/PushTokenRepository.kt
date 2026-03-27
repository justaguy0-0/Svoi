package com.example.svoi.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

class PushTokenRepository(private val supabase: SupabaseClient) {

    @Serializable
    private data class PushToken(val user_id: String, val token: String)

    @Serializable
    private data class SavePushTokenParams(val p_token: String)

    suspend fun saveToken(userId: String, token: String) {
        try {
            supabase.postgrest.rpc("save_push_token", SavePushTokenParams(p_token = token))
            Log.d("PushToken", "saveToken OK for userId=$userId")
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
