package com.example.svoi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.svoi.SvoiApp
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.components.EmojiPicker
import com.example.svoi.ui.components.MainBottomBar
import com.example.svoi.ui.components.OfflineBanner
import com.example.svoi.ui.theme.AvatarColors
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes
import com.example.svoi.util.toRegistrationDate
import kotlinx.coroutines.launch

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

    // Use app-scoped StateFlows for the banner — their values are already accurate
    // before this screen is first composed (no initial=true race condition).
    val app = LocalContext.current.applicationContext as SvoiApp
    val bannerOnline by app.isOnline.collectAsState()
    val bannerReachable by app.supabaseChecker.isReachable.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var statusText by remember(profile) { mutableStateOf(profile?.statusText ?: "") }
    var selectedEmoji by remember(profile) { mutableStateOf(profile?.emoji ?: "😊") }
    var selectedColor by remember(profile) { mutableStateOf(profile?.bgColor ?: "#5C6BC0") }

    var showAvatarSheet by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val avatarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            MainBottomBar(
                selectedTab = 1,
                onChatsClick = onNavigateToChats,
                onProfileClick = {},
                onSettingsClick = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner(isOnline = bannerOnline, isReachable = bannerReachable, isUpdating = false)

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SvoiDimens.ScreenHorizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar preview
            Avatar(
                emoji = selectedEmoji,
                bgColor = selectedColor,
                size = SvoiDimens.AvatarLarge,
                fontSize = 42.sp
            )

            // Registration date chip
            val regDate = profile?.createdAt?.toRegistrationDate()
            if (!regDate.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Surface(
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

            Spacer(Modifier.height(20.dp))

            // Name & about card
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SvoiShapes.Card,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(SvoiDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Имя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = SvoiShapes.TextField
                    )

                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        label = { Text("О себе") },
                        placeholder = { Text("Напишите что-нибудь о себе") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        shape = SvoiShapes.TextField
                    )

                    Button(
                        onClick = { viewModel.saveNameAndAbout(displayName, statusText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SvoiDimens.ButtonHeight),
                        enabled = !isSaving && isOnline,
                        shape = SvoiShapes.Button
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isOnline) "Сохранить" else "Нет подключения")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions card
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SvoiShapes.Card,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(SvoiDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAvatarSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SvoiDimens.ButtonHeight),
                        shape = SvoiShapes.Button
                    ) {
                        Text("Редактировать аватар")
                    }

                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SvoiDimens.ButtonHeight),
                        shape = SvoiShapes.Button
                    ) {
                        Text("Изменить пароль")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
        } // else
        } // outer Column
    }

    // ── Avatar bottom sheet ──────────────────────────────────────────────────
    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            sheetState = avatarSheetState,
            windowInsets = WindowInsets(0)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Редактировать аватар", style = MaterialTheme.typography.titleLarge)

                // Live preview inside sheet
                Avatar(
                    emoji = selectedEmoji,
                    bgColor = selectedColor,
                    size = 80.dp,
                    fontSize = 38.sp
                )

                Text(
                    "Эмодзи",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                EmojiPicker(
                    onEmojiSelected = { selectedEmoji = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Цвет",
                    style = MaterialTheme.typography.titleSmall,
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
                                        .size(40.dp)
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
                        viewModel.saveAvatar(selectedEmoji, selectedColor)
                        scope.launch { avatarSheetState.hide() }.invokeOnCompletion {
                            showAvatarSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SvoiDimens.ButtonHeight),
                    enabled = !isSaving,
                    shape = SvoiShapes.Button
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Применить")
                    }
                }
            }
        }
    }

    // ── Change password dialog ───────────────────────────────────────────────
    if (showPasswordDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Изменить пароль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        shape = SvoiShapes.TextField
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
                        shape = SvoiShapes.TextField
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changePassword("", newPassword, confirmPassword)
                        showPasswordDialog = false
                    },
                    enabled = newPassword.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ── Logout dialog ────────────────────────────────────────────────────────
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
