package com.example.svoi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.svoi.data.model.Profile

@Composable
fun MainBottomBar(
    selectedTab: Int,  // 0=Чаты, 1=Профиль, 2=Настройки
    onChatsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentProfile: Profile? = null
) {
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
