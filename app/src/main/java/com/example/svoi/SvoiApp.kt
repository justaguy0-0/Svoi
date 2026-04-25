package com.example.svoi

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import coil.Coil
import coil.ImageLoader
import com.example.svoi.data.ImageProgressInterceptor
import com.example.svoi.data.RetryInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.example.svoi.data.NetworkMonitor
import com.example.svoi.data.SupabaseReachabilityChecker
import com.example.svoi.data.local.CacheManager
import com.example.svoi.data.local.EncryptedPrefsManager
import com.example.svoi.data.local.DraftManager
import com.example.svoi.data.local.OutboxManager
import com.example.svoi.data.local.ThemeManager
import com.example.svoi.data.local.WallpaperManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SvoiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this))
        // Coil: увеличенный таймаут + перехватчик прогресса загрузки для медленного прокси
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(RetryInterceptor(maxRetries = 2))
                        .addNetworkInterceptor(ImageProgressInterceptor())
                        .build()
                )
                .build()
        )
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

        // Watch OS connectivity → probe Supabase reachability immediately on reconnect.
        // Also runs a periodic re-probe every 60 seconds while online.
        heartbeatScope.launch {
            var isCurrentlyOnline = false
            networkMonitor.isOnline.collect { online ->
                _isOnline.value = online
                if (!online) {
                    isCurrentlyOnline = false
                    supabaseChecker.markOffline()
                } else if (!isCurrentlyOnline) {
                    // Just came online — probe immediately
                    isCurrentlyOnline = true
                    supabaseChecker.checkNow(force = true)
                }
            }
        }
        heartbeatScope.launch {
            while (true) {
                // Probe every 10s when blocked (PROBE_COOLDOWN_MS=30s suppresses actual
                // HTTP calls when reachable, so effectively ~30s max when online).
                delay(10_000L)
                supabaseChecker.checkNow()
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
    val supabaseChecker by lazy {
        SupabaseReachabilityChecker(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
    val themeManager by lazy { ThemeManager(this) }
    val wallpaperManager by lazy { WallpaperManager(this) }
    val outboxManager by lazy { OutboxManager(this) }
    val draftManager by lazy { DraftManager(this) }

    val authRepository by lazy { AuthRepository(supabase, prefs) }
    val userRepository by lazy { UserRepository(supabase, supabaseChecker) }
    val chatRepository by lazy { ChatRepository(supabase, supabaseChecker) }
    val messageRepository by lazy { MessageRepository(supabase) }
    val pushTokenRepository by lazy { PushTokenRepository(supabase) }
    val appUpdateRepository by lazy { AppUpdateRepository(supabase) }
    val globalVoicePlayer by lazy { GlobalVoicePlayer(java.io.File(filesDir, "cache")) }

    // Результат проверки обновления — null пока не проверено / нет обновления
    private val _updateAvailable = MutableStateFlow<AppVersion?>(null)
    val updateAvailable: StateFlow<AppVersion?> = _updateAvailable

    // App-scoped network state — updated from heartbeatScope so the value is already
    // correct before any screen is composed. Use this (instead of a per-ViewModel stateIn)
    // wherever the initial value must be accurate on first composition.
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

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
                if (authRepository.isLoggedIn() && supabaseChecker.isReachable.value) {
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
            val saved = pushTokenRepository.saveToken(userId, token)
            if (!saved) {
                // Save failed (likely timeout/no connection) — retry once when reachable
                heartbeatScope.launch {
                    val became = withTimeoutOrNull(120_000L) {
                        supabaseChecker.isReachable.first { it }
                    }
                    if (became == true && authRepository.currentUserId() != null) {
                        Log.d("FCM", "Retrying FCM token registration after reachability restored")
                        pushTokenRepository.saveToken(userId, token)
                    }
                }
            }
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
