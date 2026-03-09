package com.example.svoi.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.ui.components.Avatar
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.svoi.ui.theme.BubbleOther
import com.example.svoi.ui.theme.BubbleOtherText
import com.example.svoi.ui.theme.BubbleOwn
import com.example.svoi.ui.theme.BubbleOwnText
import com.example.svoi.ui.theme.DarkBubbleOther
import com.example.svoi.ui.theme.DarkBubbleOtherText
import com.example.svoi.ui.theme.Online
import com.example.svoi.ui.theme.TextSecondary
import com.example.svoi.util.toDateSeparator
import com.example.svoi.util.toLastSeen
import com.example.svoi.util.toMessageTime
import com.example.svoi.util.toReadableSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onForwardTo: (String) -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    LaunchedEffect(chatId) { viewModel.init(chatId) }

    val messages by viewModel.messages.collectAsState()
    val chat by viewModel.chat.collectAsState()
    val chatName by viewModel.chatName.collectAsState()
    val isGroup by viewModel.isGroup.collectAsState()
    val presence by viewModel.otherUserPresence.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrollToBottomEvent by viewModel.scrollToBottomEvent.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Scroll to bottom on initial load and new messages
    LaunchedEffect(scrollToBottomEvent) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // When editing, populate the text field
    LaunchedEffect(editingMessage) {
        editingMessage?.let { inputText = it.content ?: "" }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendPhoto(it, context) }
    }

    val presenceText = when {
        presence == null -> ""
        presence!!.online -> "в сети"
        !presence!!.lastSeen.isNullOrBlank() -> presence!!.lastSeen!!.toLastSeen()
        else -> ""
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = chatName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val subtitleText: String? = when {
                                !isOnline -> "Подключение..."
                                isUpdating && memberCount == 0 && presenceText.isBlank() -> "Обновление..."
                                isGroup && memberCount > 0 -> memberCountText(memberCount)
                                !isGroup && presenceText.isNotBlank() -> presenceText
                                else -> null
                            }
                            val isStatusAnimated = !isOnline || (isUpdating && memberCount == 0 && presenceText.isBlank())
                            if (subtitleText != null) {
                                if (isStatusAnimated) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "chat_subtitle_pulse")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.4f,
                                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                                        label = "chat_subtitle_alpha"
                                    )
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.alpha(alpha)
                                    )
                                } else {
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (!isGroup && presence?.online == true) Online else TextSecondary
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Pinned message banner
                pinnedMessage?.let { pinned ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* scroll to pinned message */ },
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Закреплённое сообщение",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    pinned.messageId, // TODO: show message content
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Error banner
            error?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { viewModel.clearError() },
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Messages
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(messages, key = { _, item -> item.message.id }) { index, item ->
                            val prevItem = if (index > 0) messages[index - 1] else null
                            val showDateSeparator = prevItem == null ||
                                item.message.createdAt?.take(10) != prevItem.message.createdAt?.take(10)

                            if (showDateSeparator && !item.message.createdAt.isNullOrBlank()) {
                                DateSeparator(date = item.message.createdAt!!.toDateSeparator())
                            }

                            MessageItem(
                                item = item,
                                isGroup = isGroup,
                                modifier = Modifier.animateItem(fadeInSpec = tween(durationMillis = 150)),
                                onReply = { viewModel.setReplyTo(item.message) },
                                onEdit = { viewModel.setEditing(item.message) },
                                onDelete = { forAll ->
                                    viewModel.deleteMessage(item.message.id, forAll)
                                }
                            )
                        }
                    }
                }
            }

            // Input area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Reply / Edit preview
                val previewMessage = replyTo ?: editingMessage
                previewMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (editingMessage != null) "Редактирование" else "Ответ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = msg.content ?: "[медиа]",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            viewModel.setReplyTo(null)
                            viewModel.setEditing(null)
                            inputText = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                }

                // Text input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Attach button
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Фото",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Text field
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Сообщение...") },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(Modifier.width(6.dp))

                    // Send button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = inputText.isNotBlank()) {
                                viewModel.sendText(inputText)
                                inputText = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = if (inputText.isNotBlank()) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
            Text(
                text = date,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    item: MessageUiItem,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Boolean) -> Unit
) {
    val msg = item.message
    if (msg.deletedForAll) {
        Text(
            text = "Сообщение удалено",
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        return
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val alignment = if (item.isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (item.isOwn) BubbleOwn else if (isDark) DarkBubbleOther else BubbleOther
    val textColor = if (item.isOwn) BubbleOwnText else if (isDark) DarkBubbleOtherText else BubbleOtherText
    val bubbleShape = if (item.isOwn) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(
            modifier = Modifier.widthIn(max = 300.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Avatar for group incoming
            if (!item.isOwn && isGroup) {
                item.senderProfile?.let { profile ->
                    Avatar(
                        emoji = profile.emoji,
                        bgColor = profile.bgColor,
                        size = 28.dp,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                    )
                } ?: Spacer(Modifier.width(32.dp))
            }

            Box {
                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    shadowElevation = if (item.isOwn) 0.dp else 1.dp,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Sender name in group chats for incoming
                        if (!item.isOwn && isGroup) {
                            Text(
                                text = item.senderProfile?.displayName ?: "Пользователь",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                        }

                        // Reply reference
                        item.replyToMessage?.let { reply ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (item.isOwn) Color.White.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(modifier = Modifier.padding(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp)
                                            .background(
                                                if (item.isOwn) Color.White
                                                else MaterialTheme.colorScheme.primary
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = reply.content ?: "[медиа]",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (item.isOwn) Color.White.copy(0.9f) else textColor
                                    )
                                }
                            }
                        }

                        // Message content
                        when (msg.type) {
                            "photo" -> {
                                if (msg.fileUrl != null) {
                                    AsyncImage(
                                        model = msg.fileUrl,
                                        contentDescription = "Фото",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .widthIn(min = 120.dp, max = 220.dp)
                                            .height(160.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("📷 Фото", color = textColor)
                                }
                            }
                            "file" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = textColor.copy(0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = msg.fileName ?: "Файл",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                        msg.fileSize?.let { size ->
                                            Text(
                                                text = size.toReadableSize(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor.copy(0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    text = msg.content ?: "",
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Time + read status
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (msg.editedAt != null) {
                                Text(
                                    "изм. ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(0.7f),
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = msg.createdAt?.toMessageTime() ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(0.7f),
                                fontSize = 10.sp
                            )
                            if (item.isOwn) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = if (item.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (item.isRead) Color.White else textColor.copy(0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Context menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ответить") },
                        onClick = { showMenu = false; onReply() },
                        leadingIcon = { Icon(Icons.Default.Reply, null) }
                    )
                    if (item.isOwn) {
                        val canEdit = runCatching {
                            val msgTime = java.time.Instant.parse(msg.createdAt ?: "")
                            java.time.Instant.now().epochSecond - msgTime.epochSecond < 86400
                        }.getOrDefault(false)

                        if (canEdit && msg.type == "text") {
                            DropdownMenuItem(
                                text = { Text("Редактировать") },
                                onClick = { showMenu = false; onEdit() },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showDeleteDialog = true }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete(false) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить сообщение?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(true)
                }) { Text("Для всех", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(false)
                }) { Text("Только у меня") }
            }
        )
    }
}

private fun memberCountText(count: Int) = when {
    count % 100 in 11..19 -> "$count участников"
    count % 10 == 1 -> "$count участник"
    count % 10 in 2..4 -> "$count участника"
    else -> "$count участников"
}
