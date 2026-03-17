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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant

class AuthRepository(
    private val supabase: SupabaseClient,
    private val prefs: EncryptedPrefsManager
) {

    /** Returns true if a saved session was successfully restored */
    suspend fun restoreSession(): Boolean {
        // Wait for SDK to load session from storage — with timeout.
        // Without timeout, the app hangs forever when offline + token needs refresh
        // (SDK retries indefinitely, sessionStatus stays Initializing).
        val status = withTimeoutOrNull(5_000L) {
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }
        Log.d("Auth", "restoreSession: status=${status?.let { it::class.simpleName } ?: "timeout(offline?)"}")

        if (status is SessionStatus.Authenticated) {
            if (supabase.auth.currentUserOrNull() == null) {
                Log.w("Auth", "restoreSession: session loaded but user is null, fetching...")
                try {
                    supabase.auth.retrieveUserForCurrentSession(updateSession = true)
                } catch (e: Exception) {
                    Log.w("Auth", "restoreSession: couldn't fetch user (offline?) — ${e.message}")
                    return supabase.auth.currentSessionOrNull() != null || prefs.hasSession()
                }
            }
            Log.d("Auth", "restoreSession: SDK authenticated, userId=${supabase.auth.currentUserOrNull()?.id}")
            return supabase.auth.currentUserOrNull() != null
        }

        // RefreshFailure = SDK had a session but couldn't refresh (offline/blocked).
        // Trust in-memory session if present; otherwise fall through to prefs.
        if (status is SessionStatus.RefreshFailure) {
            val hasSession = supabase.auth.currentSessionOrNull() != null
            Log.w("Auth", "restoreSession: RefreshFailure, hasSession=$hasSession")
            if (hasSession) return true
            // In-memory session was cleared by SDK — fall through to prefs fallback below
        }

        // Timeout, NotAuthenticated, or RefreshFailure with no in-memory session.
        // Fall back to locally-saved tokens.
        val accessToken = prefs.getAccessToken() ?: run {
            Log.w("Auth", "restoreSession: no saved tokens → truly logged out")
            return false
        }
        val refreshToken = prefs.getRefreshToken() ?: return false

        // Try to load the session into the SDK. If the network is blocked this will time out —
        // that does NOT mean the tokens are invalid. Never clear tokens here.
        return try {
            withTimeoutOrNull(3_000L) {
                supabase.auth.importSession(
                    UserSession(
                        accessToken = accessToken,
                        tokenType = "bearer",
                        expiresIn = 3600,
                        expiresAt = Instant.fromEpochSeconds(prefs.getExpiresAt()),
                        refreshToken = refreshToken
                    )
                )
            }
            val sessionLoaded = supabase.auth.currentSessionOrNull() != null
            Log.d("Auth", "restoreSession: importSession sessionLoaded=$sessionLoaded")
            if (!sessionLoaded) {
                // importSession timed out — network is blocked.
                // Tokens are still valid; we'll retry silently when internet restores.
                Log.w("Auth", "restoreSession: import timed out — keeping tokens, proceeding to app")
            }
            true  // we have saved tokens → stay in the app, not on login screen
        } catch (e: Exception) {
            // Network or unknown error — do NOT clear tokens, do NOT log out.
            // Tokens are considered valid until the server explicitly rejects them.
            Log.w("Auth", "restoreSession: import threw (network?) — ${e.message}, keeping tokens")
            true
        }
    }

    /**
     * Silently re-imports the session from prefs into the SDK.
     * Call after connectivity is restored (e.g. from onResume) when the SDK
     * has no active session but we have saved tokens.
     */
    suspend fun tryRestoreSessionSilently(): Boolean {
        if (supabase.auth.currentUserOrNull() != null) return true  // already live
        if (!prefs.hasSession()) return false
        return try {
            withTimeoutOrNull(5_000L) {
                supabase.auth.importSession(
                    UserSession(
                        accessToken = prefs.getAccessToken()!!,
                        tokenType = "bearer",
                        expiresIn = 3600,
                        expiresAt = Instant.fromEpochSeconds(prefs.getExpiresAt()),
                        refreshToken = prefs.getRefreshToken()!!
                    )
                )
            }
            val ok = supabase.auth.currentUserOrNull() != null
            Log.d("Auth", "tryRestoreSessionSilently: ok=$ok")
            ok
        } catch (e: Exception) {
            Log.w("Auth", "tryRestoreSessionSilently: ${e.message}")
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
        about: String = "",
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
                set("status_text", about)
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
            Log.e("Auth", "signIn failed: ${e.message}", e)
            e.message ?: "Неверный email или пароль"
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
