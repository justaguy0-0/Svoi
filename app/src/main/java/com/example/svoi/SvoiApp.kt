package com.example.svoi

import android.app.Application
import com.example.svoi.data.NetworkMonitor
import com.example.svoi.data.local.CacheManager
import com.example.svoi.data.local.EncryptedPrefsManager
import com.example.svoi.data.local.ThemeManager
import com.example.svoi.data.repository.AuthRepository
import com.example.svoi.data.repository.ChatRepository
import com.example.svoi.data.repository.MessageRepository
import com.example.svoi.data.repository.UserRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SvoiApp : Application() {

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

    // Presence: heartbeat while in foreground + explicit offline on background
    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var goOfflineJob: Job? = null

    /**
     * Start heartbeat (fires immediately, then every 5s).
     * Also cancels any pending "go offline" signal — handles the case where
     * the user backgrounds and immediately foregrounds the app.
     */
    fun startPresenceHeartbeat() {
        goOfflineJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatJob = heartbeatScope.launch {
            while (true) {
                if (authRepository.isLoggedIn()) {
                    userRepository.setOnline(true)
                }
                delay(5_000L)
            }
        }
    }

    /**
     * Stop heartbeat and schedule explicit setOnline(false) after a short debounce.
     * The debounce window (800ms) allows onResume to cancel the offline signal
     * if the user just quickly switched apps and is coming right back.
     * If the app crashes, this never runs — TTL in the DB view handles it.
     */
    fun stopPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (authRepository.isLoggedIn()) {
            goOfflineJob?.cancel()
            goOfflineJob = heartbeatScope.launch {
                delay(800L)
                runCatching { userRepository.setOnline(false) }
            }
        }
    }
}
