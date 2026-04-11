package com.example.svoi.ui.group

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.media.AttachmentsPane
import com.example.svoi.ui.theme.OnlineGreen
import com.example.svoi.ui.theme.SvoiDimens
import com.example.svoi.ui.theme.SvoiShapes
import com.example.svoi.ui.theme.groupAvatarColor
import com.example.svoi.util.toLastSeen
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
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
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ── Always-visible header ───────────────────────────────────────────
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

                // ── Tab row ─────────────────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
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

                // ── Tab content ─────────────────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Add member button (admin only)
                            if (isAdmin) {
                                item {
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

                            // Member list
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

                            // Delete group button (admin only)
                            if (isAdmin) {
                                item {
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showDeleteDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error)
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
                        1 -> AttachmentsPane(
                            chatId = chatId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
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

    // Delete dialog
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Add member dialog
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
        // Avatar with online dot
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

    // History dialog — shown after selecting a user to add
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
