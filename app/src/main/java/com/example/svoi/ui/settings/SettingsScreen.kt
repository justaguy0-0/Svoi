package com.example.svoi.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.ThemeMode
import com.example.svoi.ui.components.MainBottomBar
import com.example.svoi.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    autoPlayVideos: Boolean = true,
    onAutoPlayChanged: (Boolean) -> Unit = {},
    onNavigateToChats: () -> Unit,
    onNavigateToProfile: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val currentProfile by profileViewModel.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }

    val app = context.applicationContext as SvoiApp
    var globalNotifMuted by remember { mutableStateOf(app.themeManager.isNotificationsMuted()) }
    var showMuteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            MainBottomBar(
                selectedTab = 2,
                onChatsClick = onNavigateToChats,
                onProfileClick = onNavigateToProfile,
                onSettingsClick = {},
                currentProfile = currentProfile
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Тема ─────────────────────────────────────────────────────────
            SectionHeader("Тема оформления")

            ThemeOption(
                icon = Icons.Default.Settings,
                title = "Системная",
                subtitle = "Как в настройках устройства",
                selected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = { onThemeChanged(ThemeMode.SYSTEM) }
            )
            ThemeOption(
                icon = Icons.Default.LightMode,
                title = "Светлая",
                subtitle = "Всегда светлая тема",
                selected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { onThemeChanged(ThemeMode.LIGHT) }
            )
            ThemeOption(
                icon = Icons.Default.DarkMode,
                title = "Тёмная",
                subtitle = "Всегда тёмная тема",
                selected = currentThemeMode == ThemeMode.DARK,
                onClick = { onThemeChanged(ThemeMode.DARK) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Медиа ─────────────────────────────────────────────────────────
            SectionHeader("Медиа")

            ToggleRow(
                icon = Icons.Default.PlayCircle,
                title = "Автовоспроизведение видео",
                subtitle = "Запускать видео автоматически при прокрутке",
                checked = autoPlayVideos,
                onCheckedChange = onAutoPlayChanged
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Уведомления ───────────────────────────────────────────────────
            SectionHeader("Уведомления")

            ToggleRow(
                icon = Icons.Default.NotificationsOff,
                title = "Отключить все уведомления",
                subtitle = if (globalNotifMuted) "Уведомления отключены во всём приложении"
                           else "Получать уведомления о новых сообщениях",
                checked = globalNotifMuted,
                onCheckedChange = { newValue ->
                    if (newValue) {
                        // Показываем предупреждение только при включении мута
                        showMuteConfirmDialog = true
                    } else {
                        globalNotifMuted = false
                        app.themeManager.setNotificationsMuted(false)
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Данные ────────────────────────────────────────────────────────
            SectionHeader("Данные")

            ActionRow(
                icon = if (isClearingCache) null else Icons.Default.DeleteSweep,
                isLoading = isClearingCache,
                title = "Очистить кэш",
                subtitle = "Удалить сохранённые сообщения, профили, превью изображений",
                onClick = { showClearCacheDialog = true }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Диалог подтверждения отключения уведомлений ──────────────────────────
    if (showMuteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMuteConfirmDialog = false },
            title = { Text("Отключить уведомления?") },
            text = {
                Text("Вы не будете получать уведомления о новых сообщениях, пока сами не включите их обратно в настройках.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showMuteConfirmDialog = false
                    globalNotifMuted = true
                    app.themeManager.setNotificationsMuted(true)
                }) {
                    Text("Отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMuteConfirmDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ── Диалог подтверждения очистки ──────────────────────────────────────────
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистить кэш?") },
            text = { Text("Сохранённые данные будут удалены. Это не затронет сообщения на сервере.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    isClearingCache = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Clear app JSON cache
                            (context.applicationContext as SvoiApp).cacheManager.clearAll()
                            // Clear Coil image/video cache
                            context.imageLoader.memoryCache?.clear()
                            context.imageLoader.diskCache?.clear()
                        }
                        isClearingCache = false
                        snackbarHostState.showSnackbar("Кэш очищен")
                    }
                }) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// ── Секция-заголовок ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

// ── Строка темы (радио) ───────────────────────────────────────────────────────

@Composable
private fun ThemeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconTint by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(220),
            label = "themeIconTint"
        )
        Icon(icon, contentDescription = null, tint = iconTint,
            modifier = Modifier.padding(end = 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

// ── Строка с переключателем (Switch) ─────────────────────────────────────────

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Строка действия (кнопка / loader) ────────────────────────────────────────

@Composable
private fun ActionRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 0.dp),
                strokeWidth = 2.dp)
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
