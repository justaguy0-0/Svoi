package com.example.svoi.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.example.svoi.BuildConfig
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.SvoiAccent
import com.example.svoi.data.local.ThemeMode
import com.example.svoi.data.model.AppVersion
import com.example.svoi.ui.components.MainBottomBar
import com.example.svoi.ui.profile.ProfileViewModel
import com.example.svoi.ui.theme.accentPalette
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
    currentAccent: SvoiAccent = SvoiAccent.BLUE,
    onAccentChanged: (SvoiAccent) -> Unit = {},
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

    val updateAvailable by app.updateAvailable.collectAsState()
    var showUpdateSheet by remember { mutableStateOf(false) }

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
            // ── Баннер обновления ─────────────────────────────────────────────
            if (updateAvailable != null) {
                UpdateBanner(
                    version = updateAvailable!!,
                    onClick = { showUpdateSheet = true }
                )
                Spacer(Modifier.height(4.dp))
            }

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

            AccentColorPicker(
                currentAccent = currentAccent,
                onAccentChanged = onAccentChanged
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

    // ── Bottom sheet обновления ───────────────────────────────────────────────
    if (showUpdateSheet && updateAvailable != null) {
        UpdateBottomSheet(
            update = updateAvailable!!,
            onDismiss = { showUpdateSheet = false }
        )
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

// ── Баннер обновления в настройках ───────────────────────────────────────────

@Composable
private fun UpdateBanner(version: AppVersion, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Доступно обновление",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Версия ${version.versionName} · Нажмите, чтобы узнать подробности",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Bottom Sheet с деталями обновления ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBottomSheet(update: AppVersion, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Заголовок
            Text(
                text = "Доступно обновление",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Версии: с какой на какую
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                VersionChip(
                    label = "Текущая",
                    version = BuildConfig.VERSION_NAME,
                    isNew = false
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(28.dp)
                )
                VersionChip(
                    label = "Новая",
                    version = update.versionName,
                    isNew = true
                )
            }

            // Блок "Что нового" — показываем только если changelog не пустой
            if (update.changelog.isNotBlank()) {
                Spacer(Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Что нового",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = update.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Кнопка скачивания
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Перейти к скачиванию",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onDismiss) {
                Text(
                    "Позже",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VersionChip(label: String, version: String, isNew: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isNew) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isNew) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "v$version",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isNew) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Выбор акцент-цвета ───────────────────────────────────────────────────────

private val accentLabels = mapOf(
    SvoiAccent.BLUE   to "Синий",
    SvoiAccent.ORANGE to "Оранжевый",
    SvoiAccent.RED    to "Красный",
    SvoiAccent.GREEN  to "Зелёный",
    SvoiAccent.PINK   to "Розовый",
    SvoiAccent.PURPLE to "Фиолетовый"
)

@Composable
private fun AccentColorPicker(
    currentAccent: SvoiAccent,
    onAccentChanged: (SvoiAccent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Цветовая палитра",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = accentLabels[currentAccent] ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SvoiAccent.entries.forEach { accent ->
                val palette = accentPalette(accent)
                val isSelected = accent == currentAccent
                val scale by animateColorAsState(
                    targetValue = if (isSelected) palette.primary else palette.primary,
                    animationSpec = tween(200),
                    label = "accentAnim"
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                            else Modifier
                        )
                        .clickable { onAccentChanged(accent) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
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
