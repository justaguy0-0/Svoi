package com.example.svoi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import com.example.svoi.ui.voice.GlobalVoiceMiniPlayer

@Composable
fun MainBottomBar(
    selectedTab: Int,  // 0=Чаты, 1=Профиль, 2=Настройки
    onChatsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentProfile: Profile? = null
) {
    val app = LocalContext.current.applicationContext as SvoiApp
    val voiceState by app.globalVoicePlayer.state.collectAsState()
    val isOnline by app.networkMonitor.isOnline.collectAsState(initial = true)
    var lastVoiceState by remember { mutableStateOf(voiceState) }
    if (voiceState != null) lastVoiceState = voiceState

    Column {
        // Network offline banner — slides in from bottom when connectivity is lost
        AnimatedVisibility(
            visible = !isOnline,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) {
            NetworkOfflineBanner()
        }

        // Mini-player sits above the tab bar when voice is playing outside a chat
        AnimatedVisibility(
            visible = voiceState != null,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) {
            lastVoiceState?.let { vs ->
                GlobalVoiceMiniPlayer(
                    state = vs,
                    onPlayPause = {
                        if (vs.isPlaying) app.globalVoicePlayer.pause()
                        else app.globalVoicePlayer.resume()
                    },
                    onClose = { app.globalVoicePlayer.stop() }
                )
            }
        }

        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = onChatsClick,
                icon = { Icon(Icons.Default.Chat, contentDescription = "Чаты") },
                label = { Text("Чаты") }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = onProfileClick,
                icon = {
                    val p = currentProfile
                    if (p != null) {
                        Avatar(
                            emoji = p.emoji,
                            bgColor = p.bgColor,
                            letter = p.displayName,
                            size = 28.dp,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
                label = { Text("Профиль") }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = onSettingsClick,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                label = { Text("Настройки") }
            )
        }
    }
}

@Composable
private fun NetworkOfflineBanner() {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "Нет подключения к интернету",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
