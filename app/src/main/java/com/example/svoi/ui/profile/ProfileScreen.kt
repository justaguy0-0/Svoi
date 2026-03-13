package com.example.svoi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.components.EmojiPicker
import com.example.svoi.ui.components.MainBottomBar
import com.example.svoi.ui.theme.AvatarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToChats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var statusText by remember(profile) { mutableStateOf(profile?.statusText ?: "") }
    var selectedEmoji by remember(profile) { mutableStateOf(profile?.emoji ?: "😊") }
    var selectedColor by remember(profile) { mutableStateOf(profile?.bgColor ?: "#5C6BC0") }

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Выйти",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            MainBottomBar(
                selectedTab = 1,
                onChatsClick = onNavigateToChats,
                onProfileClick = {},
                onSettingsClick = onNavigateToSettings,
                currentProfile = profile
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar preview
            Avatar(
                emoji = selectedEmoji,
                bgColor = selectedColor,
                size = 88.dp,
                fontSize = 42.sp
            )

            // Name & status
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it },
                label = { Text("О себе") },
                placeholder = { Text("Напишите что-нибудь о себе") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                shape = MaterialTheme.shapes.medium
            )

            // Emoji picker (same keyboard as in chat)
            Text(
                "Эмодзи",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            EmojiPicker(
                onEmojiSelected = { selectedEmoji = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Color picker — 2 symmetric rows (chunked by 6)
            Text(
                "Цвет",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AvatarColors.chunked(6).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { hex ->
                            val color = runCatching {
                                Color(android.graphics.Color.parseColor(hex))
                            }.getOrDefault(Color.Gray)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (hex == selectedColor)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveProfile(displayName, statusText, selectedEmoji, selectedColor)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSaving && isOnline,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Сохранить")
                        if (!isOnline) {
                            Text(
                                "Нет подключения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Change password section
            Text(
                "Сменить пароль",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Новый пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Повторите пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = MaterialTheme.shapes.medium
            )

            Button(
                onClick = {
                    viewModel.changePassword("", newPassword, confirmPassword)
                    newPassword = ""
                    confirmPassword = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = newPassword.isNotBlank() && !isSaving,
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Изменить пароль")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOut(onLogout)
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }
}
