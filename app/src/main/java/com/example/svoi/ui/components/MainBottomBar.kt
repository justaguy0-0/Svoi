package com.example.svoi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Column {
        // Mini-player sits above the tab bar when voice is playing outside a chat
        AnimatedVisibility(
            visible = voiceState != null,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) {
            voiceState?.let { vs ->
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
