package com.example.svoi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.svoi.SvoiApp
import com.example.svoi.ui.voice.GlobalVoiceMiniPlayer

@Composable
fun MainBottomBar(
    selectedTab: Int,  // 0=Чаты, 1=Профиль, 2=Настройки
    onChatsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val app = LocalContext.current.applicationContext as SvoiApp
    val voiceState by app.globalVoicePlayer.state.collectAsState()
    var lastVoiceState by remember { mutableStateOf(voiceState) }
    if (voiceState != null) lastVoiceState = voiceState

    Column {
        // Thin top divider
        HorizontalDivider(
            thickness = 0.4.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

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
            // Чаты
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = onChatsClick,
                icon = {
                    NavItemDot(selected = selectedTab == 0) {
                        Icon(Icons.Default.Chat, contentDescription = "Чаты")
                    }
                },
                label = null,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            // Профиль
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = onProfileClick,
                icon = {
                    NavItemDot(selected = selectedTab == 1) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
                label = null,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            // Настройки
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = onSettingsClick,
                icon = {
                    NavItemDot(selected = selectedTab == 2) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                label = null,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Wraps an icon with a small animated dot below it.
 * The dot appears (springs in) when [selected] is true.
 */
@Composable
private fun NavItemDot(selected: Boolean, icon: @Composable () -> Unit) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 5.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navDotSize"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        // Reserve space so icon doesn't shift when dot disappears
        Spacer(Modifier.height(5.dp - dotSize.coerceAtMost(5.dp)))
    }
}
