package com.example.svoi.data.repository

import com.example.svoi.data.local.EncryptedPrefsManager
import com.example.svoi.data.model.InviteKey
import com.example.svoi.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Instant

class AuthRepository(
    private val supabase: SupabaseClient,
    private val prefs: EncryptedPrefsManager
) {

    /** Returns true if a saved session was successfully restored */
    suspend fun restoreSession(): Boolean {
        val accessToken = prefs.getAccessToken() ?: return false
        val refreshToken = prefs.getRefreshToken() ?: return false
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
            true
        } catch (e: Exception) {
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

    fun isLoggedIn(): Boolean = supabase.auth.currentSessionOrNull() != null
}
