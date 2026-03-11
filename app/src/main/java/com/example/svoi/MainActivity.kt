package com.example.svoi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.svoi.data.local.ThemeMode
import androidx.navigation.compose.rememberNavController
import com.example.svoi.navigation.NavGraph
import com.example.svoi.navigation.Routes
import com.example.svoi.ui.theme.SvoiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    private val app get() = application as SvoiApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themeMode by remember { mutableStateOf(app.themeManager.getThemeMode()) }

            SvoiTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val restored = app.authRepository.restoreSession()
                    startDestination = if (restored) Routes.CHAT_LIST else Routes.LOGIN
                    // setOnline is handled by startPresenceHeartbeat() in onResume
                }

                startDestination?.let { start ->
                    NavGraph(
                        navController = navController,
                        startDestination = start,
                        onThemeChanged = { mode ->
                            app.themeManager.setThemeMode(mode)
                            themeMode = mode
                        },
                        currentThemeMode = themeMode
                    )
                }
            }
        }
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
