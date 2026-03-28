package com.example.svoi.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.theme.OnlineGreen
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes
import com.example.svoi.util.toRegistrationDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: UserProfileViewModel = viewModel()
) {
    LaunchedEffect(userId) { viewModel.load(userId) }

    val profile by viewModel.profile.collectAsState()
    val presence by viewModel.presence.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    // Entrance animation
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) appeared = true }
    val avatarScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = tween(350),
        label = "avatarScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(400),
        label = "contentAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SvoiDimens.ScreenHorizontalPadding)
                    .graphicsLayer { alpha = contentAlpha },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // Large avatar with online dot
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.scale(avatarScale)
                ) {
                    Avatar(
                        emoji = profile?.emoji ?: "😊",
                        bgColor = profile?.bgColor ?: "#5C6BC0",
                        letter = profile?.displayName ?: "",
                        size = SvoiDimens.AvatarXLarge,
                        fontSize = 44.sp
                    )
                    val isOnline = presence?.isTrulyOnline() == true
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(OnlineGreen, CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Name
                Text(
                    text = profile?.displayName ?: "Пользователь",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                // Online status
                val isOnline = presence?.isTrulyOnline() == true
                Text(
                    text = if (isOnline) "в сети" else "не в сети",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status text
                if (!profile?.statusText.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = profile?.statusText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Registration date chip
                val regDate = profile?.createdAt?.toRegistrationDate()
                if (!regDate.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = SvoiShapes.Chip,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "В Свои с $regDate",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Write message button
                Button(
                    onClick = {
                        scope.launch {
                            val chatId = viewModel.getOrCreateChat(userId)
                            if (chatId != null) onOpenChat(chatId)
                        }
                    },
                    shape = SvoiShapes.Button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SvoiDimens.ButtonHeight)
                ) {
                    Icon(
                        Icons.Default.ChatBubble,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Написать", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
