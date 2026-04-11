package com.example.svoi.ui.group

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.chat.FullscreenVideoPlayer
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.media.ChatMediaViewModel
import com.example.svoi.ui.media.MediaImageLightbox
import com.example.svoi.ui.media.MediaItem
import com.example.svoi.ui.media.MediaTypeTabBar
import com.example.svoi.ui.media.addPhotoSections
import com.example.svoi.ui.media.addVideoSections
import com.example.svoi.ui.media.addVoiceSections
import com.example.svoi.ui.theme.OnlineGreen
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes
import com.example.svoi.ui.theme.groupAvatarColor
import com.example.svoi.util.toLastSeen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupInfoScreen(
    chatId: String,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    onChatDeleted: () -> Unit,
    viewModel: GroupInfoViewModel = viewModel()
) {
    LaunchedEffect(chatId) { viewModel.init(chatId) }

    val chat by viewModel.chat.collectAsState()
    val members by viewModel.members.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val chatDeleted by viewModel.chatDeleted.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(chatDeleted) { if (chatDeleted) onChatDeleted() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    // ── Media state ───────────────────────────────────────────────────────────
    val mediaViewModel: ChatMediaViewModel = viewModel()
    LaunchedEffect(chatId) { if (chatId.isNotEmpty()) mediaViewModel.load(chatId) }
    val photos by mediaViewModel.photos.collectAsState()
    val videos by mediaViewModel.videos.collectAsState()
    val voices by mediaViewModel.voices.collectAsState()
    val totalPhotos by mediaViewModel.totalPhotos.collectAsState()
    val totalVideos by mediaViewModel.totalVideos.collectAsState()
    val totalVoices by mediaViewModel.totalVoices.collectAsState()
    val isMediaLoading by mediaViewModel.isLoading.collectAsState()

    var selectedMediaTab by remember { mutableIntStateOf(0) }
    val allPhotoUrls = remember(photos) {
        photos.flatMap { s -> s.items.filterIsInstance<MediaItem.Photo>().map { it.url } }
    }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }

    // ── Voice player ──────────────────────────────────────────────────────────
    val voicePlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { voicePlayer.release() } }
    var playingVoiceUrl by remember { mutableStateOf<String?>(null) }
    var voiceIsPlaying by remember { mutableStateOf(false) }
    var voicePositionMs by remember { mutableStateOf(0L) }
    var voiceDurationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(playingVoiceUrl, voiceIsPlaying) {
        if (voiceIsPlaying) {
            while (true) {
                voicePositionMs = voicePlayer.currentPosition
                val dur = voicePlayer.duration
                if (dur > 0) voiceDurationMs = dur
                delay(100)
            }
        }
    }
    DisposableEffect(voicePlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    voiceIsPlaying = false
                    voicePositionMs = 0
                    voicePlayer.seekTo(0)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { voiceIsPlaying = playing }
        }
        voicePlayer.addListener(listener)
        onDispose { voicePlayer.removeListener(listener) }
    }
    val onVoicePlay: (String) -> Unit = { url ->
        if (playingVoiceUrl != url) {
            voicePlayer.stop()
            voicePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            voicePlayer.prepare()
            voiceDurationMs = 0
            voicePositionMs = 0
        }
        playingVoiceUrl = url
        voicePlayer.play()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о группе") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Group header (scrolls away) ────────────────────────────────
                item(key = "group_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(
                            emoji = (chat?.name ?: "Г").take(1),
                            bgColor = groupAvatarColor(chatId),
                            isGroup = true,
                            letter = chat?.name ?: "Г",
                            size = SvoiDimens.AvatarXLarge,
                            fontSize = 44.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chat?.name ?: "Группа",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isAdmin) {
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { showRenameDialog = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Переименовать",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${members.size} участников",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Sticky outer tabs (Участники / Вложения) ──────────────────
                stickyHeader(key = "outer_tabs") {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    "Участники",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "Вложения",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    // ── Участники ──────────────────────────────────────────────
                    0 -> {
                        if (isAdmin) {
                            item(key = "add_member_btn") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAddMemberDialog = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Добавить участника",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        items(members, key = { it.member.userId }) { item ->
                            val isSelf = item.member.userId == currentUserId
                            val isOnline = item.presence?.isTrulyOnline() == true
                            MemberRow(
                                item = item,
                                isOnline = isOnline,
                                isSelf = isSelf,
                                showRemove = isAdmin && !isSelf,
                                onClick = { if (!isSelf) onMemberClick(item.member.userId) },
                                onRemove = { viewModel.removeMember(item.member.userId) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if (isAdmin) {
                            item(key = "delete_group_btn") {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDeleteDialog = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Удалить группу",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // ── Вложения ───────────────────────────────────────────────
                    1 -> {
                        stickyHeader(key = "media_tab_bar") {
                            MediaTypeTabBar(
                                selectedTab = selectedMediaTab,
                                onTabSelected = { selectedMediaTab = it },
                                totalPhotos = totalPhotos,
                                totalVideos = totalVideos,
                                totalVoices = totalVoices
                            )
                        }

                        if (isMediaLoading) {
                            item(key = "media_loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            }
                        } else {
                            when (selectedMediaTab) {
                                0 -> addPhotoSections(photos, allPhotoUrls) { lightboxIndex = it }
                                1 -> addVideoSections(videos) { fullscreenVideoUrl = it }
                                2 -> addVoiceSections(
                                    sections = voices,
                                    playingUrl = playingVoiceUrl,
                                    isPlaying = voiceIsPlaying,
                                    positionMs = voicePositionMs,
                                    durationMs = voiceDurationMs,
                                    onPlay = onVoicePlay,
                                    onPause = { voicePlayer.pause() },
                                    onSeek = { ms -> voicePlayer.seekTo(ms); voicePositionMs = ms }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Overlays ───────────────────────────────────────────────────────────────
    fullscreenVideoUrl?.let { url ->
        FullscreenVideoPlayer(url = url, onDismiss = { fullscreenVideoUrl = null })
    }
    lightboxIndex?.let { startIdx ->
        if (allPhotoUrls.isNotEmpty()) {
            MediaImageLightbox(
                urls = allPhotoUrls,
                startIndex = startIdx.coerceIn(0, allPhotoUrls.lastIndex),
                onDismiss = { lightboxIndex = null },
                onDownload = { url ->
                    val filename = "svoi_${System.currentTimeMillis()}.jpg"
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle(filename)
                        .setDescription("Сохранение изображения")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Svoi/$filename")
                        .setMimeType("image/jpeg")
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                }
            )
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = chat?.name ?: "",
            onConfirm = { newName ->
                viewModel.renameGroup(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить группу?") },
            text = { Text("Это действие нельзя отменить. Все сообщения будут потеряны.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteGroup(onDeleted = onChatDeleted)
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            existingMemberIds = members.map { it.member.userId }.toSet(),
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false }
        )
    }
}

@Composable
private fun MemberRow(
    item: MemberUiItem,
    isOnline: Boolean,
    isSelf: Boolean,
    showRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                emoji = item.profile?.emoji ?: "😊",
                bgColor = item.profile?.bgColor ?: "#5C6BC0",
                size = 46.dp,
                fontSize = 20.sp
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .background(OnlineGreen, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(item.profile?.displayName ?: "Пользователь")
                    if (isSelf) append(" (вы)")
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.member.role == "admin") {
                Text(
                    text = "Администратор",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!isSelf) {
                val presenceText = remember(item.presence) {
                    when {
                        item.presence?.isTrulyOnline() == true -> "в сети"
                        !item.presence?.lastSeen.isNullOrBlank() ->
                            item.presence!!.lastSeen!!.toLastSeen()
                        else -> null
                    }
                }
                if (presenceText != null) {
                    Text(
                        text = presenceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Удалить из группы",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать группу") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название группы") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = SvoiShapes.TextField
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberDialog(
    existingMemberIds: Set<String>,
    viewModel: GroupInfoViewModel,
    onDismiss: () -> Unit
) {
    val app = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.example.svoi.SvoiApp)
    val userRepo = app.userRepository
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.example.svoi.data.model.Profile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    var pendingUserId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); searchError = false; return@LaunchedEffect }
        isSearching = true
        searchError = false
        val found = runCatching { userRepo.searchUsers(query) }
        if (found.isSuccess) {
            results = found.getOrDefault(emptyList()).filter { it.id !in existingMemberIds }
        } else {
            results = emptyList()
            searchError = true
        }
        isSearching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить участника") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по имени") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(Modifier.height(8.dp))

                if (results.isNotEmpty()) {
                    Column {
                        results.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pendingUserId = profile.id }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(
                                    emoji = profile.emoji ?: "😊",
                                    bgColor = profile.bgColor ?: "#5C6BC0",
                                    size = 36.dp,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = profile.displayName ?: "Пользователь",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (searchError) {
                    Text(
                        "Ошибка поиска. Проверьте соединение.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (query.length >= 2 && !isSearching) {
                    Text(
                        "Пользователи не найдены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )

    if (pendingUserId != null) {
        AlertDialog(
            onDismissRequest = { pendingUserId = null },
            title = { Text("Показать историю?") },
            text = { Text("Показать добавляемому пользователю последние 100 сообщений?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addMember(pendingUserId!!, showHistory = true)
                    pendingUserId = null
                    onDismiss()
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.addMember(pendingUserId!!, showHistory = false)
                    pendingUserId = null
                    onDismiss()
                }) { Text("Нет") }
            }
        )
    }
}
