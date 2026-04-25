package com.example.svoi.ui.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import com.example.svoi.ui.components.SvoiLoader
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import com.example.svoi.R
import com.example.svoi.SvoiApp
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.components.MainBottomBar
import com.example.svoi.ui.components.OfflineBanner
import com.example.svoi.ui.theme.DraftRed
import com.example.svoi.ui.theme.OnlineGreen
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.util.toChatListTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: ChatListViewModel = viewModel()
) {
    // Перехватываем системный «назад» — чтобы Navigation не попытался убрать
    // стартовый экран из стека (что даёт пустой NavHost и белый экран).
    BackHandler(enabled = true) { /* minimize handled by system after this intercept */ }

    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isReachable by viewModel.isReachable.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val chatTyping by viewModel.chatTyping.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as SvoiApp

    var selectedChat by remember { mutableStateOf<ChatListItem?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf(app.draftManager.getAllDrafts()) }
    val isVictoryDay = remember {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.MONTH) == java.util.Calendar.MAY &&
            cal.get(java.util.Calendar.DAY_OF_MONTH) == 9
    }
    var bannerDismissed by remember { mutableStateOf(viewModel.victoryBannerDismissed) }

    // Refresh unread counts when user returns to this screen (e.g. after reading a chat)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.silentRefresh()
                viewModel.refreshCurrentProfile()
                drafts = app.draftManager.getAllDrafts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState()

    Scaffold(
        bottomBar = {
            MainBottomBar(
                selectedTab = 0,
                onChatsClick = {},
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Свои",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск по сообщениям"
                        )
                    }
                    if (!isOnline || !isReachable) {
                        Icon(
                            imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.CloudOff,
                            contentDescription = "Нет соединения",
                            modifier = Modifier.padding(end = 12.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner(isOnline = isOnline, isReachable = isReachable, isUpdating = isUpdating)
            AnimatedVisibility(
                visible = isVictoryDay && !bannerDismissed,
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                )
            ) {
                VictoryDayBanner(onDismiss = {
                    bannerDismissed = true
                    viewModel.dismissVictoryBanner()
                })
            }
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (isLoading && chats.isEmpty()) {
                SvoiLoader(modifier = Modifier.align(Alignment.Center))
            } else if (chats.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💬", fontSize = 36.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Нет чатов",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите + чтобы начать",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn {
                    items(chats, key = { it.chatId }) { chat ->
                        Column(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                fadeOutSpec = tween(150)
                            )
                        ) {
                            ChatListItem(
                                item = chat,
                                typingText = chatTyping[chat.chatId],
                                draftText = drafts[chat.chatId],
                                onClick = { onChatClick(chat.chatId) },
                                onLongClick = {
                                    selectedChat = chat
                                    showBottomSheet = true
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp),
                                thickness = 0.4.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
        } // Column
    }

    // Long-press bottom sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                selectedChat?.let { chat ->
                    Text(
                        text = chat.displayName,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            showClearConfirm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "Очистить историю",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            showDeleteConfirm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "Удалить чат",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить чат?") },
            text = { Text("Чат и все сообщения будут удалены навсегда.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    selectedChat?.let { chat ->
                        scope.launch { viewModel.deleteChat(chat.chatId) }
                    }
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }

    // Clear history confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все сообщения в этом чате будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    selectedChat?.let { chat ->
                        scope.launch { viewModel.clearHistory(chat.chatId) }
                    }
                }) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun VictoryDayBanner(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.victory_day_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "С Днём Победы!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.OnlineDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "online_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(pulseScale)
            .align(Alignment.BottomEnd)
            .background(OnlineGreen, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    item: ChatListItem,
    typingText: String?,
    draftText: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = SvoiDimens.ScreenHorizontalPadding, vertical = SvoiDimens.ItemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with optional online dot
        Box {
            Avatar(
                emoji = item.emoji,
                bgColor = item.bgColor,
                isGroup = item.isGroup,
                letter = item.displayName,
                size = SvoiDimens.AvatarMedium,
                fontSize = 24.sp
            )
            if (!item.isGroup && item.isOtherOnline) {
                OnlineDot()
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isMuted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Уведомления отключены",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.lastMessageIsOwn) {
                        Icon(
                            imageVector = if (item.lastMessageIsRead) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).padding(end = 2.dp),
                            tint = if (item.lastMessageIsRead) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.lastMessageTime.toChatListTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.unreadCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasDraft = typingText == null && !draftText.isNullOrBlank()
                if (hasDraft) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = DraftRed)) {
                                append("Черновик: ")
                            }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(draftText!!)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (typingText == null && item.lastMessageIsForwarded) {
                            Icon(
                                imageVector = Icons.Default.Redo,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(end = 2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = typingText ?: item.lastMessageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (typingText != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    val badgeText = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .defaultMinSize(minWidth = 22.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
