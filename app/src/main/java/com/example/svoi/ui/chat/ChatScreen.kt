package com.example.svoi.ui.chat

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.svoi.ActiveChatTracker
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.svoi.SvoiApp
import com.example.svoi.ui.voice.GlobalVoiceMiniPlayer
import com.example.svoi.ui.voice.GlobalVoicePlayer
import com.example.svoi.ui.voice.GlobalVoiceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.svoi.ui.components.EmojiPicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.ReactionGroup
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.ui.components.Avatar
import com.example.svoi.ui.components.OfflineBanner
import com.example.svoi.ui.theme.BubbleOther
import com.example.svoi.ui.theme.BubbleOtherText
import com.example.svoi.ui.theme.BubbleOwnText
import com.example.svoi.ui.theme.DarkBackground
import com.example.svoi.ui.theme.DarkBubbleOther
import com.example.svoi.ui.theme.DarkBubbleOtherText
import com.example.svoi.ui.theme.Online
import com.example.svoi.ui.theme.SvoiShapes
import com.example.svoi.ui.theme.TextSecondary
import com.example.svoi.util.toDateSeparator
import com.example.svoi.util.toLastSeen
import com.example.svoi.util.toMessageTime
import com.example.svoi.util.toReadableSize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.media3.exoplayer.ExoPlayer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    draftUserId: String? = null,
    initialMessageId: String? = null,
    autoPlayVideos: Boolean = true,
    onBack: () -> Unit,
    onForwardTo: (String) -> Unit,
    onUserClick: (String) -> Unit = {},
    onGroupInfoClick: (String) -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    LaunchedEffect(chatId, draftUserId) {
        if (draftUserId != null) viewModel.initDraft(draftUserId)
        else viewModel.init(chatId)
    }

    DisposableEffect(chatId) {
        ActiveChatTracker.activeChatId = chatId
        onDispose {
            if (ActiveChatTracker.activeChatId == chatId) {
                ActiveChatTracker.activeChatId = null
            }
        }
    }

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
    val isReachable by viewModel.isReachable.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()
    val groupOnlineCount by viewModel.groupOnlineCount.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMoreMessages by viewModel.hasMoreMessages.collectAsState()
    val snapToBottomEvent by viewModel.snapToBottomEvent.collectAsState()
    val scrollToBottomEvent by viewModel.scrollToBottomEvent.collectAsState()
    val scrollToOwnMessageEvent by viewModel.scrollToOwnMessageEvent.collectAsState()
    val firstUnreadIndex by viewModel.firstUnreadIndex.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val highlightedMessageId by viewModel.highlightedMessageId.collectAsState()
    val scrollToMessageEvent by viewModel.scrollToMessageEvent.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()
    val chatsForForward by viewModel.chatsForForward.collectAsState()
    val otherUserId by viewModel.otherUserId.collectAsState()
    val isChatDeleted by viewModel.isChatDeleted.collectAsState()
    val animatingMessageIds by viewModel.animatingMessageIds.collectAsState()
    val stagedMedia by viewModel.stagedMedia.collectAsState()
    val uploadProgresses by viewModel.uploadProgresses.collectAsState()
    val voiceRecordState by viewModel.voiceRecordState.collectAsState()
    val voiceElapsedMs by viewModel.voiceElapsedMs.collectAsState()
    val voicePlayState by viewModel.voicePlayState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isPartnerLeft by viewModel.isPartnerLeft.collectAsState()
    val myReadMessageIds by viewModel.myReadMessageIds.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()

    // If the group chat was deleted by the admin, kick this user back to chat list
    LaunchedEffect(isChatDeleted) {
        if (isChatDeleted) onBack()
    }

    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Закрываем эмодзи-панель как только система показывает свою клавиатуру
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) { if (imeVisible) showEmojiPicker = false }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as SvoiApp
    val globalVoiceState by app.globalVoicePlayer.state.collectAsState()

    // Draft: load saved draft when opening chat
    LaunchedEffect(chatId) {
        val saved = app.draftManager.getDraft(chatId)
        if (saved.isNotBlank()) {
            inputValue = TextFieldValue(saved, selection = TextRange(saved.length))
        }
    }

    // Draft: auto-save after 1.5s of inactivity while typing
    LaunchedEffect(Unit) {
        snapshotFlow { inputValue.text }
            .distinctUntilChanged()
            .collect { text ->
                delay(1_500L)
                if (text.isBlank()) app.draftManager.clearDraft(chatId)
                else app.draftManager.saveDraft(chatId, text)
            }
    }

    // Draft: save immediately when leaving the chat
    val latestInputText by rememberUpdatedState(inputValue.text)
    DisposableEffect(chatId) {
        onDispose {
            if (latestInputText.isBlank()) app.draftManager.clearDraft(chatId)
            else app.draftManager.saveDraft(chatId, latestInputText)
        }
    }

    // Chat reveal: invisible until scrolled to position, then fade-in
    var chatReady by remember { mutableStateOf(false) }
    val chatAlpha by animateFloatAsState(
        targetValue = if (chatReady) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "chatReveal"
    )
    LaunchedEffect(isLoading) { if (isLoading) chatReady = false }

    // True when opening chat with a target message — suppress normal reveal until positioned.
    var isRevealingToMessage by remember { mutableStateOf(initialMessageId != null) }
    // True when the search loop is loading history for a pinned/searched message.
    var isScrollSearchLoading by remember { mutableStateOf(false) }

    // When opened from global search: scroll to the matched message once the chat is ready.
    // isRevealingToMessage keeps the overlay visible until we're positioned at the target.
    LaunchedEffect(initialMessageId) {
        if (initialMessageId == null) return@LaunchedEffect
        withTimeoutOrNull(10_000L) {
            viewModel.messages.first { it.isNotEmpty() }  // data arrived
            viewModel.isLoading.first { !it }             // loading done
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        } ?: run {
            // Timeout — give up and reveal normally
            isRevealingToMessage = false
            return@LaunchedEffect
        }
        viewModel.scrollToMessage(initialMessageId)
    }

    // Keep screen on while recording voice (prevents phone sleep during locked recording)
    val isRecordingVoice = voiceRecordState is VoiceRecordState.Recording
    DisposableEffect(isRecordingVoice) {
        val window = (context as? android.app.Activity)?.window
        if (isRecordingVoice) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Microphone permission
    var micPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermissionGranted = granted
    }
    // Voice recording drag state
    var voiceDragOffsetX by remember { mutableFloatStateOf(0f) }
    var voiceDragOffsetY by remember { mutableFloatStateOf(0f) }

    // Bottom sheet state
    var selectedMessage by remember { mutableStateOf<MessageUiItem?>(null) }
    var lightboxState by remember { mutableStateOf<LightboxState?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Forward picker state
    var showForwardPicker by remember { mutableStateOf(false) }
    var pendingForwardMessageId by remember { mutableStateOf<String?>(null) }

    // Video playback state
    val exoPlayer = rememberChatExoPlayer()
    var activeVideoUrl by remember { mutableStateOf<String?>(null) }
    var isVideoMuted by remember { mutableStateOf(true) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }
    // Cached aspect ratios per URL (populated on first play when real size is known)
    val videoAspectRatios = remember { mutableStateMapOf<String, Float>() }

    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.roundToPx()
    }

    var showChatMenu by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }

    // Exit selection mode with back button
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    // Clear staged media/file with back button
    BackHandler(enabled = stagedMedia.isNotEmpty()) {
        viewModel.clearStagedMedia()
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

    val currentDisplayEntries by rememberUpdatedState(displayEntries)

    // «Зона около низа»: если последний видимый элемент не дальше 3 позиций от конца —
    // пользователь считается «у низа» и новые сообщения скроллят его вниз.
    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && (total - lastVisible) <= 3
        }
    }

    val isNearTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 2 }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (isNearBottom) {
            val last = currentDisplayEntries.size - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }

    // Флаг: начальный авто-скролл (на вход в чат) завершён.
    // markAsRead() нельзя вызывать до этого момента — иначе авто-скролл к разделителю
    // непрочитанных может оказаться «у дна» и сразу сбросить счётчик.
    var initialScrollDone by remember { mutableStateOf(false) }

    // Переменные для восстановления позиции скролла после prepend старых сообщений
    var loadMoreSavedIndex by remember { mutableIntStateOf(0) }
    var loadMoreSavedOffset by remember { mutableIntStateOf(0) }
    var loadMoreCountBefore by remember { mutableIntStateOf(0) }
    var pendingScrollRestore by remember { mutableStateOf(false) }

    // Поиск закреплённого сообщения через подгрузку истории
    var pendingScrollToId by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableIntStateOf(0) }

    // Помечаем прочитанными только когда пользователь САМ оказался у низа
    // (после завершения начального авто-скролла)
    val shouldMarkRead by remember {
        derivedStateOf { initialScrollDone && !listState.canScrollForward && listState.layoutInfo.totalItemsCount > 0 }
    }
    LaunchedEffect(shouldMarkRead, messages.size) {
        if (shouldMarkRead) viewModel.markAsRead()
    }

    // Когда загружается OG-превью ссылки, карточка расширяет сообщение «вниз».
    // Если пользователь был у низа — скроллим вниз вслед за расширением.
    val ogCacheSize = viewModel.ogCache.size
    LaunchedEffect(ogCacheSize) {
        if (ogCacheSize == 0 || !initialScrollDone) return@LaunchedEffect
        if (!isNearBottom) return@LaunchedEffect
        delay(150) // ждём пока LazyColumn пересчитает layout после появления карточки
        val last = currentDisplayEntries.size - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    // Восстанавливаем позицию скролла после того как старые сообщения prepend'нуты вверх.
    // Восстанавливаем позицию только при обычной подгрузке (не при поиске закреплённого).
    LaunchedEffect(messages.size) {
        if (pendingScrollRestore && messages.size > loadMoreCountBefore && pendingScrollToId == null) {
            val prepended = messages.size - loadMoreCountBefore
            listState.scrollToItem(
                index = (loadMoreSavedIndex + prepended).coerceAtLeast(0),
                scrollOffset = loadMoreSavedOffset
            )
            pendingScrollRestore = false
        }
    }

    // Поиск закреплённого сообщения: грузим историю батчами пока не найдём нужный ID.
    // Ключ — searchTrigger, а не pendingScrollToId, иначе обнуление pendingScrollToId
    // отменит coroutine до выполнения smoothScrollToItem.
    LaunchedEffect(searchTrigger) {
        val targetId = pendingScrollToId ?: return@LaunchedEffect
        // Show loading overlay while we're searching through history
        isScrollSearchLoading = true
        try {
            while (pendingScrollToId == targetId) {
                val idx = currentDisplayEntries
                    .indexOfFirst { it is ChatEntry.Msg && it.item.message.id == targetId }
                when {
                    idx >= 0 -> {
                        // Found — reset state first, then position and reveal
                        pendingScrollRestore = false
                        pendingScrollToId = null
                        isScrollSearchLoading = false
                        viewModel.refreshHighlight(targetId)  // restart timer — message now visible
                        if (isRevealingToMessage) {
                            // Chat was hidden — snap instantly then reveal with fade-in
                            listState.scrollToItem(idx, scrollOffset = -(screenHeightPx / 3))
                            chatReady = true
                            isRevealingToMessage = false
                        } else {
                            // Chat was already visible — smooth animated scroll
                            listState.smoothScrollToItem(idx, scrollOffset = -(screenHeightPx / 3))
                        }
                        return@LaunchedEffect
                    }
                    !hasMoreMessages -> {
                        pendingScrollToId = null
                        isScrollSearchLoading = false
                        if (isRevealingToMessage) {
                            chatReady = true
                            isRevealingToMessage = false
                        }
                        return@LaunchedEffect
                    }
                    !isLoadingMore -> {
                        // Сохраняем позицию и грузим следующую порцию
                        loadMoreSavedIndex = listState.firstVisibleItemIndex
                        loadMoreSavedOffset = listState.firstVisibleItemScrollOffset
                        loadMoreCountBefore = messages.size
                        pendingScrollRestore = true
                        viewModel.loadMoreMessages()
                        // Ждём пока эта порция загрузится
                        snapshotFlow { messages.size }.first { it > loadMoreCountBefore }
                        delay(50) // даём layout пересчитать индексы
                    }
                    else -> {
                        // Загрузка уже идёт — ждём завершения
                        snapshotFlow { isLoadingMore }.first { !it }
                    }
                }
            }
        } finally {
            // Safety: always clear the overlay if coroutine is cancelled
            isScrollSearchLoading = false
        }
    }

    // Триггер бесконечного скролла вверх: когда пользователь у верха списка,
    // загружаем следующую порцию старых сообщений.
    LaunchedEffect(isNearTop) {
        if (!isNearTop || !initialScrollDone) return@LaunchedEffect
        if (!hasMoreMessages || isLoadingMore) return@LaunchedEffect
        loadMoreSavedIndex = listState.firstVisibleItemIndex
        loadMoreSavedOffset = listState.firstVisibleItemScrollOffset
        loadMoreCountBefore = messages.size
        pendingScrollRestore = true
        viewModel.loadMoreMessages()
    }

    // When keyboard opens — scroll to bottom so latest messages are visible.
    // rememberUpdatedState wraps IME height in a snapshot-observable State so snapshotFlow
    // can reliably detect every keyboard open event, no matter how many times.
    val imeBottomState = rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current))
    LaunchedEffect("imeScroll") {
        snapshotFlow { imeBottomState.value > 0 }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                delay(200)
                val last = currentDisplayEntries.size - 1
                if (last >= 0) listState.animateScrollToItem(last)
            }
    }

    // Auto-play: when scroll stops, find the bottommost 50%-visible video and play it
    val currentAutoPlay by rememberUpdatedState(autoPlayVideos)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (!currentAutoPlay) {
                    activeVideoUrl = null
                    exoPlayer.pause()
                    return@collect
                }
                val layoutInfo = listState.layoutInfo
                val viewportH = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val firstVideoUrl = layoutInfo.visibleItemsInfo.asReversed().firstNotNullOfOrNull { info ->
                    val e = currentDisplayEntries.getOrNull(info.index) as? ChatEntry.Msg
                        ?: return@firstNotNullOfOrNull null
                    if (e.item.message.type != "video") return@firstNotNullOfOrNull null
                    val visible = minOf(info.offset + info.size, viewportH) - maxOf(info.offset, 0)
                    if (visible.toFloat() / info.size >= 0.5f) e.item.message.fileUrl else null
                }
                activeVideoUrl = firstVideoUrl
                if (firstVideoUrl == null) exoPlayer.pause()
            }
    }

    // Fast path: мгновенный snap к низу кэша — пользователь не видит верх списка.
    // initialScrollDone НЕ ставится здесь, чтобы markAsRead не сработал раньше времени.
    LaunchedEffect(snapToBottomEvent) {
        if (snapToBottomEvent == 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        val last = currentDisplayEntries.size - 1
        if (last >= 0) listState.scrollToItem(last)
    }

    // Финальный скролл к разделителю непрочитанных (или к низу) после загрузки с сервера
    LaunchedEffect(scrollToBottomEvent) {
        if (scrollToBottomEvent == 0) return@LaunchedEffect
        // Empty chat — no items to wait for or scroll to, reveal immediately
        if (currentDisplayEntries.isNotEmpty()) {
            // Wait until the LazyColumn has actually laid out items (max 2s fallback)
            withTimeoutOrNull(2_000L) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { it > 0 }
            }
            val entries = currentDisplayEntries
            val unreadEntryIdx = entries.indexOfFirst { it is ChatEntry.UnreadDivider }
            val target = if (unreadEntryIdx >= 0) unreadEntryIdx else entries.size - 1
            if (target >= 0) {
                val offset = if (unreadEntryIdx >= 0) -(screenHeightPx / 2) else 0
                try {
                    listState.scrollToItem(target, scrollOffset = offset)
                } catch (_: Exception) { /* best-effort scroll — chat must be revealed regardless */ }
            }
        }
        // Reveal chat — unless we're waiting for a specific message to be positioned first
        if (!isRevealingToMessage) chatReady = true
        // After initial scroll, allow markAsRead to fire when user reaches the bottom
        initialScrollDone = true
    }

    // Scroll to absolute bottom when user sends their own message
    LaunchedEffect(scrollToOwnMessageEvent) {
        if (scrollToOwnMessageEvent == 0) return@LaunchedEffect
        val last = currentDisplayEntries.size - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    // Scroll to specific message (pinned banner, system message click, reply jump).
    // Если не загружено — запускает цикл подгрузки истории через searchTrigger.
    LaunchedEffect(scrollToMessageEvent) {
        val targetId = scrollToMessageEvent ?: return@LaunchedEffect
        val idx = currentDisplayEntries.indexOfFirst { it is ChatEntry.Msg && it.item.message.id == targetId }
        viewModel.clearScrollToMessageEvent()
        if (idx >= 0) {
            viewModel.refreshHighlight(targetId)  // restart timer — message is now visible
            if (isRevealingToMessage) {
                // Chat is still hidden — snap instantly then reveal
                listState.scrollToItem(idx, scrollOffset = -(screenHeightPx / 3))
                chatReady = true
                isRevealingToMessage = false
            } else {
                listState.smoothScrollToItem(idx, scrollOffset = -(screenHeightPx / 3))
            }
        } else if (hasMoreMessages) {
            pendingScrollToId = targetId
            searchTrigger++
        } else if (isRevealingToMessage) {
            // Message not found and no more history — reveal anyway
            chatReady = true
            isRevealingToMessage = false
        }
    }

    LaunchedEffect(editingMessage) {
        editingMessage?.let { inputValue = TextFieldValue(it.content ?: "") }
    }

    val cropBarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val cropOnBarColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val cropBgColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val cropAccentColor = MaterialTheme.colorScheme.primary.toArgb()
    var cropEditIndex by remember { mutableStateOf(-1) }
    val cropEditLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                viewModel.replaceStagedMedia(cropEditIndex, uri, context)
            }
        }
        cropEditIndex = -1
    }

    val mediaPicker = rememberLauncherForActivityResult(GetMultipleMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addStagedMedia(uris, context)
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
                                modifier = Modifier.clickable(
                                    enabled = (isGroup || otherUserId != null)
                                ) {
                                    if (isGroup) onGroupInfoClick(chatId)
                                    else otherUserId?.let { onUserClick(it) }
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = chatName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isMuted) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.NotificationsOff,
                                            contentDescription = "Уведомления отключены",
                                            modifier = Modifier.size(16.dp),
                                            tint = TextSecondary
                                        )
                                    }
                                }
                                val typingText = remember(typingUsers, isGroup) { typingIndicatorText(typingUsers, isGroup) }
                                val subtitleText: String? = when {
                                    typingText != null -> typingText
                                    !isOnline -> "Нет подключения"
                                    !isReachable -> "Нет доступа к серверам"
                                    isUpdating && memberCount == 0 && presenceText.isBlank() -> "Обновление..."
                                    isGroup && memberCount > 0 -> memberCountText(memberCount).let { base ->
                                        if (groupOnlineCount >= 1) "$base, ${groupOnlineCount + 1} в сети" else base
                                    }
                                    !isGroup && presenceText.isNotBlank() -> presenceText
                                    else -> null
                                }
                                val isStatusAnimated = !isOnline || !isReachable || (isUpdating && memberCount == 0 && presenceText.isBlank())
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
                    actions = {
                        if (!isSelectionMode) {
                            Box {
                                IconButton(onClick = { showChatMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                                }
                                DropdownMenu(
                                    expanded = showChatMenu,
                                    onDismissRequest = { showChatMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isMuted) "Включить уведомления" else "Отключить уведомления")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.toggleMute()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isGroup) "Выйти из группы" else "Удалить чат",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.ExitToApp,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            if (isGroup) showLeaveConfirmDialog = true
                                            else showDeleteChatDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                OfflineBanner(isOnline = isOnline, isReachable = isReachable, isUpdating = isUpdating)

                // Pinned message banner — slides in/out smoothly
                AnimatedVisibility(
                    visible = pinnedMessage != null,
                    enter = slideInVertically { -it } + fadeIn(tween(220)),
                    exit  = slideOutVertically { -it } + fadeOut(tween(180))
                ) {
                    pinnedMessage?.let { pinned ->
                        val contentText = when (pinnedContent?.type) {
                            "album" -> {
                                val count = pinnedContent?.photoUrls?.size ?: 0
                                val caption = pinnedContent?.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                "📷 ${if (count > 1) "$count фото" else "Фото"}$caption"
                            }
                            "photo" -> {
                                val caption = pinnedContent?.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                "📷 Фото$caption"
                            }
                            "file" -> "📎 ${pinnedContent?.fileName ?: "Файл"}"
                            "video" -> {
                                val caption = pinnedContent?.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                "🎬 Видео$caption"
                            }
                            "voice" -> "🎤 Голосовое сообщение"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
        ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Messages
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!isLoading && chatReady && messages.isEmpty()) {
                    Text(
                        text = "Начните общение сегодня",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isLoading) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = chatAlpha },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Спиннер загрузки истории (вверху списка)
                        if (isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.Center),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        // Метка «Начало переписки» когда вся история загружена
                        if (!hasMoreMessages && !isLoadingMore && messages.isNotEmpty()) {
                            item(key = "start_of_chat") {
                                Text(
                                    text = "Начало переписки",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(
                            displayEntries,
                            key = { entry ->
                                when (entry) {
                                    is ChatEntry.Msg -> entry.item.stableKey
                                    is ChatEntry.DateDivider -> "date_${entry.triggerMsgId}"
                                    ChatEntry.UnreadDivider -> "unread_divider"
                                }
                            }
                        ) { entry ->
                            val isNew = entry is ChatEntry.Msg &&
                                entry.item.message.id in animatingMessageIds

                            // Full-width tap zone: makes selection easier on short messages.
                            // Inner MessageItem clickables (photo, video, etc.) still win
                            // because Compose routes events to the innermost handler first.
                            val rowMessageId = (entry as? ChatEntry.Msg)
                                ?.takeIf { it.item.message.type != "system" }
                                ?.item?.message?.id
                            Box(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec = if (isSelectionMode) null else spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = null
                                    )
                                    .fillMaxWidth()
                                    .then(
                                        if (rowMessageId != null) Modifier.combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) viewModel.toggleSelection(rowMessageId)
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.toggleSelection(rowMessageId)
                                            }
                                        ) else Modifier
                                    )
                            ) {
                                NewMessageAnimation(isNew = isNew) {
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
                                                    uploadProgresses = if (entry.item.isPending) uploadProgresses else emptyList(),
                                                    modifier = Modifier,
                                                    activeVideoUrl = activeVideoUrl,
                                                    exoPlayer = exoPlayer,
                                                    isMuted = isVideoMuted,
                                                    videoAspectRatios = videoAspectRatios,
                                                    onLongClick = {
                                                        viewModel.toggleSelection(entry.item.message.id)
                                                    },
                                                    onTap = {
                                                        if (isSelectionMode) {
                                                            viewModel.toggleSelection(entry.item.message.id)
                                                        } else {
                                                            selectedMessage = entry.item
                                                        }
                                                    },
                                                    onReply = {
                                                        viewModel.setReplyTo(entry.item.message)
                                                    },
                                                    onPhotoClick = { url, albumUrls ->
                                                        lightboxState = LightboxState(albumUrls, albumUrls.indexOf(url).coerceAtLeast(0))
                                                    },
                                                    onUserClick = { userId -> onUserClick(userId) },
                                                    onVideoTap = { url ->
                                                        if (!isSelectionMode) {
                                                            exoPlayer.pause()
                                                            fullscreenVideoUrl = url
                                                        }
                                                    },
                                                    onVideoMuteToggle = { isVideoMuted = !isVideoMuted },
                                                    onVideoSizeDetected = { url, ratio ->
                                                        val prevRatio = videoAspectRatios[url] ?: (16f / 9f)
                                                        videoAspectRatios[url] = ratio
                                                        // Когда вертикальное видео начинает воспроизводиться,
                                                        // пузырёк анимируется из 16:9 в портрет (tween 300ms).
                                                        // Скроллим ПОСЛЕ анимации, иначе скролл уходит на
                                                        // "старое" дно до того, как пузырёк успел вырасти.
                                                        if (ratio < 1f && prevRatio >= 1f) {
                                                            scope.launch {
                                                                delay(350L) // ждём окончания анимации роста
                                                                val total = currentDisplayEntries.size
                                                                val lastVisible = listState.layoutInfo
                                                                    .visibleItemsInfo.lastOrNull()?.index ?: -1
                                                                if (total > 0 && lastVisible >= total - 4) {
                                                                    listState.animateScrollToItem(total - 1)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    voicePlayState = voicePlayState,
                                                    onVoicePlay = { msgId, url, dur -> viewModel.playVoice(msgId, url, dur) },
                                                    onVoicePause = { viewModel.pauseVoice() },
                                                    onVoiceResume = { viewModel.resumeVoice() },
                                                    onVoiceSeek = { viewModel.seekVoice(it) },
                                                    onReactionToggle = { msgId, emoji -> viewModel.toggleReaction(msgId, emoji) },
                                                    ogCache = viewModel.ogCache,
                                                    onFetchOg = viewModel::ensureOgFetched
                                                )
                                            }
                                        }
                                        is ChatEntry.DateDivider -> DateSeparator(date = entry.date)
                                        ChatEntry.UnreadDivider -> UnreadMessagesDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                // Scroll to bottom button
                // Показываем кнопку только если прокручено более ~200dp от конца списка.
                // Это убирает: (1) мигание при новом сообщении, (2) кнопку когда автоскролл
                // недобирает пару пикселей до конца.
                val density = LocalDensity.current
                val scrollHideThresholdPx = with(density) { 200.dp.toPx() }
                val rawCanScrollFar by remember(scrollHideThresholdPx) {
                    derivedStateOf {
                        if (!listState.canScrollForward) return@derivedStateOf false
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf false
                        val lastIndex = info.totalItemsCount - 1
                        if (lastVisible.index < lastIndex) return@derivedStateOf true
                        // Последний элемент частично за нижней границей вьюпорта
                        (lastVisible.offset + lastVisible.size) - info.viewportEndOffset > scrollHideThresholdPx
                    }
                }
                var showScrollToBottom by remember { mutableStateOf(false) }
                LaunchedEffect(rawCanScrollFar) {
                    if (rawCanScrollFar) {
                        delay(100) // пропускаем кратковременные layout-фреймы
                        showScrollToBottom = true
                    } else {
                        showScrollToBottom = false
                    }
                }
                // Входящие сообщения, которые ещё не помечены прочитанными.
                // Используем реальные read receipts, а не позиционный счётчик.
                val unreadBadgeCount = messages.count { !it.isOwn && it.message.id !in myReadMessageIds }
                if (showScrollToBottom && !isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch { listState.smoothScrollToItem(displayEntries.size - 1) }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Вниз",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (unreadBadgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadBadgeCount > 99) "99+" else unreadBadgeCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Mini-player overlay: floats under top bar, doesn't shift messages
                MiniPlayerOverlay(state = globalVoiceState, player = app.globalVoicePlayer)

                // Loading overlays — rendered last so they appear on top of the LazyColumn
                if (isLoading || !chatReady || isScrollSearchLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                    onDeleteForAll = { viewModel.deleteSelectedMessages(forEveryone = true) },
                    hasOwnMessages = messages.any { it.message.id in selectedMessageIds && it.isOwn }
                )
            } else if (isPartnerLeft) {
                // Personal chat: partner deleted the chat
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Пользователь удалил чат",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .navigationBarsPadding()
                ) {
                    // Thin separator above input bar
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    // Staged media preview
                    AnimatedVisibility(
                        visible = stagedMedia.isNotEmpty(),
                        enter = slideInVertically { it } + fadeIn(tween(180)),
                        exit  = slideOutVertically { it } + fadeOut(tween(140))
                    ) {
                        StagedMediaRow(
                            items = stagedMedia,
                            onRemove = { viewModel.removeStagedMedia(it) },
                            onEdit = { idx ->
                                val uri = stagedMedia.getOrNull(idx)?.uri ?: return@StagedMediaRow
                                cropEditIndex = idx
                                cropEditLauncher.launch(
                                    CropImageContractOptions(
                                        uri = uri,
                                        cropImageOptions = CropImageOptions(
                                            toolbarColor = cropBarColor,
                                            toolbarTitleColor = cropOnBarColor,
                                            toolbarBackButtonColor = cropOnBarColor,
                                            activityMenuIconColor = cropOnBarColor,
                                            activityMenuTextColor = cropOnBarColor,
                                            activityBackgroundColor = cropBgColor,
                                            borderCornerColor = cropAccentColor,
                                            outputCompressQuality = 95
                                        )
                                    )
                                )
                            }
                        )
                    }


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

                    // @Mention suggestions panel
                    if (isGroup && mentionSuggestions.isNotEmpty()) {
                        MentionSuggestionsPanel(
                            suggestions = mentionSuggestions,
                            onSelect = { profile ->
                                // Find the active @query start position and replace it
                                val text = inputValue.text
                                val cursor = inputValue.selection.start
                                val beforeCursor = text.substring(0, cursor)
                                val lastAtIdx = beforeCursor.lastIndexOf('@')
                                if (lastAtIdx >= 0) {
                                    val name = profile.displayName ?: return@MentionSuggestionsPanel
                                    val afterCursor = text.substring(cursor)
                                    val newText = text.substring(0, lastAtIdx) + "@$name " + afterCursor
                                    val newCursor = lastAtIdx + name.length + 2 // +2 for @ and space
                                    inputValue = TextFieldValue(newText, TextRange(newCursor))
                                    viewModel.onInputTextChanged(newText)
                                    viewModel.onMentionQueryChanged(newText, newCursor)
                                }
                            }
                        )
                    }

                    // Reply / Edit preview
                    val previewMessage = replyTo ?: editingMessage
                    AnimatedVisibility(
                        visible = previewMessage != null,
                        enter = slideInVertically { it } + fadeIn(tween(180)),
                        exit  = slideOutVertically { it } + fadeOut(tween(140))
                    ) {
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
                                        text = when {
                                            msg.type == "album" -> {
                                                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                "📷 ${msg.photoUrls?.size ?: 0} фото$caption"
                                            }
                                            msg.type == "photo" -> {
                                                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                "📷 Фото$caption"
                                            }
                                            msg.type == "file" -> "📎 ${msg.fileName ?: "Файл"}"
                                            msg.type == "video" -> {
                                                val caption = msg.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                "🎬 Видео$caption"
                                            }
                                            msg.type == "voice" -> "🎤 Голосовое сообщение"
                                            else -> msg.content ?: "[медиа]"
                                        },
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
                    }

                    // Text input row — mic button always stays in composition during recording
                    val isRecording = voiceRecordState is VoiceRecordState.Recording
                    val isLocked = isRecording && (voiceRecordState as VoiceRecordState.Recording).isLocked
                    val density = LocalDensity.current
                    var showSendMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Left side: recording overlay OR normal inputs ───────
                        Box(modifier = Modifier.weight(1f)) {
                            if (isRecording) {
                                // Recording indicator
                                val elapsedSec = (voiceElapsedMs / 1000).toInt()
                                val timeStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                                val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                                val dotAlpha by infiniteTransition.animateFloat(
                                    initialValue = 1f, targetValue = 0.3f,
                                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                    label = "dot_alpha"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(10.dp).clip(CircleShape)
                                            .background(Color.Red.copy(alpha = dotAlpha))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (isLocked) {
                                        TextButton(onClick = { viewModel.cancelVoiceRecording() }) {
                                            Text("Отменить", color = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        val cancelFraction = (-voiceDragOffsetX / with(density) { 80.dp.toPx() }).coerceIn(0f, 1f)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.alpha(1f - cancelFraction)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowBack, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Сдвиньте, чтобы отменить",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                }
                            } else {
                                // Normal inputs: [ 📎 ] [ 😀 TextField ] right→ mic/send
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Attach button
                                    IconButton(
                                        onClick = { mediaPicker.launch(Unit) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = "Прикрепить",
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    // Text field with emoji button inside as leading icon
                                    TextField(
                                        value = inputValue,
                                        onValueChange = {
                                            inputValue = it
                                            viewModel.onInputTextChanged(it.text)
                                            viewModel.onMentionQueryChanged(it.text, it.selection.start)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(24.dp))
                                            .onFocusChanged {
                                                isTextFieldFocused = it.isFocused
                                                if (it.isFocused) showEmojiPicker = false
                                            },
                                        placeholder = { Text("Сообщение...") },
                                        maxLines = 5,
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    showEmojiPicker = !showEmojiPicker
                                                    if (showEmojiPicker) keyboardController?.hide()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.EmojiEmotions,
                                                    contentDescription = "Эмодзи",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // ── Right: Mic/Send — always in composition ─────────────
                        // When locked → Send (tap to send voice)
                        // When has text/media and not recording → Send (tap to send message)
                        // Otherwise → Mic (hold-to-record gesture)
                        val hasContent = inputValue.text.isNotBlank() || stagedMedia.isNotEmpty()
                        val showSend = isLocked || (!isRecording && hasContent)
                        Box {
                        AnimatedContent(
                            targetState = showSend,
                            transitionSpec = {
                                (scaleIn(tween(160)) + fadeIn(tween(160))) togetherWith
                                (scaleOut(tween(120)) + fadeOut(tween(120)))
                            },
                            label = "micSendToggle"
                        ) { isSend ->
                            if (isSend) {
                                // Visual only — gestures handled by overlay below
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Отправить",
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                // Mic button — stays in composition while recording (non-locked)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isRecording) Color.Red
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        .pointerInput(micPermissionGranted) {
                                            if (!micPermissionGranted) return@pointerInput
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                viewModel.startVoiceRecording()
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                var cancelled = false
                                                var locked = false
                                                val cancelThreshold = 80.dp.toPx()
                                                val lockThreshold = 70.dp.toPx()
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) {
                                                        if (!cancelled && !locked) viewModel.sendVoiceRecording(context)
                                                        voiceDragOffsetX = 0f; voiceDragOffsetY = 0f
                                                        break
                                                    }
                                                    val offset = change.position - down.position
                                                    voiceDragOffsetX = offset.x
                                                    voiceDragOffsetY = offset.y
                                                    if (!locked && offset.x < -cancelThreshold) {
                                                        viewModel.cancelVoiceRecording()
                                                        cancelled = true
                                                        voiceDragOffsetX = 0f; voiceDragOffsetY = 0f
                                                        break
                                                    }
                                                    if (!locked && offset.y < -lockThreshold) {
                                                        viewModel.lockRecording()
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        locked = true
                                                        voiceDragOffsetX = 0f; voiceDragOffsetY = 0f
                                                    }
                                                    change.consume()
                                                }
                                            }
                                        }
                                        .clickable(enabled = !micPermissionGranted && !isRecording) {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Голосовое",
                                        tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                        // Gesture overlay — outside AnimatedContent so detectTapGestures works.
                        // Popup(focusable=false) → keyboard never hides on long press.
                        if (showSend) {
                            val onTapSend by rememberUpdatedState<() -> Unit> {
                                if (isLocked) {
                                    viewModel.sendVoiceRecording(context)
                                } else {
                                    val text = inputValue.text
                                    val media = stagedMedia
                                    inputValue = TextFieldValue("")
                                    viewModel.onInputTextChanged("")
                                    app.draftManager.clearDraft(chatId)
                                    if (media.isNotEmpty()) viewModel.sendWithAttachments(text, media, context)
                                    else viewModel.sendText(text)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { onTapSend() },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showSendMenu = true
                                            }
                                        )
                                    }
                            ) {
                                if (showSendMenu) {
                                    val popupPositionProvider = remember {
                                        object : PopupPositionProvider {
                                            override fun calculatePosition(
                                                anchorBounds: IntRect,
                                                windowSize: IntSize,
                                                layoutDirection: LayoutDirection,
                                                popupContentSize: IntSize
                                            ): IntOffset {
                                                val gap = with(density) { 8.dp.roundToPx() }
                                                return IntOffset(
                                                    x = (anchorBounds.right - popupContentSize.width)
                                                        .coerceAtLeast(with(density) { 8.dp.roundToPx() }),
                                                    y = anchorBounds.top - popupContentSize.height - gap
                                                )
                                            }
                                        }
                                    }
                                    Popup(
                                        popupPositionProvider = popupPositionProvider,
                                        onDismissRequest = { showSendMenu = false },
                                        properties = PopupProperties(
                                            focusable = false,
                                            dismissOnClickOutside = true
                                        )
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            shadowElevation = 8.dp,
                                            tonalElevation = 2.dp,
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clickable {
                                                        showSendMenu = false
                                                        if (isLocked) {
                                                            viewModel.sendVoiceRecording(context, silent = true)
                                                        } else {
                                                            val text = inputValue.text
                                                            val media = stagedMedia
                                                            inputValue = TextFieldValue("")
                                                            viewModel.onInputTextChanged("")
                                                            if (media.isNotEmpty()) viewModel.sendWithAttachments(text, media, context, silent = true)
                                                            else viewModel.sendText(text, silent = true)
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.NotificationsOff,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    "Отправить без звука",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } // close outer Box
                    }
                }
            }
        }
        // Error overlay — floats over content without shifting layout
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
        }
    }

    // ── Fullscreen video player ───────────────────────────────────────────────
    fullscreenVideoUrl?.let { url ->
        FullscreenVideoPlayer(
            url = url,
            onDismiss = {
                fullscreenVideoUrl = null
                if (activeVideoUrl != null) exoPlayer.play()
            }
        )
    }

    // ── Image Lightbox ──────────────────────────────────────────────────────────
    lightboxState?.let { ls ->
        val ctx = LocalContext.current
        ImageLightbox(
            state = ls,
            onDismiss = { lightboxState = null },
            onDownload = { url ->
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
                        "album" -> {
                            val caption = selected.message.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                            "📷 ${selected.message.photoUrls?.size ?: 0} фото$caption"
                        }
                        "photo" -> {
                            val caption = selected.message.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                            "📷 Фото$caption"
                        }
                        "file" -> "📎 ${selected.message.fileName ?: "Файл"}"
                        "video" -> {
                            val caption = selected.message.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                            "🎬 Видео$caption"
                        }
                        "voice" -> "🎤 Голосовое (${selected.message.duration?.toVoiceDuration() ?: "0:00"})"
                        else -> selected.message.content ?: ""
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (selected.isFailed) {
                    // ── Failed message: only retry / cancel options ────────────
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BottomSheetAction(Icons.Default.Refresh, "Повторить отправку") {
                        if (selected.pendingMediaContext != null) {
                            viewModel.retryFailedMediaMessage(selected.message.id, context)
                        } else {
                            viewModel.retryFailedMessage(selected.message.id)
                        }
                        selectedMessage = null
                    }
                    BottomSheetAction(
                        Icons.Default.Close, "Отменить отправку",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        viewModel.cancelFailedMessage(selected.message.id)
                        selectedMessage = null
                    }
                } else {
                    // ── Normal message: reactions + full action set ────────────
                    val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👎")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val myReactions = selected.reactions
                            .filter { it.hasMyReaction }
                            .map { it.emoji }
                            .toSet()
                        quickEmojis.forEach { emoji ->
                            val isActive = emoji in myReactions
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.toggleReaction(selected.message.id, emoji)
                                        selectedMessage = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }

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

    // ── Диалог: выход из группы ──────────────────────────────────────────────
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Выйти из группы?") },
            text = { Text("Вы покинете группу. Сообщения сохранятся для остальных участников.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmDialog = false
                        viewModel.leaveGroup(onLeft = onBack)
                    }
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ── Диалог: удаление личного чата ────────────────────────────────────────
    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = { Text("Удалить чат?") },
            text = { Text("Чат исчезнет из вашего списка. У собеседника останется история сообщений, но он не сможет писать вам в этот чат.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatDialog = false
                        viewModel.deletePersonalChat(onDeleted = onBack)
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) { Text("Отмена") }
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

// ── Mime type to emoji helper ─────────────────────────────────────────────────

private fun mimeTypeToEmoji(mimeType: String?, fileName: String?): String {
    val ext = fileName?.substringAfterLast('.')?.lowercase()
    return when {
        mimeType?.startsWith("video/") == true -> "🎬"
        mimeType?.startsWith("audio/") == true -> "🎵"
        mimeType?.startsWith("image/") == true -> "🖼️"
        mimeType == "application/pdf" -> "📄"
        ext == "pdf" -> "📄"
        mimeType in listOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") -> "📝"
        ext in listOf("doc", "docx") -> "📝"
        mimeType in listOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation") -> "📊"
        ext in listOf("ppt", "pptx") -> "📊"
        mimeType in listOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") -> "📈"
        ext in listOf("xls", "xlsx", "csv") -> "📈"
        mimeType == "application/vnd.android.package-archive" || ext == "apk" -> "📦"
        mimeType in listOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed") -> "🗜️"
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> "🗜️"
        mimeType?.startsWith("text/") == true || ext in listOf("txt", "md", "log") -> "📃"
        else -> "📎"
    }
}

// ── Staged media preview row ──────────────────────────────────────────────────

@Composable
private fun StagedMediaRow(
    items: List<StagedMedia>,
    onRemove: (Int) -> Unit,
    onEdit: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { idx, item ->
            Box(modifier = Modifier.size(80.dp)) {
                SubcomposeAsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        is AsyncImagePainter.State.Error -> Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Text(if (item.isVideo) "🎬" else "🖼️", fontSize = 24.sp) }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
                // Video overlay
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                // Remove button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                        .clickable { onRemove(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Удалить",
                        tint = Color.White, modifier = Modifier.size(14.dp))
                }
                // Edit (crop) button — only for photos
                if (!item.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.6f))
                            .clickable { onEdit(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = "Редактировать",
                            tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── Photo grid (single or album) ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    urls: List<String>,
    isPending: Boolean,
    uploadProgresses: List<Float>,
    isOwn: Boolean,
    isSelectionMode: Boolean,
    textColor: Color,
    onPhotoClick: (url: String, albumUrls: List<String>) -> Unit,
    onTap: () -> Unit,
    onLongClick: () -> Unit
) {
    val count = urls.size
    val maxVisible = 4
    val visibleUrls = urls.take(maxVisible)
    val extraCount = (count - maxVisible).coerceAtLeast(0)
    val gap = 2.dp

    // Helper: one photo cell
    @Composable
    fun PhotoCell(
        url: String,
        idx: Int,
        modifier: Modifier,
        showExtra: Boolean = false
    ) {
        val model: Any = if (url.startsWith("content://") || url.startsWith("file://"))
            Uri.parse(url) else url
        val progress = uploadProgresses.getOrNull(idx)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick = { onPhotoClick(url, urls) },
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = model,
                contentDescription = "Фото",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPending) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress != null && progress > 0f && progress < 1f) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White
                        )
                    }
                }
            }
            if (showExtra) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+$extraCount",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    when (count) {
        1 -> {
            // Single photo — adaptive aspect ratio
            val url = urls[0]
            val model: Any = if (url.startsWith("content://") || url.startsWith("file://"))
                Uri.parse(url) else url
            val progress = uploadProgresses.getOrNull(0)
            var imageRatio by remember(url) { mutableStateOf<Float?>(null) }
            var loadError by remember(url) { mutableStateOf(false) }
            val clampedRatio = imageRatio?.coerceIn(0.5f, 2.0f)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .widthIn(min = 120.dp, max = 260.dp)
                    .then(
                        if (clampedRatio != null)
                            Modifier.aspectRatio(clampedRatio)
                        else
                            Modifier.height(180.dp)
                    )
                    .combinedClickable(onClick = { onPhotoClick(url, urls) }, onLongClick = onLongClick)
            ) {
                AsyncImage(
                    model = model,
                    contentDescription = "Фото",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        loadError = false
                        val d = state.result.drawable
                        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                            imageRatio = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                        }
                    },
                    onError = { loadError = true }
                )
                if (imageRatio == null && !loadError) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = if (isOwn) Color.White.copy(0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (loadError) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Default.BrokenImage, null, tint = textColor.copy(0.5f), modifier = Modifier.size(32.dp))
                    }
                }
                if (isPending) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (progress != null && progress > 0f && progress < 1f) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = Color.White, trackColor = Color.White.copy(0.3f))
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = Color.White)
                        }
                    }
                }
            }
        }
        2 -> {
            // Two equal columns
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PhotoCell(visibleUrls[0], 0, Modifier.weight(1f).height(160.dp))
                PhotoCell(visibleUrls[1], 1, Modifier.weight(1f).height(160.dp))
            }
        }
        3 -> {
            // Big left cell + 2 stacked on right
            val totalH = 180.dp
            val cellH = (totalH - gap) / 2
            Row(
                modifier = Modifier.width(262.dp),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                PhotoCell(visibleUrls[0], 0, Modifier.width(158.dp).height(totalH))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    PhotoCell(visibleUrls[1], 1, Modifier.fillMaxWidth().height(cellH))
                    PhotoCell(visibleUrls[2], 2, Modifier.fillMaxWidth().height(cellH))
                }
            }
        }
        else -> {
            // 4+ photos: 2×2 grid, last cell shows "+N" if more
            val cellSize = 130.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    PhotoCell(visibleUrls[0], 0, Modifier.size(cellSize))
                    PhotoCell(visibleUrls[1], 1, Modifier.size(cellSize))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    PhotoCell(visibleUrls[2], 2, Modifier.size(cellSize))
                    val last = visibleUrls[3]
                    PhotoCell(last, 3, Modifier.size(cellSize), showExtra = extraCount > 0)
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
            shape = SvoiShapes.Chip,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            tonalElevation = 1.dp
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
    uploadProgresses: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
    activeVideoUrl: String? = null,
    exoPlayer: ExoPlayer? = null,
    isMuted: Boolean = true,
    videoAspectRatios: Map<String, Float> = emptyMap(),
    onLongClick: () -> Unit,
    onTap: () -> Unit = {},
    onReply: () -> Unit = {},
    onPhotoClick: (url: String, albumUrls: List<String>) -> Unit = { _, _ -> },
    onUserClick: (String) -> Unit = {},
    onVideoTap: (String) -> Unit = {},
    onVideoMuteToggle: () -> Unit = {},
    onVideoSizeDetected: (url: String, ratio: Float) -> Unit = { _, _ -> },
    voicePlayState: VoicePlayState? = null,
    onVoicePlay: (msgId: String, url: String, durationSec: Int) -> Unit = { _, _, _ -> },
    onVoicePause: () -> Unit = {},
    onVoiceResume: () -> Unit = {},
    onVoiceSeek: (Int) -> Unit = {},
    onReactionToggle: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    ogCache: Map<String, OgData> = emptyMap(),
    onFetchOg: (String) -> Unit = {}
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
    val bubbleColor = if (item.isOwn) MaterialTheme.colorScheme.primary else if (isDark) DarkBubbleOther else BubbleOther
    val textColor = if (item.isOwn) BubbleOwnText else if (isDark) DarkBubbleOtherText else BubbleOtherText
    val bubbleShape = if (item.isOwn) SvoiShapes.BubbleOwn else SvoiShapes.BubbleOther

    // Big-emoji mode: 1–3 emoji with no text, no reply, no forward → no bubble
    val isEmojiOnlyMsg = msg.type == "text" &&
        !msg.content.isNullOrBlank() &&
        item.replyToMessage == null &&
        item.forwardedFromProfile == null &&
        isEmojiOnly(msg.content)

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
                            if (swipeOffset.value <= -swipeThresholdPx) onReply()
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
                            // All messages: swipe left (negative) to reply
                            if (dragAmount < 0) {
                                val newOffset = (swipeOffset.value + dragAmount)
                                    .coerceIn(-swipeThresholdPx * 1.3f, 0f)
                                scope.launch { swipeOffset.snapTo(newOffset) }
                                if (newOffset <= -swipeThresholdPx && !replyTriggered.value) {
                                    replyTriggered.value = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 12.dp)
                        .size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = swipeOffsetDp),
                horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start
            ) {
                // Bubble row: Avatar + Surface — avatar aligns to bottom of bubble only
                Row(verticalAlignment = Alignment.Bottom) {
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
                if (isEmojiOnlyMsg) {
                    // ── Emoji-only: no bubble, just big emoji + timestamp ──────────────
                    Column(
                        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .combinedClickable(onClick = onTap, onLongClick = onLongClick)
                            .padding(horizontal = 4.dp)
                    ) {
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
                        Text(
                            text = msg.content ?: "",
                            fontSize = 48.sp,
                            lineHeight = 52.sp
                        )
                        Row(
                            modifier = Modifier.padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (msg.editedAt != null) {
                                Text(
                                    "изм. ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = msg.createdAt?.toMessageTime() ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            if (item.isPending && !item.isFailed) {
                                Spacer(Modifier.width(3.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (item.isFailed) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Не отправлено",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (item.isOwn) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = if (item.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (item.isRead) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                // ── Normal bubble ────────────────────────────────────────────────────
                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    tonalElevation = if (item.isOwn) 0.dp else 1.dp,
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
                                            text = when {
                                                reply.type == "video" -> {
                                                    val caption = reply.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                    "🎬 Видео$caption"
                                                }
                                                reply.type == "file" -> "📎 ${reply.fileName ?: "Файл"}"
                                                reply.type == "photo" -> {
                                                    val caption = reply.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                    "📷 Фото$caption"
                                                }
                                                reply.type == "album" -> {
                                                    val caption = reply.content?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                                                    "📷 ${reply.photoUrls?.size ?: 0} фото$caption"
                                                }
                                                reply.type == "voice" -> "🎤 Голосовое"
                                                else -> reply.content ?: "[медиа]"
                                            },
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
                            "photo", "album" -> {
                                val photos: List<String> = when {
                                    item.isPending || item.isFailed -> item.pendingLocalUris
                                    msg.type == "album" -> msg.photoUrls ?: emptyList()
                                    msg.fileUrl != null -> listOf(msg.fileUrl)
                                    else -> emptyList()
                                }
                                if (photos.isNotEmpty()) {
                                    PhotoGrid(
                                        urls = photos,
                                        isPending = item.isPending,
                                        uploadProgresses = uploadProgresses,
                                        isOwn = item.isOwn,
                                        isSelectionMode = isSelectionMode,
                                        textColor = textColor,
                                        onPhotoClick = { url, albumUrls ->
                                            if (!isSelectionMode) onPhotoClick(url, albumUrls) else onTap()
                                        },
                                        onTap = onTap,
                                        onLongClick = onLongClick
                                    )
                                    // Caption text (if present)
                                    if (!msg.content.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        LinkText(
                                            text = msg.content,
                                            color = textColor,
                                            isOwn = item.isOwn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            onOtherTap = onTap
                                        )
                                    }
                                } else {
                                    Text("📷 Фото", color = textColor)
                                }
                            }
                            "video" -> {
                                if (msg.fileUrl != null) {
                                    if (exoPlayer != null) {
                                        InlineVideoPlayer(
                                            url = msg.fileUrl,
                                            isActive = activeVideoUrl == msg.fileUrl,
                                            exoPlayer = exoPlayer,
                                            isMuted = isMuted,
                                            aspectRatio = videoAspectRatios[msg.fileUrl] ?: (16f / 9f),
                                            onTap = { onVideoTap(msg.fileUrl) },
                                            onMuteToggle = onVideoMuteToggle,
                                            onVideoSizeDetected = { ratio -> onVideoSizeDetected(msg.fileUrl, ratio) }
                                        )
                                    }
                                } else {
                                    // Pending or failed — show placeholder matching video proportions
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.isPending) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(36.dp),
                                                strokeWidth = 3.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                }
                                if (!msg.content.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    LinkText(
                                        text = msg.content,
                                        color = textColor,
                                        isOwn = item.isOwn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        onOtherTap = onTap
                                    )
                                }
                            }
                            "file" -> {
                                val ctx = LocalContext.current
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            msg.fileUrl?.let { url ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    val mime = msg.mimeType ?: "*/*"
                                                    setDataAndType(Uri.parse(url), mime)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try { ctx.startActivity(intent) } catch (_: Exception) { }
                                            }
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mimeTypeToEmoji(msg.mimeType, msg.fileName),
                                            fontSize = 22.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = msg.fileName ?: "Файл",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                            "voice" -> {
                                if (item.isPending) {
                                    // Upload in progress — show spinner + duration
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(180.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = textColor.copy(0.7f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = textColor.copy(0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = (msg.duration ?: 0).toVoiceDuration(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor.copy(0.7f)
                                        )
                                    }
                                } else {
                                    msg.fileUrl?.let { url ->
                                        VoiceMessageBubble(
                                            messageId = msg.id,
                                            url = url,
                                            durationSec = msg.duration ?: 0,
                                            isOwn = item.isOwn,
                                            isListened = item.isListened,
                                            voicePlayState = voicePlayState,
                                            onPlay = { onVoicePlay(msg.id, url, msg.duration ?: 0) },
                                            onPause = onVoicePause,
                                            onResume = onVoiceResume,
                                            onSeek = onVoiceSeek
                                        )
                                    }
                                }
                            }
                            else -> {
                                LinkText(
                                    text = msg.content ?: "",
                                    color = textColor,
                                    isOwn = item.isOwn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    onOtherTap = onTap
                                )
                                // OG link preview
                                val firstUrl = remember(msg.content) {
                                    msg.content?.let { URL_REGEX.find(it)?.value }
                                }
                                LaunchedEffect(firstUrl) {
                                    firstUrl?.let { onFetchOg(it) }
                                }
                                val ogData = firstUrl?.let { ogCache[it] }
                                if (ogData != null) {
                                    OgPreviewCard(
                                        ogData = ogData,
                                        isOwn = item.isOwn,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
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
                            if (item.isPending && !item.isFailed) {
                                // Sending — spinner
                                Spacer(Modifier.width(3.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = textColor.copy(0.7f)
                                )
                            } else if (item.isFailed) {
                                // Failed — red error icon; tap message to retry/cancel
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Не отправлено",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (item.isOwn) {
                                Spacer(Modifier.width(3.dp))
                                val isVoice = msg.type == "voice"
                                Icon(
                                    imageVector = when {
                                        isVoice && item.isListened -> Icons.Default.Hearing
                                        item.isRead -> Icons.Default.DoneAll
                                        else -> Icons.Default.Check
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isVoice && item.isListened -> Color.White
                                        item.isRead -> Color.White
                                        else -> textColor.copy(0.7f)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                } // end else (normal bubble)
                } // close Row (Avatar + Surface)
                // Reactions row — below the bubble, outside the Row so avatar stays at bubble bottom
                if (item.reactions.isNotEmpty()) {
                    ReactionsRow(
                        reactions = item.reactions,
                        isOwn = item.isOwn,
                        hasAvatarSpace = !item.isOwn && isGroup,
                        onReactionClick = { emoji -> onReactionToggle(item.message.id, emoji) }
                    )
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

// ── Кликабельные ссылки в тексте ────────────────────────────────────────────

private val URL_REGEX = Regex(
    "(https?://[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+)"
)

/**
 * Returns true when [text] consists of 1–3 emoji and nothing else.
 * Handles surrogate pairs, ZWJ sequences, skin-tone modifiers, and flag pairs.
 */
private fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    var emojiCount = 0
    var i = 0
    while (i < trimmed.length) {
        val cp = Character.codePointAt(trimmed, i)
        val charCount = Character.charCount(cp)
        when {
            // Non-visible modifiers — skip without counting
            cp == 0x200D ||                   // Zero Width Joiner
            cp == 0xFE0F ||                   // Variation Selector-16
            cp == 0x20E3 ||                   // Combining Enclosing Keycap
            cp in 0x1F3FB..0x1F3FF -> { /* skin-tone modifiers */ }

            // Flags: two consecutive regional indicators = one flag
            cp in 0x1F1E6..0x1F1FF -> {
                val ni = i + charCount
                if (ni < trimmed.length) {
                    val next = Character.codePointAt(trimmed, ni)
                    if (next in 0x1F1E6..0x1F1FF) {
                        emojiCount++
                        i = ni + Character.charCount(next)
                        continue
                    }
                }
                return false // lone regional indicator
            }

            // Keycap sequences: 0-9 / # / * + FE0F + 20E3
            cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A -> {
                val ni = i + charCount
                if (ni < trimmed.length &&
                    Character.codePointAt(trimmed, ni) == 0xFE0F) {
                    val ni2 = ni + 1
                    if (ni2 < trimmed.length &&
                        Character.codePointAt(trimmed, ni2) == 0x20E3) {
                        emojiCount++
                        i = ni2 + 1
                        continue
                    }
                }
                return false // bare digit / symbol
            }

            // All supplementary-plane emoji (U+1F000..U+1FFFF)
            cp in 0x1F000..0x1FFFF -> emojiCount++

            // Common BMP symbol/emoji blocks
            cp in 0x2300..0x23FF ||   // Misc Technical (⌚⌛⏰…)
            cp in 0x2600..0x26FF ||   // Misc Symbols (☀☁❤…)
            cp in 0x2700..0x27BF ||   // Dingbats (✂✈✨…)
            cp in 0x2B00..0x2BFF ||   // Misc Symbols and Arrows
            cp == 0x00A9 ||           // ©
            cp == 0x00AE ||           // ®
            cp == 0x2122 -> emojiCount++ // ™

            else -> return false // regular text character
        }
        i += charCount
    }
    return emojiCount in 1..3
}

@Composable
private fun LinkText(
    text: String,
    color: Color,
    isOwn: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onOtherTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val linkColor = if (isOwn) Color.White else primary

    val matches = remember(text) { URL_REGEX.findAll(text).toList() }

    if (matches.isEmpty()) {
        Text(text = text, color = color, style = style, modifier = modifier)
        return
    }

    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            matches.forEach { match ->
                if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
                pushStringAnnotation("URL", match.value)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(match.value)
                }
                pop()
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            val urlAnnotation = annotated
                .getStringAnnotations("URL", offset, offset)
                .firstOrNull()
            if (urlAnnotation != null) {
                val raw = urlAnnotation.item
                val uri = if (raw.startsWith("www.")) Uri.parse("https://$raw") else Uri.parse(raw)
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
            } else {
                onOtherTap()
            }
        }
    )
}

// ── OG-превью ссылок ─────────────────────────────────────────────────────────

@Composable
private fun OgPreviewCard(
    ogData: OgData,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = if (isOwn) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
    val bgColor = if (isOwn) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ogData.url)))
                } catch (_: Exception) {}
            }
    ) {
        Row(modifier = Modifier.padding(0.dp)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 48.dp)
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).weight(1f)) {
                ogData.siteName?.takeIf { it.isNotBlank() }?.let { site ->
                    Text(
                        text = site,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ogData.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ogData.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOwn) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ogData.imageUrl?.takeIf { it.isNotBlank() }?.let { imgUrl ->
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                )
            }
        }
    }
}

private fun Int.toVoiceDuration(): String {
    val m = this / 60
    val s = this % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun VoiceMessageBubble(
    messageId: String,
    url: String,
    durationSec: Int,
    isOwn: Boolean,
    isListened: Boolean,
    voicePlayState: VoicePlayState?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Int) -> Unit
) {
    val isThisPlaying = voicePlayState?.messageId == messageId && voicePlayState.isPlaying
    val isThisActive = voicePlayState?.messageId == messageId
    val positionMs = if (isThisActive) voicePlayState!!.positionMs else 0
    val durationMs = if (isThisActive && voicePlayState!!.durationMs > 0) voicePlayState.durationMs
                     else (durationSec * 1000).coerceAtLeast(1000)
    val progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val sliderActiveColor = if (isOwn) Color.White else MaterialTheme.colorScheme.primary
    val sliderInactiveColor = if (isOwn) Color.White.copy(0.4f) else MaterialTheme.colorScheme.onSurface.copy(0.2f)

    // Displayed time: current position if active, else total duration
    val displaySec = if (isThisActive) positionMs / 1000 else durationSec
    val timeStr = displaySec.toVoiceDuration()

    // Dot color: white on own (blue) bubble
    val dotColor = Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(220.dp)
    ) {
        // Play button wrapped in Box so we can overlay the "unlistened" dot badge
        Box(modifier = Modifier.size(40.dp)) {
            IconButton(
                onClick = {
                    when {
                        !isThisActive -> onPlay()
                        isThisPlaying -> onPause()
                        else -> onResume()
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "Пауза" else "Играть",
                    tint = if (isOwn) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            // Unlistened dot badge — visible on own messages until the recipient listens
            if (isOwn && !isListened) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = { onSeek((it * durationMs).toInt()) },
                colors = SliderDefaults.colors(
                    thumbColor = sliderActiveColor,
                    activeTrackColor = sliderActiveColor,
                    inactiveTrackColor = sliderInactiveColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOwn) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).offset(y = (-6).dp)
            )
        }
    }
}

@Composable
private fun ReactionsRow(
    reactions: List<ReactionGroup>,
    isOwn: Boolean,
    hasAvatarSpace: Boolean = false,
    onReactionClick: (emoji: String) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .padding(start = if (hasAvatarSpace) 32.dp else 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { group ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (group.hasMyReaction)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .defaultMinSize(minHeight = 30.dp)
                    .clickable { onReactionClick(group.emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(group.emoji, fontSize = 16.sp, lineHeight = 18.sp)
                    if (group.count > 1) {
                        Text(
                            "${group.count}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (group.hasMyReaction)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private sealed class ChatEntry {
    data class Msg(val item: MessageUiItem) : ChatEntry()
    data class DateDivider(val date: String, val triggerMsgId: String) : ChatEntry()
    object UnreadDivider : ChatEntry()
}

data class LightboxState(val urls: List<String>, val startIndex: Int = 0)

@Composable
private fun ImageLightbox(
    state: LightboxState,
    onDismiss: () -> Unit,
    onDownload: (url: String) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = state.startIndex,
        pageCount = { state.urls.size }
    )
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
            // Swipeable pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) { page ->
                val url = state.urls[page]
                val model: Any = if (url.startsWith("content://") || url.startsWith("file://"))
                    Uri.parse(url) else url
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = "Изображение",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
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
            }

            // Page indicator (only for albums)
            if (state.urls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${state.urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Top-right: download + close
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentUrl = state.urls.getOrElse(pagerState.currentPage) { state.urls.first() }
                IconButton(onClick = { onDownload(currentUrl) }) {
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

/**
 * Entrance animation for new incoming messages.
 *
 * New messages slide up from 60dp below their final position while fading in.
 * Existing messages are animated via [Modifier.animateItem] (placement spring).
 *
 * Uses [mutableFloatStateOf] so the initial values are set on first composition;
 * [LaunchedEffect(Unit)] fires once and drives the animation to target values.
 * This way the composable never "jumps" even if [isNew] becomes false later
 * (state is already at final values — no visual change on recomposition).
 */
@Composable
private fun NewMessageAnimation(
    isNew: Boolean,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val startOffsetPx = remember { with(density) { 60.dp.toPx() } }

    var targetOffset by remember { mutableFloatStateOf(if (isNew) startOffsetPx else 0f) }
    var targetAlpha  by remember { mutableFloatStateOf(if (isNew) 0f else 1f) }

    val animOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.65f,   // gentle overshoot for a lively feel
            stiffness = 320f        // ~medium speed — not too slow, not snappy
        ),
        label = "newMsgOffset"
    )
    val animAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "newMsgAlpha"
    )

    // One-shot: animate from start values to final on first composition of a new message.
    // For non-new messages this block is a no-op (targets are already 0f / 1f).
    LaunchedEffect(Unit) {
        if (isNew) {
            targetOffset = 0f
            targetAlpha  = 1f
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(0, animOffset.roundToInt()) }
            .alpha(animAlpha)
    ) {
        content()
    }
}

private fun typingIndicatorText(users: List<TypingInfo>, isGroup: Boolean): String? {
    if (users.isEmpty()) return null
    val uploading = users.filter { it.status == "uploading_media" }
    val typing = users.filter { it.status != "uploading_media" }
    return if (!isGroup) {
        when {
            uploading.isNotEmpty() -> "Загружает медиа..."
            else -> "Печатает..."
        }
    } else {
        when {
            uploading.isNotEmpty() && typing.isEmpty() -> when (uploading.size) {
                1 -> "${uploading[0].displayName} загружает медиа..."
                2 -> "${uploading[0].displayName} и ${uploading[1].displayName} загружают медиа..."
                else -> "${uploading[0].displayName} и ещё ${uploading.size - 1} загружают медиа..."
            }
            uploading.isEmpty() -> when (typing.size) {
                1 -> "${typing[0].displayName} печатает..."
                2 -> "${typing[0].displayName} и ${typing[1].displayName} печатают..."
                else -> "${typing[0].displayName}, ${typing[1].displayName} и ещё ${typing.size - 2} печатают..."
            }
            else -> {
                val all = uploading + typing
                when (all.size) {
                    2 -> "${all[0].displayName} и ${all[1].displayName} активны..."
                    else -> "${all[0].displayName} и ещё ${all.size - 1} активны..."
                }
            }
        }
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

/**
 * Mini-player overlay: extracted into a standalone @Composable so AnimatedVisibility
 * resolves to the generic overload (not ColumnScope.AnimatedVisibility, which the compiler
 * would incorrectly pick when called directly inside a Box that's nested inside a Column).
 */
@Composable
private fun MiniPlayerOverlay(state: GlobalVoiceState?, player: GlobalVoicePlayer) {
    var lastState by remember { mutableStateOf(state) }
    if (state != null) lastState = state
    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit  = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))
    ) {
        lastState?.let { vs ->
            GlobalVoiceMiniPlayer(
                state = vs,
                onPlayPause = { if (vs.isPlaying) player.pause() else player.resume() },
                onClose = { player.stop() }
            )
        }
    }
}

/** Mention suggestions panel shown above the input bar when user types @. */
@Composable
private fun MentionSuggestionsPanel(
    suggestions: List<Profile>,
    onSelect: (Profile) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            suggestions.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(profile) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        emoji = profile.emoji ?: "😊",
                        bgColor = profile.bgColor ?: "#5C6BC0",
                        size = 36.dp,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "@${profile.displayName ?: "Пользователь"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (suggestions.last() != profile) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
