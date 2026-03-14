package com.example.svoi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.theme.Online
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // Large avatar with online dot
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        emoji = profile?.emoji ?: "😊",
                        bgColor = profile?.bgColor ?: "#5C6BC0",
                        letter = profile?.displayName ?: "",
                        size = 96.dp,
                        fontSize = 44.sp
                    )
                    val isOnline = presence?.isTrulyOnline() == true
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Online, CircleShape)
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
                    color = if (isOnline) Online else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status text
                if (!profile?.statusText.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = profile?.statusText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Registration date
                val regDate = profile?.createdAt?.toRegistrationDate()
                if (!regDate.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "В Свои с $regDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
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
