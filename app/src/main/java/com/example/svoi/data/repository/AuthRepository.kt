package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.local.EncryptedPrefsManager
import com.example.svoi.data.model.InviteKey
import com.example.svoi.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

class AuthRepository(
    private val supabase: SupabaseClient,
    private val prefs: EncryptedPrefsManager
) {

    /** Returns true if a saved session was successfully restored */
    suspend fun restoreSession(): Boolean {
        // Wait for Supabase SDK to finish loading its own session from storage
        val status = supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }

        if (status is SessionStatus.Authenticated) {
            Log.d("Auth", "restoreSession: SDK already authenticated, userId=${supabase.auth.currentUserOrNull()?.id}")
            return true
        }

        // SDK is not authenticated — fall back to our EncryptedPrefsManager
        val accessToken = prefs.getAccessToken() ?: run {
            Log.w("Auth", "restoreSession: no access token saved")
            return false
        }
        val refreshToken = prefs.getRefreshToken() ?: run {
            Log.w("Auth", "restoreSession: no refresh token saved")
            return false
        }
        return try {
            supabase.auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    tokenType = "bearer",
                    expiresIn = 3600,
                    expiresAt = Instant.fromEpochSeconds(prefs.getExpiresAt()),
                    refreshToken = refreshToken
                )
            )
            // Wait for the session to be fully processed after import
            val newStatus = supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            val success = newStatus is SessionStatus.Authenticated
            Log.d("Auth", "restoreSession: importSession result=$success, userId=${supabase.auth.currentUserOrNull()?.id}")
            if (!success) prefs.clearSession()
            success
        } catch (e: Exception) {
            Log.e("Auth", "restoreSession: FAILED — ${e.message}", e)
            prefs.clearSession()
            false
        }
    }

    /** Validates an invite key. Returns true if key exists and is unused. */
    suspend fun validateInviteKey(key: String): Boolean {
        return try {
            val results = supabase.from("invite_keys")
                .select {
                    filter {
                        eq("key", key)
                        eq("used", false)
                    }
                }
                .decodeList<InviteKey>()
            results.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** Registers a new user using an invite key. Returns error message or null on success. */
    suspend fun signUpWithInviteKey(
        inviteKey: String,
        email: String,
        password: String,
        displayName: String,
        emoji: String,
        bgColor: String
    ): String? {
        return try {
            // 1. Create auth user
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val session = supabase.auth.currentSessionOrNull()
                ?: return "Ошибка создания сессии"
            val userId = supabase.auth.currentUserOrNull()?.id
                ?: return "Ошибка получения пользователя"

            // 2. Update profile
            supabase.from("profiles").update({
                set("display_name", displayName)
                set("emoji", emoji)
                set("bg_color", bgColor)
            }) {
                filter { eq("id", userId) }
            }

            // 3. Mark invite key as used
            supabase.from("invite_keys").update({
                set("used", true)
                set("used_by", userId)
            }) {
                filter {
                    eq("key", inviteKey)
                }
            }

            // 4. Save session
            prefs.saveSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAt = session.expiresAt.epochSeconds
            )

            null // success
        } catch (e: Exception) {
            e.message ?: "Неизвестная ошибка"
        }
    }

    /** Signs in an existing user. Returns error message or null on success. */
    suspend fun signIn(email: String, password: String): String? {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val session = supabase.auth.currentSessionOrNull()
                ?: return "Ошибка получения сессии"
            prefs.saveSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAt = session.expiresAt.epochSeconds
            )
            null
        } catch (e: Exception) {
            "Неверный email или пароль"
        }
    }

    suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } finally {
            prefs.clearSession()
        }
    }

    suspend fun changePassword(newPassword: String): String? {
        return try {
            supabase.auth.updateUser { password = newPassword }
            null
        } catch (e: Exception) {
            e.message ?: "Ошибка смены пароля"
        }
    }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    fun isLoggedIn(): Boolean = supabase.auth.currentUserOrNull() != null
}
