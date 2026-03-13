package com.example.svoi

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.svoi.data.local.ThemeMode
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.svoi.navigation.NavGraph
import com.example.svoi.navigation.Routes
import com.example.svoi.ui.theme.SvoiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    private val app get() = application as SvoiApp

    // chat_id из уведомления — обновляется при onCreate и onNewIntent
    private val pendingChatId = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingChatId.value = intent.getStringExtra("chat_id")

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

            SvoiTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val restored = app.authRepository.restoreSession()
                    startDestination = if (restored) Routes.CHAT_LIST else Routes.LOGIN
                    // setOnline is handled by startPresenceHeartbeat() in onResume
                    if (restored) app.registerFcmToken()
                }

                // Навигация в чат по уведомлению.
                // Ждём пока NavHost полностью инициализируется (currentBackStackEntry != null),
                // только после этого навигируем — иначе граф ещё не задан и будет краш.
                val chatId by pendingChatId
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentBackStackEntry?.destination?.route, chatId) {
                    val currentRoute = currentBackStackEntry?.destination?.route
                    if (currentRoute == Routes.CHAT_LIST && chatId != null) {
                        navController.navigate(Routes.chat(chatId!!))
                        pendingChatId.value = null
                    }
                }

                startDestination?.let { start ->
                    NavGraph(
                        navController = navController,
                        startDestination = start,
                        onThemeChanged = { mode ->
                            app.themeManager.setThemeMode(mode)
                            themeMode = mode
                        },
                        currentThemeMode = themeMode,
                        autoPlayVideos = autoPlayVideos,
                        onAutoPlayChanged = { enabled ->
                            app.themeManager.setAutoPlayVideos(enabled)
                            autoPlayVideos = enabled
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
    }

    override fun onResume() {
        super.onResume()
        // Heartbeat fires immediately (no initial delay), then every 5s.
        if (app.authRepository.isLoggedIn()) {
            app.startPresenceHeartbeat()
        }
    }

    override fun onPause() {
        super.onPause()
        app.stopPresenceHeartbeat()
    }

    override fun onStop() {
        super.onStop()
        // runBlocking on the main thread is the ONLY reliable way to guarantee
        // the network request completes before the process can be suspended.
        // Android lifecycle guarantees onStop() always completes before onResume() runs,
        // so there is no race condition between setOnline(false) and setOnline(true).
        if (app.authRepository.isLoggedIn()) {
            runBlocking {
                try {
                    withTimeout(1_500) { app.userRepository.setOnline(false) }
                } catch (_: Exception) {}
            }
        }
    }
}
