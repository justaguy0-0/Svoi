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
import androidx.navigation.compose.rememberNavController
import com.example.svoi.navigation.NavGraph
import com.example.svoi.navigation.Routes
import com.example.svoi.ui.theme.SvoiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    private val app get() = application as SvoiApp
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SvoiTheme {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val restored = app.authRepository.restoreSession()
                    startDestination = if (restored) Routes.CHAT_LIST else Routes.LOGIN
                    if (restored) app.userRepository.setOnline(true)
                }

                startDestination?.let { start ->
                    NavGraph(
                        navController = navController,
                        startDestination = start
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.authRepository.isLoggedIn()) {
            scope.launch { app.userRepository.setOnline(true) }
            app.startPresenceHeartbeat()
        }
    }

    override fun onPause() {
        super.onPause()
        // Only stop the heartbeat here — do NOT send setOnline(false) asynchronously.
        // An async false launched here can complete AFTER onResume's async true,
        // leaving the status stuck at false. onStop handles the actual offline write
        // synchronously via runBlocking, which always completes before onResume fires.
        app.stopPresenceHeartbeat()
    }

    override fun onStop() {
        super.onStop()
        // runBlocking guarantees this completes before the process can be killed,
        // and always completes before the next onResume can run on the main thread.
        if (app.authRepository.isLoggedIn()) {
            runBlocking {
                try {
                    withTimeout(1500) { app.userRepository.setOnline(false) }
                } catch (_: Exception) {}
            }
        }
    }
}
