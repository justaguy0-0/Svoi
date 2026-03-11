package com.example.svoi.ui.chat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.svoi.ui.components.EmojiPicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.theme.BubbleOther
import com.example.svoi.ui.theme.BubbleOtherText
import com.example.svoi.ui.theme.BubbleOwn
import com.example.svoi.ui.theme.BubbleOwnText
import com.example.svoi.ui.theme.DarkBackground
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
    onUserClick: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    LaunchedEffect(chatId) { viewModel.init(chatId) }

    val messages by viewModel.messages.collectAsState()
    val chat by viewModel.chat.collectAsState()
    val chatName by viewModel.chatName.collectAsState()
    val isGroup by viewModel.isGroup.collectAsState()
    val presence by viewModel.otherUserPresence.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val pinnedContent by viewModel.pinnedMessageContent.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrollToBottomEvent by viewModel.scrollToBottomEvent.collectAsState()
    val firstUnreadIndex by viewModel.firstUnreadIndex.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val highlightedMessageId by viewModel.highlightedMessageId.collectAsState()
    val scrollToMessageEvent by viewModel.scrollToMessageEvent.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()
    val chatsForForward by viewModel.chatsForForward.collectAsState()
    val otherUserId by viewModel.otherUserId.collectAsState()

    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Bottom sheet state
    var selectedMessage by remember { mutableStateOf<MessageUiItem?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Forward picker state
    var showForwardPicker by remember { mutableStateOf(false) }
    var pendingForwardMessageId by remember { mutableStateOf<String?>(null) }

    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.roundToPx()
    }

    // Exit selection mode with back button
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    val displayEntries = remember(messages, firstUnreadIndex) {
        buildList {
            messages.forEachIndexed { index, item ->
                if (index == firstUnreadIndex && firstUnreadIndex >= 0) {
                    add(ChatEntry.UnreadDivider)
                }
                val prev = if (index > 0) messages[index - 1] else null
                val showDate = prev == null ||
                    item.message.createdAt?.take(10) != prev.message.createdAt?.take(10)
                if (showDate && !item.message.createdAt.isNullOrBlank()) {
                    add(ChatEntry.DateDivider(
                        date = item.message.createdAt!!.toDateSeparator(),
                        triggerMsgId = item.message.id
                    ))
                }
                add(ChatEntry.Msg(item))
            }
        }
    }

    // Scroll to first unread or bottom
    LaunchedEffect(scrollToBottomEvent) {
        if (displayEntries.isNotEmpty()) {
            val unreadEntryIdx = displayEntries.indexOfFirst { it is ChatEntry.UnreadDivider }
            val unreadCount = if (firstUnreadIndex >= 0) messages.size - firstUnreadIndex else 0
            val target = if (unreadEntryIdx >= 0 && unreadCount >= 5) unreadEntryIdx
                         else displayEntries.size - 1
            if (target >= 0) {
                val offset = if (unreadEntryIdx >= 0 && unreadCount >= 5)
                    -(screenHeightPx / 3) else 0
                listState.scrollToItem(target, scrollOffset = offset)
            }
        }
    }

    // Scroll to specific message (from pinned banner or system message click)
    LaunchedEffect(scrollToMessageEvent) {
        val targetId = scrollToMessageEvent ?: return@LaunchedEffect
        val idx = displayEntries.indexOfFirst { it is ChatEntry.Msg && it.item.message.id == targetId }
        if (idx >= 0) {
            listState.smoothScrollToItem(idx, scrollOffset = -(screenHeightPx / 3))
        }
        viewModel.clearScrollToMessageEvent()
    }

    LaunchedEffect(editingMessage) {
        editingMessage?.let { inputValue = TextFieldValue(it.content ?: "") }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendPhoto(it, context) }
    }

    val presenceText = remember(presence) {
        val p = presence
        when {
            p == null -> ""
            p.isTrulyOnline() -> "в сети"
            !p.lastSeen.isNullOrBlank() -> p.lastSeen!!.toLastSeen()
            else -> ""
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text(
                                text = "Выбрано ${selectedMessageIds.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Column(
                                modifier = Modifier.clickable(enabled = !isGroup && otherUserId != null) {
                                    otherUserId?.let { onUserClick(it) }
                                }
                            ) {
                                Text(
                                    text = chatName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val typingText = remember(typingUsers, isGroup) { typingIndicatorText(typingUsers, isGroup) }
                                val subtitleText: String? = when {
                                    typingText != null -> typingText
                                    !isOnline -> "Подключение..."
                                    isUpdating && memberCount == 0 && presenceText.isBlank() -> "Обновление..."
                                    isGroup && memberCount > 0 -> memberCountText(memberCount)
                                    !isGroup && presenceText.isNotBlank() -> presenceText
                                    else -> null
                                }
                                val isStatusAnimated = !isOnline || (isUpdating && memberCount == 0 && presenceText.isBlank())
                                val isTyping = typingText != null
                                if (subtitleText != null) {
                                    if (isStatusAnimated && !isTyping) {
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
                                            color = when {
                                                isTyping -> MaterialTheme.colorScheme.primary
                                                !isGroup && presence?.isTrulyOnline() == true -> Online
                                                else -> TextSecondary
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSelectionMode) viewModel.clearSelection() else onBack()
                        }) {
                            Icon(
                                if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                                contentDescription = if (isSelectionMode) "Отмена" else "Назад"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Pinned message banner
                if (!isSelectionMode) {
                    pinnedMessage?.let { pinned ->
                        val contentText = when (pinnedContent?.type) {
                            "photo" -> "📷 Фото"
                            "file" -> "📎 ${pinnedContent?.fileName ?: "Файл"}"
                            else -> pinnedContent?.content ?: ""
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.scrollToMessage(pinned.messageId) },
                            color = MaterialTheme.colorScheme.surfaceVariant
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
                                        contentText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.unpinMessage() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Открепить",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                .padding(top = padding.calculateTopPadding())
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
                        items(
                            displayEntries,
                            key = { entry ->
                                when (entry) {
                                    is ChatEntry.Msg -> entry.item.message.id
                                    is ChatEntry.DateDivider -> "date_${entry.triggerMsgId}"
                                    ChatEntry.UnreadDivider -> "unread_divider"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is ChatEntry.Msg -> {
                                    val msg = entry.item.message
                                    if (msg.type == "system") {
                                        SystemMessageItem(
                                            item = entry.item,
                                            onClick = {
                                                msg.replyToId?.let { viewModel.scrollToMessage(it) }
                                            }
                                        )
                                    } else {
                                        MessageItem(
                                            item = entry.item,
                                            isGroup = isGroup,
                                            isHighlighted = entry.item.message.id == highlightedMessageId,
                                            isSelected = entry.item.message.id in selectedMessageIds,
                                            isSelectionMode = isSelectionMode,
                                            modifier = Modifier,
                                            onLongClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(entry.item.message.id)
                                                } else {
                                                    selectedMessage = entry.item
                                                }
                                            },
                                            onTap = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(entry.item.message.id)
                                                }
                                            },
                                            onReply = {
                                                viewModel.setReplyTo(entry.item.message)
                                            },
                                            onPhotoClick = { url -> lightboxUrl = url },
                                            onUserClick = { userId -> onUserClick(userId) }
                                        )
                                    }
                                }
                                is ChatEntry.DateDivider -> DateSeparator(date = entry.date)
                                ChatEntry.UnreadDivider -> UnreadMessagesDivider()
                            }
                        }
                    }
                }

                // Scroll to bottom button
                val showScrollToBottom by remember { derivedStateOf { listState.canScrollForward } }
                if (showScrollToBottom && !isSelectionMode) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch { listState.smoothScrollToItem(displayEntries.size - 1) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Input area or Selection action bar
            if (isSelectionMode) {
                SelectionActionBar(
                    selectedCount = selectedMessageIds.size,
                    onForward = {
                        viewModel.loadChatsForForward()
                        pendingForwardMessageId = null
                        showForwardPicker = true
                    },
                    onDeleteLocally = { viewModel.deleteSelectedMessages(forEveryone = false) },
                    onDeleteForAll = { viewModel.deleteSelectedMessages(forEveryone = true) },
                    hasOwnMessages = messages.any { it.message.id in selectedMessageIds && it.isOwn }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                ) {
                    // Emoji picker panel
                    AnimatedVisibility(
                        visible = showEmojiPicker,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        EmojiPicker(
                            onEmojiSelected = { emoji ->
                                val sel = inputValue.selection
                                val text = inputValue.text
                                val newText = text.substring(0, sel.start) + emoji + text.substring(sel.end)
                                val newCursor = sel.start + emoji.length
                                inputValue = TextFieldValue(newText, TextRange(newCursor))
                                viewModel.onInputTextChanged(newText)
                            }
                        )
                    }

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
                                inputValue = TextFieldValue("")
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
                        // Emoji button
                        IconButton(onClick = {
                            showEmojiPicker = !showEmojiPicker
                            if (showEmojiPicker) keyboardController?.hide()
                        }) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                contentDescription = "Эмодзи",
                                tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextField(
                            value = inputValue,
                            onValueChange = { inputValue = it; viewModel.onInputTextChanged(it.text) },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .onFocusChanged { if (it.isFocused) showEmojiPicker = false },
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

                        // Photo / Send button
                        if (inputValue.text.isBlank()) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Фото",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        viewModel.sendText(inputValue.text)
                                        inputValue = TextFieldValue("")
                                        viewModel.onInputTextChanged("")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Отправить",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Image Lightbox ──────────────────────────────────────────────────────────
    lightboxUrl?.let { url ->
        val ctx = LocalContext.current
        ImageLightbox(
            url = url,
            onDismiss = { lightboxUrl = null },
            onDownload = {
                val filename = "svoi_${System.currentTimeMillis()}.jpg"
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(filename)
                    .setDescription("Сохранение изображения")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Svoi/$filename")
                    .setMimeType("image/jpeg")
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
            }
        )
    }

    // ── Bottom sheet for message actions ──────────────────────────────────────
    selectedMessage?.let { selected ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessage = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // Message preview
                Text(
                    text = when (selected.message.type) {
                        "photo" -> "📷 Фото"
                        "file" -> "📎 ${selected.message.fileName ?: "Файл"}"
                        else -> selected.message.content ?: ""
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Reply
                BottomSheetAction(Icons.Default.Reply, "Ответить") {
                    viewModel.setReplyTo(selected.message)
                    selectedMessage = null
                }

                // Forward
                BottomSheetAction(Icons.Default.Share, "Переслать") {
                    pendingForwardMessageId = selected.message.id
                    viewModel.loadChatsForForward()
                    // Ждём полного закрытия шита перед показом диалога,
                    // иначе touch-up от нажатия сразу закроет диалог.
                    scope.launch {
                        sheetState.hide()
                        selectedMessage = null
                        showForwardPicker = true
                    }
                }

                // Select (enter selection mode)
                BottomSheetAction(Icons.Default.Check, "Выделить") {
                    viewModel.toggleSelection(selected.message.id)
                    selectedMessage = null
                }

                // Pin / Unpin
                val isPinned = pinnedMessage?.messageId == selected.message.id
                BottomSheetAction(
                    icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    text = if (isPinned) "Открепить" else "Закрепить"
                ) {
                    if (isPinned) viewModel.unpinMessage() else viewModel.pinMessage(selected.message.id)
                    selectedMessage = null
                }

                // Copy (text only)
                if (selected.message.type == "text" && !selected.message.content.isNullOrBlank()) {
                    BottomSheetAction(Icons.Default.ContentCopy, "Копировать") {
                        clipboardManager.setText(AnnotatedString(selected.message.content!!))
                        selectedMessage = null
                    }
                }

                // Edit (own, text, < 24h)
                if (selected.isOwn && selected.message.type == "text") {
                    val canEdit = runCatching {
                        val msgTime = java.time.Instant.parse(selected.message.createdAt ?: "")
                        java.time.Instant.now().epochSecond - msgTime.epochSecond < 86400
                    }.getOrDefault(false)
                    if (canEdit) {
                        BottomSheetAction(Icons.Default.Edit, "Редактировать") {
                            viewModel.setEditing(selected.message)
                            selectedMessage = null
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Delete
                if (selected.isOwn) {
                    BottomSheetAction(
                        Icons.Default.Delete, "Удалить для всех",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        viewModel.deleteMessage(selected.message.id, true)
                        selectedMessage = null
                    }
                }
                BottomSheetAction(
                    Icons.Default.Delete, "Удалить у себя",
                    color = MaterialTheme.colorScheme.error
                ) {
                    viewModel.deleteMessage(selected.message.id, false)
                    selectedMessage = null
                }
            }
        }
    }

    // ── Forward chat picker ────────────────────────────────────────────────────
    if (showForwardPicker) {
        ForwardPickerDialog(
            chats = chatsForForward,
            onDismiss = {
                showForwardPicker = false
                pendingForwardMessageId = null
            },
            onSelect = { targetChatId ->
                val fwdId = pendingForwardMessageId
                if (fwdId != null) {
                    viewModel.forwardSingleMessage(fwdId, targetChatId)
                } else {
                    viewModel.forwardSelectedMessages(targetChatId)
                }
                showForwardPicker = false
                pendingForwardMessageId = null
            }
        )
    }
}

// ── Bottom sheet action row ──────────────────────────────────────────────────

@Composable
private fun BottomSheetAction(
    icon: ImageVector,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

// ── Selection action bar ──────────────────────────────────────────────────────

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onForward: () -> Unit,
    onDeleteLocally: () -> Unit,
    onDeleteForAll: () -> Unit,
    hasOwnMessages: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Forward
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onForward)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Переслать",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("Переслать", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            // Delete locally
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onDeleteLocally)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить у себя",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("Удалить", style = MaterialTheme.typography.labelSmall)
            }

            // Delete for all (only if own messages selected)
            if (hasOwnMessages) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(onClick = onDeleteForAll)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить для всех",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Для всех", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Forward picker dialog ─────────────────────────────────────────────────────

@Composable
private fun ForwardPickerDialog(
    chats: List<ChatListItem>,
    onDismiss: () -> Unit,
    onSelect: (chatId: String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    "Переслать в...",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                HorizontalDivider()
                if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(chats, key = { it.chatId }) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(chat.chatId) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(
                                    emoji = chat.emoji,
                                    bgColor = chat.bgColor,
                                    size = 40.dp,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    chat.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── System message (centered, clickable) ─────────────────────────────────────

@Composable
private fun SystemMessageItem(
    item: MessageUiItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = item.message.content ?: "",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Date separator ───────────────────────────────────────────────────────────

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

// ── Unread divider ───────────────────────────────────────────────────────────

@Composable
private fun UnreadMessagesDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        )
        Text(
            text = "  Новые сообщения  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        )
    }
}

// ── Message bubble ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    item: MessageUiItem,
    isGroup: Boolean,
    isHighlighted: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onTap: () -> Unit = {},
    onReply: () -> Unit = {},
    onPhotoClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {}
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

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val bubbleColor = if (item.isOwn) BubbleOwn else if (isDark) DarkBubbleOther else BubbleOther
    val textColor = if (item.isOwn) BubbleOwnText else if (isDark) DarkBubbleOtherText else BubbleOtherText
    val bubbleShape = if (item.isOwn) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    // Highlight animation
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 500),
        label = "highlight"
    )

    // Selection tint
    val selectionColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else Color.Transparent

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val swipeOffset = remember { Animatable(0f) }
    val replyTriggered = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) selectionColor else highlightColor,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(isSelectionMode, item.isOwn) {
                if (!isSelectionMode) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (item.isOwn) {
                                if (swipeOffset.value <= -swipeThresholdPx) onReply()
                            } else {
                                if (swipeOffset.value >= swipeThresholdPx) onReply()
                            }
                            replyTriggered.value = false
                            scope.launch {
                                swipeOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                            }
                        },
                        onDragCancel = {
                            replyTriggered.value = false
                            scope.launch { swipeOffset.animateTo(0f) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (item.isOwn) {
                                // Own messages: swipe left (negative)
                                if (dragAmount < 0) {
                                    val newOffset = (swipeOffset.value + dragAmount)
                                        .coerceIn(-swipeThresholdPx * 1.3f, 0f)
                                    scope.launch { swipeOffset.snapTo(newOffset) }
                                    if (newOffset <= -swipeThresholdPx && !replyTriggered.value) {
                                        replyTriggered.value = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            } else {
                                // Others' messages: swipe right (positive)
                                if (dragAmount > 0) {
                                    val newOffset = (swipeOffset.value + dragAmount)
                                        .coerceIn(0f, swipeThresholdPx * 1.3f)
                                    scope.launch { swipeOffset.snapTo(newOffset) }
                                    if (newOffset >= swipeThresholdPx && !replyTriggered.value) {
                                        replyTriggered.value = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        }
                    )
                }
            },
        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start
    ) {
        val swipeOffsetDp = with(density) { swipeOffset.value.toDp() }
        val replyIconAlpha = (kotlin.math.abs(swipeOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxWidth()) {
            // Reply icon — appears on the revealed side
            if (replyIconAlpha > 0.01f) {
                Icon(
                    Icons.Default.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = replyIconAlpha),
                    modifier = Modifier
                        .align(if (item.isOwn) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 12.dp)
                        .size(20.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = swipeOffsetDp),
                horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!item.isOwn && isGroup) {
                    item.senderProfile?.let { profile ->
                        Avatar(
                            emoji = profile.emoji,
                            bgColor = profile.bgColor,
                            size = 28.dp,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 2.dp)
                                .combinedClickable(
                                    onClick = { if (!isSelectionMode) onUserClick(profile.id) else onTap() },
                                    onLongClick = onLongClick
                                )
                        )
                    } ?: Spacer(Modifier.width(32.dp))
                }

                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    shadowElevation = if (item.isOwn) 0.dp else 1.dp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .combinedClickable(
                            onClick = onTap,
                            onLongClick = onLongClick
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Sender name in group chats
                        if (!item.isOwn && isGroup) {
                            Text(
                                text = item.senderProfile?.displayName ?: "Пользователь",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.combinedClickable(
                                    onClick = { if (!isSelectionMode) item.senderProfile?.let { onUserClick(it.id) } },
                                    onLongClick = onLongClick
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                        }

                        // Forwarded from header
                        item.forwardedFromProfile?.let { fwdProfile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = if (item.isOwn) Color.White.copy(0.7f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Переслано от ${fwdProfile.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.isOwn) Color.White.copy(0.75f) else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Reply reference
                        item.replyToMessage?.let { reply ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (item.isOwn) Color.White.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(32.dp)
                                            .background(
                                                if (item.isOwn) Color.White
                                                else MaterialTheme.colorScheme.primary
                                            )
                                            .align(Alignment.CenterVertically)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        val authorName = item.replyToSenderProfile?.displayName ?: "Пользователь"
                                        Text(
                                            text = authorName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.isOwn) Color.White
                                                    else MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = reply.content ?: "[медиа]",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (item.isOwn) Color.White.copy(0.85f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Message content
                        when (msg.type) {
                            "photo" -> {
                                if (msg.fileUrl != null) {
                                    SubcomposeAsyncImage(
                                        model = msg.fileUrl,
                                        contentDescription = "Фото",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .widthIn(min = 120.dp, max = 220.dp)
                                            .height(160.dp)
                                            .combinedClickable(
                                                onClick = { if (!isSelectionMode) onPhotoClick(msg.fileUrl) else onTap() },
                                                onLongClick = onLongClick
                                            ),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp,
                                                    color = if (item.isOwn) Color.White.copy(0.7f)
                                                            else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.BrokenImage,
                                                    contentDescription = null,
                                                    tint = textColor.copy(0.5f),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
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
            }
        }
    }
}

private fun memberCountText(count: Int) = when {
    count % 100 in 11..19 -> "$count участников"
    count % 10 == 1 -> "$count участник"
    count % 10 in 2..4 -> "$count участника"
    else -> "$count участников"
}

private sealed class ChatEntry {
    data class Msg(val item: MessageUiItem) : ChatEntry()
    data class DateDivider(val date: String, val triggerMsgId: String) : ChatEntry()
    object UnreadDivider : ChatEntry()
}

@Composable
private fun ImageLightbox(
    url: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Image with loading state
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "Изображение",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                },
                error = {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                    )
                }
            )

            // Top bar: download + close buttons
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Скачать",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun typingIndicatorText(users: List<TypingInfo>, isGroup: Boolean): String? {
    if (users.isEmpty()) return null
    return if (!isGroup) {
        "Печатает..."
    } else when (users.size) {
        1 -> "${users[0].displayName} печатает..."
        2 -> "${users[0].displayName} и ${users[1].displayName} печатают..."
        else -> "${users[0].displayName}, ${users[1].displayName} и ещё ${users.size - 2} печатают..."
    }
}

/**
 * Truly smooth tween-based scroll to an item.
 *
 * Compose's [animateScrollToItem] uses a fixed spring spec — it has no public
 * [animationSpec] parameter and can feel abrupt over large distances.
 *
 * This implementation:
 *  1. If the target is far (> 10 items), **instantly** jumps to 8 items before it
 *     (one frame, imperceptible to the user) so the remaining pixel distance is small.
 *  2. Reads the exact pixel offset of the target from [layoutInfo].
 *  3. Drives the scroll with [animate] + [tween] + [FastOutSlowInEasing] inside
 *     [scroll] { [scrollBy] } — giving a genuine ease-in-out glide.
 */
private suspend fun LazyListState.smoothScrollToItem(index: Int, scrollOffset: Int = 0) {
    // Phase 1: instant jump to bring target into layout range
    val diff = index - firstVisibleItemIndex
    if (kotlin.math.abs(diff) > 10) {
        val clampedMax = (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        val jumpTo = if (diff > 0) (index - 8).coerceAtLeast(0)
                     else (index + 8).coerceAtMost(clampedMax)
        scrollToItem(jumpTo)
    }

    // Phase 2: find target's current pixel position
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (targetItem == null) {
        // Fallback: target still off-screen (e.g. very large items) — use default
        animateScrollToItem(index, scrollOffset)
        return
    }

    val pixelDelta = targetItem.offset.toFloat() - scrollOffset.toFloat()
    if (kotlin.math.abs(pixelDelta) < 1f) return  // already at target

    // Phase 3: real tween scroll with FastOutSlowIn easing (starts fast, decelerates)
    scroll {
        var prev = 0f
        animate(
            initialValue = 0f,
            targetValue = pixelDelta,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        ) { value, _ ->
            scrollBy(value - prev)
            prev = value
        }
    }
}
