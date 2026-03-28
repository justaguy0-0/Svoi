package com.example.svoi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.Profile
import com.example.svoi.ui.theme.SvoiShapes
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
    var lastVoiceState by remember { mutableStateOf(voiceState) }
    if (voiceState != null) lastVoiceState = voiceState

    Column {
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

        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBarIconItem(
                    selected = selectedTab == 0,
                    onClick = onChatsClick,
                    icon = Icons.Default.Chat,
                    contentDescription = "Чаты"
                )

                NavBarProfileItem(
                    selected = selectedTab == 1,
                    onClick = onProfileClick,
                    profile = currentProfile
                )

                NavBarIconItem(
                    selected = selectedTab == 2,
                    onClick = onSettingsClick,
                    icon = Icons.Default.Settings,
                    contentDescription = "Настройки"
                )
            }
        }
    }
}

@Composable
private fun NavBarIconItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else Color.Transparent,
        animationSpec = tween(220),
        label = "navBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navIcon"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 44.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillWidth"
    )

    Box(
        modifier = Modifier
            .height(36.dp)
            .size(width = pillWidth, height = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavBarProfileItem(
    selected: Boolean,
    onClick: () -> Unit,
    profile: Profile?
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else Color.Transparent,
        animationSpec = tween(220),
        label = "profileBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else Color.Transparent,
        animationSpec = tween(220),
        label = "profileBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "profileIconColor"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .then(
                if (selected && profile != null)
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (profile != null) {
            Avatar(
                emoji = profile.emoji,
                bgColor = profile.bgColor,
                letter = profile.displayName,
                size = if (selected) 32.dp else 28.dp,
                fontSize = 13.sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Профиль",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
