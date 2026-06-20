package com.example.svoi

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.svoi.data.local.AppTextSizePreset
import com.example.svoi.data.local.SvoiAccent
import com.example.svoi.data.local.ThemeMode
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.svoi.data.repository.AuthRepository
import com.example.svoi.navigation.NavGraph
import com.example.svoi.navigation.Routes
import com.example.svoi.ui.theme.SvoiTheme
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val app get() = application as SvoiApp

    // chat_id из уведомления — обновляется при onCreate и onNewIntent
    private val pendingChatId = mutableStateOf<String?>(null)
    private val pendingPasswordReset = mutableStateOf(false)
    private val passwordResetRecoveryReady = mutableStateOf(false)

    // Set to true after restoreSession() completes in LaunchedEffect.
    // Guards onResume from calling tryRestoreSessionSilently() concurrently with
    // restoreSession() — two simultaneous importSession() calls break the SDK session.
    @Volatile private var initialRestoreCompleted = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingChatId.value = intent.getStringExtra("chat_id")
        handleAuthDeeplink(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var themeMode by remember { mutableStateOf(app.themeManager.getThemeMode()) }
            var autoPlayVideos by remember { mutableStateOf(app.themeManager.getAutoPlayVideos()) }
            var accent by remember { mutableStateOf(app.themeManager.getAccent()) }
            var textSizePreset by remember { mutableStateOf(app.themeManager.getTextSizePreset()) }

            SvoiTheme(
                themeMode = themeMode,
                accent = accent,
                textSizePreset = textSizePreset
            ) {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val restored = app.authRepository.restoreSession()
                    initialRestoreCompleted = true
                    startDestination = when {
                        pendingPasswordReset.value -> Routes.RESET_PASSWORD
                        restored -> Routes.CHAT_LIST
                        else -> Routes.LOGIN
                    }
                    // setOnline is handled by startPresenceHeartbeat() in onResume
                    if (restored) launch {
                        delay(1_000L)
                        app.registerFcmToken()
                    }
                    // Проверка обновления — один раз за запуск, в фоне
                    launch {
                        delay(1_000L)
                        if (app.awaitSupabaseReachable()) {
                            val update = app.appUpdateRepository.checkForUpdate()
                            app.setUpdateAvailable(update)
                        }
                    }
                }

                // Навигация в чат по уведомлению.
                // Ждём пока NavHost полностью инициализируется (currentBackStackEntry != null),
                // только после этого навигируем — иначе граф ещё не задан и будет краш.
                val chatId by pendingChatId
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentBackStackEntry?.destination?.route, chatId) {
                    val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
                    val targetChatId = chatId ?: return@LaunchedEffect
                    // Already in the exact target chat — no navigation needed (Realtime handles updates)
                    val currentChatId = currentBackStackEntry?.arguments?.getString("chatId")
                    val alreadyThere = currentRoute == Routes.CHAT && currentChatId == targetChatId
                    if (!alreadyThere) {
                        navController.navigate(Routes.chat(targetChatId))
                    }
                    pendingChatId.value = null
                }

                val resetRequested by pendingPasswordReset
                LaunchedEffect(currentBackStackEntry?.destination?.route, resetRequested) {
                    val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
                    if (resetRequested && currentRoute != Routes.RESET_PASSWORD) {
                        navController.navigate(Routes.RESET_PASSWORD) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                startDestination?.let { start ->
                    NavGraph(
                        navController = navController,
                        startDestination = start,
                        passwordResetRecoveryReady = passwordResetRecoveryReady.value,
                        onPasswordResetConsumed = {
                            pendingPasswordReset.value = false
                            passwordResetRecoveryReady.value = false
                        },
                        onThemeChanged = { mode ->
                            app.themeManager.setThemeMode(mode)
                            themeMode = mode
                        },
                        currentThemeMode = themeMode,
                        autoPlayVideos = autoPlayVideos,
                        onAutoPlayChanged = { enabled ->
                            app.themeManager.setAutoPlayVideos(enabled)
                            autoPlayVideos = enabled
                        },
                        currentAccent = accent,
                        onAccentChanged = { newAccent ->
                            app.themeManager.setAccent(newAccent)
                            accent = newAccent
                        },
                        currentTextSizePreset = textSizePreset,
                        onTextSizeChanged = { preset ->
                            app.themeManager.setTextSizePreset(preset)
                            textSizePreset = preset
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatId.value = intent.getStringExtra("chat_id")
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(intent: Intent) {
        val data = intent.data ?: return
        val path = data.path.orEmpty()
        val isAuthLink = data.scheme == AuthRepository.PASSWORD_RESET_SCHEME &&
            data.host == AuthRepository.PASSWORD_RESET_HOST
        if (!isAuthLink) return

        val isResetPasswordLink = path == AuthRepository.PASSWORD_RESET_PATH
        val isEmailChangeLink = path == AuthRepository.EMAIL_CHANGE_PATH
        if (!isResetPasswordLink && !isEmailChangeLink) return

        if (isResetPasswordLink) {
            Log.d("PasswordReset", "deeplink received path=$path")
            pendingPasswordReset.value = true
            passwordResetRecoveryReady.value = false
        } else {
            Log.d("EmailChange", "deeplink received")
        }
        try {
            app.supabase.handleDeeplinks(intent) { session ->
                app.prefs.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt.epochSeconds
                )
                if (isResetPasswordLink) {
                    runOnUiThread {
                        passwordResetRecoveryReady.value = true
                    }
                } else {
                    lifecycleScope.launch {
                        app.authRepository.refreshUserAfterEmailChange()
                    }
                }
            }
        } catch (e: Exception) {
            if (isResetPasswordLink) {
                Log.w("PasswordReset", "invalid recovery link: ${e.message}")
            } else {
                Log.w("EmailChange", "request failed")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.updateAppForeground(true, "activity.onResume")
        // If the SDK session was lost after the initial restore (e.g. blocked internet cleared),
        // silently re-import now that connectivity may have restored.
        // Guard: only after initialRestoreCompleted to avoid concurrent importSession() calls
        // with restoreSession() — two simultaneous imports corrupt the SDK session state.
        if (initialRestoreCompleted && !app.authRepository.isLoggedIn() && app.prefs.hasSession()) {
            lifecycleScope.launch {
                if (app.authRepository.tryRestoreSessionSilently()) {
                    app.updateAppForeground(true, "activity.sessionRestored")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // runBlocking on the main thread is the ONLY reliable way to guarantee
        // the network request completes before the process can be suspended.
        // Android lifecycle guarantees onStop() always completes before onResume() runs,
        // so there is no race condition between setOnline(false) and setOnline(true).
        if (app.updateAppForeground(false, "activity.onStop")) {
            runBlocking {
                app.setOfflinePresenceForBackground()
            }
        }
    }
}
