package com.example.svoi

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.example.svoi.data.NetworkMonitor
import com.example.svoi.data.local.CacheManager
import com.example.svoi.data.local.EncryptedPrefsManager
import com.example.svoi.data.local.ThemeManager
import com.example.svoi.data.model.AppVersion
import com.example.svoi.data.repository.AppUpdateRepository
import com.example.svoi.data.repository.AuthRepository
import com.example.svoi.data.repository.ChatRepository
import com.example.svoi.data.repository.MessageRepository
import com.example.svoi.data.repository.PushTokenRepository
import com.example.svoi.data.repository.UserRepository
import com.example.svoi.ui.voice.GlobalVoicePlayer
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SvoiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this))
        // Persist token refreshes: the Supabase SDK auto-refreshes the access/refresh token
        // internally. Without this, prefs always stores the original login tokens. After the
        // first SDK refresh cycle, the old refresh token is invalidated (token rotation), and
        // the next importSession() call fails with "refresh_token_already_used".
        // Fix: whenever the SDK reports Authenticated, save the current session to prefs.
        heartbeatScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    supabase.auth.currentSessionOrNull()?.let { session ->
                        prefs.saveSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            expiresAt = session.expiresAt.epochSeconds
                        )
                        Log.d("Auth", "SvoiApp: session persisted to prefs (token refresh)")
                    }
                }
            }
        }
    }

    val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    val prefs by lazy { EncryptedPrefsManager(this) }
    val cacheManager by lazy { CacheManager(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }
    val themeManager by lazy { ThemeManager(this) }

    val authRepository by lazy { AuthRepository(supabase, prefs) }
    val userRepository by lazy { UserRepository(supabase) }
    val chatRepository by lazy { ChatRepository(supabase) }
    val messageRepository by lazy { MessageRepository(supabase) }
    val pushTokenRepository by lazy { PushTokenRepository(supabase) }
    val appUpdateRepository by lazy { AppUpdateRepository(supabase) }
    val globalVoicePlayer by lazy { GlobalVoicePlayer() }

    // Результат проверки обновления — null пока не проверено / нет обновления
    private val _updateAvailable = MutableStateFlow<AppVersion?>(null)
    val updateAvailable: StateFlow<AppVersion?> = _updateAvailable

    fun setUpdateAvailable(version: AppVersion?) {
        _updateAvailable.value = version
    }

    // Heartbeat: keeps online=true while app is in foreground.
    // Fires immediately on start, then every 3s.
    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    fun startPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = heartbeatScope.launch {
            while (true) {
                if (authRepository.isLoggedIn()) {
                    userRepository.setOnline(true)
                }
                delay(3_000L)
            }
        }
    }

    fun stopPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun registerFcmToken() {
        val userId = authRepository.currentUserId() ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            pushTokenRepository.saveToken(userId, token)
        } catch (e: Exception) {
            Log.e("FCM", "registerFcmToken: ${e.message}")
        }
    }

    suspend fun unregisterFcmToken() {
        val userId = authRepository.currentUserId() ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            pushTokenRepository.deleteToken(userId, token)
        } catch (e: Exception) {
            Log.e("FCM", "unregisterFcmToken: ${e.message}")
        }
    }

    /** Central logout: unregister FCM token, clear cache, then sign out. */
    suspend fun logout() {
        unregisterFcmToken() // must be before signOut — needs currentUserId()
        cacheManager.clearAll()
        authRepository.signOut()
    }
}
