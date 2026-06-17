package com.example.svoi.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.KeyStoreException
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException

class EncryptedPrefsManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? = createPrefsWithRecovery(appContext)

    fun saveSession(accessToken: String, refreshToken: String, expiresAt: Long) {
        prefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH_TOKEN, null)
    fun getExpiresAt(): Long = prefs?.getLong(KEY_EXPIRES_AT, 0L) ?: 0L

    fun clearSession() {
        prefs?.edit()?.apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
            apply()
        }
    }

    fun hasSession(): Boolean = getAccessToken() != null

    /**
     * Extracts the user UUID from the stored JWT access token without any network call.
     * JWT payload is base64url-encoded and contains "sub" = user UUID.
     * Works fully offline — useful as a fallback when the Supabase SDK hasn't loaded
     * the session into memory yet (e.g. when a VPN blocks the refresh endpoint).
     */
    fun getUserIdFromStoredToken(): String? {
        val token = getAccessToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            var payload = parts[1]
            payload += "=".repeat((4 - payload.length % 4) % 4)
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE)
            val json = String(decoded, Charsets.UTF_8)
            Regex(""""sub"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "EncryptedPrefs"
        private const val PREFS_NAME = "svoi_secure_prefs"
        private const val ANDROIDX_SECURITY_PREFIX = "__androidx_security_crypto_"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        private fun createPrefsWithRecovery(context: Context): SharedPreferences? {
            return try {
                createEncryptedPrefs(context)
            } catch (e: Exception) {
                if (!e.isRecoverableEncryptedPrefsFailure()) throw e
                Log.w(TAG, "EncryptedPrefs: corrupted encrypted prefs, clearing and recreating")
                clearEncryptedPrefsStorage(context)
                try {
                    createEncryptedPrefs(context)
                } catch (second: Exception) {
                    Log.e(TAG, "EncryptedPrefs: failed to recreate encrypted prefs; starting logged out")
                    null
                }
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun clearEncryptedPrefsStorage(context: Context) {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
            runCatching { deleteSharedPrefsFile(context, PREFS_NAME) }
            runCatching {
                sharedPrefsDir(context)
                    ?.listFiles { file ->
                        file.name.startsWith(ANDROIDX_SECURITY_PREFIX) ||
                            file.name.startsWith("$ANDROIDX_SECURITY_PREFIX$PREFS_NAME")
                    }
                    ?.forEach { file -> file.delete() }
            }
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    if (containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                        deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    }
                }
            }
        }

        private fun deleteSharedPrefsFile(context: Context, name: String) {
            sharedPrefsDir(context)?.let { dir ->
                File(dir, "$name.xml").delete()
                File(dir, "$name.xml.bak").delete()
            }
        }

        private fun sharedPrefsDir(context: Context): File? {
            return File(context.applicationInfo.dataDir, "shared_prefs")
                .takeIf { it.exists() && it.isDirectory }
        }

        private fun Throwable.isRecoverableEncryptedPrefsFailure(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                when (current) {
                    is AEADBadTagException,
                    is GeneralSecurityException,
                    is KeyPermanentlyInvalidatedException,
                    is KeyStoreException,
                    is IOException -> return true
                }
                current = current.cause
            }
            return false
        }
    }
}
