package com.example.svoi

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.Coil
import coil.ImageLoader
import com.example.svoi.data.ImageProgressInterceptor
import com.example.svoi.data.NoCacheInterceptor
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
import com.example.svoi.data.repository.AppAnnouncementRepository
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
import io.github.jan.supabase.realtime.realtime
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class SvoiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                updateAppForeground(true, "process.onStart")
            }

            override fun onResume(owner: LifecycleOwner) {
                updateAppForeground(true, "process.onResume")
            }

            override fun onStop(owner: LifecycleOwner) {
                if (updateAppForeground(false, "process.onStop")) {
                    heartbeatScope.launch { setOfflinePresenceForBackground() }
                }
            }
        })
        EmojiCompat.init(BundledEmojiCompatConfig(this))
        // Coil: increased timeout + download progress interceptor for large media files.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(NoCacheInterceptor())
                        .addInterceptor(RetryInterceptor(
                            maxRetries = 2,
                            shouldSkipRetry = { supabaseChecker.isStartupNetworkUnstable() }
                        ))
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
                } else if (!isCurrentlyOnline && isAppInForeground.value) {
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
                if (isAppInForeground.value) {
                    supabaseChecker.checkNow()
                }
            }
        }
        heartbeatScope.launch {
            supabase.realtime.status.collect { status ->
                if (status == Realtime.Status.CONNECTED) {
                    supabaseChecker.markRealtimeConnected()
                }
            }
        }
    }

    val supabase by lazy {
        createSupabaseClient(supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage) {
                resumable {
                    defaultChunkSize = 6L * 1024 * 1024
                }
            }
        }
    }

    val prefs by lazy { EncryptedPrefsManager(this) }
    val cacheManager by lazy { CacheManager(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }
    val supabaseChecker by lazy {
        SupabaseReachabilityChecker(this, "${BuildConfig.SUPABASE_URL}/rest/v1/", BuildConfig.SUPABASE_ANON_KEY)
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
    val appAnnouncementRepository by lazy { AppAnnouncementRepository(supabase) }
    val globalVoicePlayer by lazy {
        GlobalVoicePlayer(
            context = this,
            cacheDir = java.io.File(filesDir, "cache"),
            initialSpeed = themeManager.getVoicePlaybackSpeed(),
            onSpeedChanged = { speed -> themeManager.setVoicePlaybackSpeed(speed) }
        )
    }

    // Результат проверки обновления — null пока не проверено / нет обновления
    private val _updateAvailable = MutableStateFlow<AppVersion?>(null)
    val updateAvailable: StateFlow<AppVersion?> = _updateAvailable

    // App-scoped network state — updated from heartbeatScope so the value is already
    // correct before any screen is composed. Use this (instead of a per-ViewModel stateIn)
    // wherever the initial value must be accurate on first composition.
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground

    fun setUpdateAvailable(version: AppVersion?) {
        _updateAvailable.value = version
    }

    // Heartbeat: keeps online=true while app is in foreground.
    // Fires immediately on start, then every 8s to stay within the current 10s DB TTL.
    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var fcmRetryJob: Job? = null
    private var lastOnlinePresenceAtMs: Long = 0L

    fun updateAppForeground(foreground: Boolean, source: String): Boolean {
        if (_isAppInForeground.value == foreground) return false
        _isAppInForeground.value = foreground
        supabaseChecker.setAppInForeground(foreground)
        Log.d("AppLifecycle", "foreground=$foreground source=$source")
        if (foreground) {
            startPresenceHeartbeat()
            heartbeatScope.launch {
                supabaseChecker.checkNow(force = true)
            }
            Log.d("Realtime", "resumed because app foreground")
        } else {
            stopPresenceHeartbeat()
            Log.d("Realtime", "paused because app background")
        }
        return true
    }

    suspend fun setOfflinePresenceForBackground() {
        if (!authRepository.isLoggedIn()) return
        try {
            withTimeout(1_500) { userRepository.setOnline(false) }
        } catch (_: Exception) {
        }
    }

    fun startPresenceHeartbeat() {
        if (!isAppInForeground.value) return
        heartbeatJob?.cancel()
        heartbeatJob = heartbeatScope.launch {
            while (true) {
                if (isAppInForeground.value && authRepository.isLoggedIn() && supabaseChecker.isReachable.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastOnlinePresenceAtMs >= PRESENCE_ONLINE_MIN_UPDATE_MS) {
                        val updated = userRepository.setOnline(true)
                        if (updated) lastOnlinePresenceAtMs = System.currentTimeMillis()
                    }
                }
                delay(PRESENCE_HEARTBEAT_MS)
            }
        }
    }

    fun stopPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private companion object {
        const val PRESENCE_HEARTBEAT_MS = 8_000L
        const val PRESENCE_ONLINE_MIN_UPDATE_MS = 7_000L
    }

    suspend fun registerFcmToken() {
        val userId = authRepository.currentUserId() ?: return
        if (!supabaseChecker.isReachable.value) {
            scheduleFcmTokenRetry()
            return
        }
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val saved = pushTokenRepository.saveToken(userId, token)
            if (!saved) {
                scheduleFcmTokenRetry()
            }
        } catch (e: Exception) {
            Log.e("FCM", "registerFcmToken: ${e.message}")
            scheduleFcmTokenRetry()
        }
    }

    suspend fun awaitSupabaseReachable(timeoutMs: Long = 120_000L): Boolean {
        if (supabaseChecker.isReachable.value) return true
        return withTimeoutOrNull(timeoutMs) {
            supabaseChecker.isReachable.first { it }
        } == true
    }

    private fun scheduleFcmTokenRetry() {
        if (fcmRetryJob?.isActive == true) return
        fcmRetryJob = heartbeatScope.launch {
            while (authRepository.currentUserId() != null) {
                val becameReachable = awaitSupabaseReachable()
                val userId = authRepository.currentUserId() ?: return@launch
                if (!becameReachable) continue
                Log.d("FCM", "Retrying FCM token registration after reachability restored")
                val saved = runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    pushTokenRepository.saveToken(userId, token)
                }.onFailure { e ->
                    Log.w("FCM", "retry save token failed: ${e.message}")
                }.getOrDefault(false)
                if (saved) return@launch
                delay(10_000L)
            }
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
