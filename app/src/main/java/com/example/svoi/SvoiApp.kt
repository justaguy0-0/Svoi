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

    // Heartbeat: keeps online=true while app is in foreground.
    // Fires immediately on start, then every 5s.
    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    fun startPresenceHeartbeat() {
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

    fun stopPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
