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
        // Heartbeat fires immediately on first tick, then every 30s.
        // No need for a separate setOnline(true) call.
        if (app.authRepository.isLoggedIn()) {
            app.startPresenceHeartbeat()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop heartbeat — TTL in the DB view (90s) will mark the user offline
        // automatically once heartbeats stop. No explicit setOnline(false) needed,
        // which eliminates race conditions and the "stuck online after crash" bug.
        app.stopPresenceHeartbeat()
    }
}
