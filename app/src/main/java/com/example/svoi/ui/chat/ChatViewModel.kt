package com.example.svoi.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.CacheManager.CachedChatInfo
import com.example.svoi.data.local.OutboxMessage
import com.example.svoi.data.model.Chat
import com.example.svoi.data.model.ChatListItem
import com.example.svoi.data.model.Message
import com.example.svoi.data.model.MessageUiItem
import com.example.svoi.data.model.PendingMediaContext
import com.example.svoi.data.model.PinnedMessage
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.ReactionGroup
import com.example.svoi.data.model.UserPresence
import com.example.svoi.data.model.isTrulyOnline
import com.example.svoi.data.repository.MessageAnchorWindowResult
import com.example.svoi.ui.voice.VoiceQueueItem
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class TypingInfo(val userId: String, val displayName: String, val status: String = "typing")
data class StagedMedia(val uri: Uri, val isVideo: Boolean)
data class PinnedScrollRequest(
    val requestId: Long,
    val messageId: String,
    val highlight: Boolean = true
)

@kotlinx.serialization.Serializable
data class OgData(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val url: String
)

sealed class VoiceRecordState {
    object Idle : VoiceRecordState()
    data class Recording(val isLocked: Boolean = false) : VoiceRecordState()
}

data class VoicePlayState(
    val messageId: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val downloadProgress: Float = -1f
)

private const val OG_FAILURE_TTL_MS = 30 * 60 * 1_000L
private const val VIDEO_UPLOAD_BUFFER_SIZE = 64 * 1024
private const val VIDEO_RESUMABLE_THRESHOLD_BYTES = 20L * 1024 * 1024
private const val CHAT_OPEN_LOG = "ChatOpen"
private const val SECONDARY_DUPLICATE_GUARD_MS = 2_000L

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SvoiApp
    private val messageRepo = app.messageRepository
    private val chatRepo = app.chatRepository
    private val userRepo = app.userRepository
    private val authRepo = app.authRepository
    private val cache = app.cacheManager

    private val _messages = MutableStateFlow<List<MessageUiItem>>(emptyList())
    val messages: StateFlow<List<MessageUiItem>> = _messages

    private val _chat = MutableStateFlow<Chat?>(null)
    val chat: StateFlow<Chat?> = _chat

    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup

    private val _chatName = MutableStateFlow("")
    val chatName: StateFlow<String> = _chatName

    private val _memberCount = MutableStateFlow(0)
    val memberCount: StateFlow<Int> = _memberCount

    private val _otherUserPresence = MutableStateFlow<UserPresence?>(null)
    val otherUserPresence: StateFlow<UserPresence?> = _otherUserPresence

    private val _otherUserProfile = MutableStateFlow<Profile?>(null)
    val otherUserProfile: StateFlow<Profile?> = _otherUserProfile

    /** Number of group members (excluding self) currently online. 0 = nobody or not a group. */
    private val _groupOnlineCount = MutableStateFlow(0)
    val groupOnlineCount: StateFlow<Int> = _groupOnlineCount

    private val _pinnedMessage = MutableStateFlow<PinnedMessage?>(null)
    val pinnedMessage: StateFlow<PinnedMessage?> = _pinnedMessage

    private val _pinnedMessageContent = MutableStateFlow<Message?>(null)
    val pinnedMessageContent: StateFlow<Message?> = _pinnedMessageContent

    private val _pinnedSenderProfile = MutableStateFlow<Profile?>(null)
    val pinnedSenderProfile: StateFlow<Profile?> = _pinnedSenderProfile

    private val _pinnedNavigationLoadingMessageId = MutableStateFlow<String?>(null)
    val pinnedNavigationLoadingMessageId: StateFlow<String?> = _pinnedNavigationLoadingMessageId

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId

    private val _scrollToMessageRequest = MutableStateFlow<PinnedScrollRequest?>(null)
    val scrollToMessageRequest: StateFlow<PinnedScrollRequest?> = _scrollToMessageRequest

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage

    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    /** Media (photos+videos) staged before sending */
    private val _stagedMedia = MutableStateFlow<List<StagedMedia>>(emptyList())
    val stagedMedia: StateFlow<List<StagedMedia>> = _stagedMedia

    /** Upload progress (0..1) for each staged item while uploading */
    private val _uploadProgresses = MutableStateFlow<List<Float>>(emptyList())
    val uploadProgresses: StateFlow<List<Float>> = _uploadProgresses

    private val _chatsForForward = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chatsForForward: StateFlow<List<ChatListItem>> = _chatsForForward

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // true while fetching from network (for subtitle "Обновление...")
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Fast path: немедленный snap к низу кэша, не разрешает markAsRead
    private val _snapToBottomEvent = MutableStateFlow(0)
    val snapToBottomEvent: StateFlow<Int> = _snapToBottomEvent

    // После вычисления firstUnreadIndex: скролл к разделителю/низу, разрешает markAsRead
    private val _scrollToBottomEvent = MutableStateFlow(0)
    val scrollToBottomEvent: StateFlow<Int> = _scrollToBottomEvent

    // Incremented when user sends own message — always scrolls to absolute bottom
    private val _scrollToOwnMessageEvent = MutableStateFlow(0)
    val scrollToOwnMessageEvent: StateFlow<Int> = _scrollToOwnMessageEvent

    // Index of first unread message for the separator (-1 = none)
    private val _firstUnreadIndex = MutableStateFlow(-1)
    val firstUnreadIndex: StateFlow<Int> = _firstUnreadIndex

    // Currently typing users (excluding self)
    private val _typingUsers = MutableStateFlow<List<TypingInfo>>(emptyList())
    val typingUsers: StateFlow<List<TypingInfo>> = _typingUsers

    val isOnline: StateFlow<Boolean> = app.networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val isReachable: StateFlow<Boolean> = app.supabaseChecker.isReachable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val shouldShowOfflineBanner: StateFlow<Boolean> = app.supabaseChecker.shouldShowOfflineBanner
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Cached at init() time so it stays stable even if SDK session expires while offline.
    // Never reset to "" during a session — once captured, it remains valid for the lifetime
    // of the ViewModel. This prevents messages from flip-flopping to "others'" side when
    // the Supabase SDK loses its in-memory session due to a connectivity problem.
    private var currentUserId: String = authRepo.currentUserId() ?: ""

    /** Becomes true when the group chat is deleted externally — screen must close */
    private val _isChatDeleted = MutableStateFlow(false)
    val isChatDeleted: StateFlow<Boolean> = _isChatDeleted

    /** Количество сообщений, которые пользователь видел в момент последнего markAsRead */
    private val _lastSeenMsgCount = MutableStateFlow(0)
    val lastSeenMsgCount: StateFlow<Int> = _lastSeenMsgCount

    /** IDs of incoming messages that the current user has already read */
    private val _myReadMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val myReadMessageIds: StateFlow<Set<String>> = _myReadMessageIds
    private val pendingReadReceiptIds = mutableSetOf<String>()
    private var readReceiptBaselineReady = false
    private var markAsReadAfterBaseline = false

    /** Mute state for this chat (notifications) */
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    /** True when partner left the personal chat (only current user remains) */
    private val _isPartnerLeft = MutableStateFlow(false)
    val isPartnerLeft: StateFlow<Boolean> = _isPartnerLeft

    /** IDs of messages currently playing their entrance animation */
    private val _animatingMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val animatingMessageIds: StateFlow<Set<String>> = _animatingMessageIds

    /** Non-self group members available for @-mention suggestions */
    private val _groupMemberProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val groupMemberProfiles: StateFlow<List<Profile>> = _groupMemberProfiles

    /** Currently shown mention suggestions (subset of groupMemberProfiles filtered by query) */
    private val _mentionSuggestions = MutableStateFlow<List<Profile>>(emptyList())
    val mentionSuggestions: StateFlow<List<Profile>> = _mentionSuggestions

    private var chatId: String = ""
    private var draftTargetUserId: String? = null
    private val draftMutex = Mutex()
    private val profileCache = mutableMapOf<String, Profile>()
    private var otherUserIdVal: String? = null
    private val _otherUserId = MutableStateFlow<String?>(null)
    val otherUserId: StateFlow<String?> = _otherUserId
    private var lastKnownMessageId: String? = null
    private var typingJob: Job? = null
    private val activeUploadCount = AtomicInteger(0)
    private var historyFrom: String? = null  // null = see all; timestamp = restricted to messages after join
    private val serverLoadApplyMutex = Mutex()
    private var lastAppliedServerMessageSignature: String? = null
    private var lastAppliedServerMessageAtMs: Long = 0L
    private var completedSecondaryMessageIds: List<String> = emptyList()
    private var completedSecondaryAtMs: Long = 0L
    private var inFlightSecondaryMessageIds: List<String>? = null
    private var messageInsertJob: Job? = null
    private var messageUpdateJob: Job? = null
    private var readReceiptJob: Job? = null
    private var voiceListenJob: Job? = null
    private var reactionInsertJob: Job? = null
    private var reactionDeleteJob: Job? = null
    private var pinnedNavigationJob: Job? = null
    private var pinnedScrollRequestId: Long = 0L
    private var typingPollingJob: Job? = null
    private var personalPresencePollingJob: Job? = null
    private var personalPresenceRealtimeJob: Job? = null
    private var activePresenceUserId: String? = null
    private var groupPresencePollingJob: Job? = null
    private var groupPresenceRealtimeJob: Job? = null
    private var chatDeletionWatchJob: Job? = null
    private var currentGroupPresenceMemberIds: List<String> = emptyList()

    init {
        viewModelScope.launch {
            app.isAppInForeground.collect { foreground ->
                if (foreground) {
                    Log.d("Realtime", "chat realtime resumed")
                    app.supabaseChecker.checkNow(force = true)
                    resumeForegroundNetworkJobs()
                } else {
                    pauseForegroundNetworkJobs()
                }
            }
        }
    }

    // ── Voice recording ───────────────────────────────────────────────────────
    private val voiceRecorder = VoiceRecorder(getApplication())
    private val _voiceRecordState = MutableStateFlow<VoiceRecordState>(VoiceRecordState.Idle)
    val voiceRecordState: StateFlow<VoiceRecordState> = _voiceRecordState
    private val _voiceElapsedMs = MutableStateFlow(0L)
    val voiceElapsedMs: StateFlow<Long> = _voiceElapsedMs
    private var voiceTimerJob: Job? = null

    // ── Open Graph preview cache ───────────────────────────────────────────────
    val ogCache = mutableStateMapOf<String, OgData>()
    private val ogAttempted = mutableSetOf<String>()
    private val ogFailedAtMs = mutableMapOf<String, Long>()

    // ── Pagination state ───────────────────────────────────────────────────────
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMoreMessages = MutableStateFlow(true)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages
    private fun pauseForegroundNetworkJobs() {
        messageInsertJob?.cancel()
        messageUpdateJob?.cancel()
        readReceiptJob?.cancel()
        voiceListenJob?.cancel()
        reactionInsertJob?.cancel()
        reactionDeleteJob?.cancel()
        typingPollingJob?.cancel()
        personalPresencePollingJob?.cancel()
        personalPresenceRealtimeJob?.cancel()
        activePresenceUserId = null
        groupPresencePollingJob?.cancel()
        groupPresenceRealtimeJob?.cancel()
        chatDeletionWatchJob?.cancel()
        messageInsertJob = null
        messageUpdateJob = null
        readReceiptJob = null
        voiceListenJob = null
        reactionInsertJob = null
        reactionDeleteJob = null
        typingPollingJob = null
        personalPresencePollingJob = null
        personalPresenceRealtimeJob = null
        groupPresencePollingJob = null
        groupPresenceRealtimeJob = null
        chatDeletionWatchJob = null
        typingJob?.cancel()
        _typingUsers.value = emptyList()
        Log.d("Typing", "polling stopped because app background")
        Log.d("Realtime", "chat realtime paused because app background")
    }

    private fun resumeForegroundNetworkJobs() {
        if (chatId.isEmpty()) return
        observeNewMessages()
        observeUpdatedMessages()
        observeReadReceipts()
        observeVoiceListens()
        observeReactions()
        startTypingPolling()
        startChatDeletionWatch()
        otherUserIdVal?.let { startPresencePolling(it) }
        if (currentGroupPresenceMemberIds.isNotEmpty()) startGroupPresencePolling(currentGroupPresenceMemberIds)
        if (app.supabaseChecker.isReachable.value) {
            viewModelScope.launch { loadMessages(scrollAfter = false) }
        }
    }

    private fun canUseForegroundNetwork(): Boolean =
        app.isAppInForeground.value && app.supabaseChecker.isReachable.value

    fun ensureOgFetched(url: String) {
        if (url in ogCache) return
        val failedAt = ogFailedAtMs[url]
        if (failedAt != null && System.currentTimeMillis() - failedAt < OG_FAILURE_TTL_MS) {
            Log.d("OgFetch", "skip cached failed url: $url")
            return
        }
        if (url in ogAttempted) return
        if (!app.isAppInForeground.value) return
        ogAttempted.add(url)
        viewModelScope.launch(Dispatchers.IO) {
            val data = fetchOgData(url)
            if (data != null) {
                withContext(Dispatchers.Main) { ogCache[url] = data }
                cache.saveOgData(ogCache.toMap())
            } else {
                ogFailedAtMs[url] = System.currentTimeMillis()
                ogAttempted.remove(url)
            }
        }
    }

    private suspend fun fetchOgData(url: String): OgData? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 2_500
            conn.readTimeout   = 2_500
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            try {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                val sb = StringBuilder()
                var totalChars = 0
                var line: String?
                while (reader.readLine().also { line = it } != null && totalChars < 65_536) {
                    sb.append(line)
                    totalChars += line!!.length
                    // Stop reading once we've passed the <head> section
                    if (line!!.contains("<body", ignoreCase = true) ||
                        line!!.contains("</head>", ignoreCase = true)) break
                }
                reader.close()
                parseOgData(sb.toString(), url)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.d("OgFetch", "Failed to fetch OG for $url: ${e.message}")
            null
        }
    }

    private fun parseOgData(html: String, sourceUrl: String): OgData? {
        fun meta(vararg properties: String): String? {
            for (prop in properties) {
                val escaped = Regex.escape(prop)
                // <meta property="og:title" content="..."> — attribute order varies
                val r1 = Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val r2 = Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']$escaped["']""", RegexOption.IGNORE_CASE)
                val result = (r1.find(html) ?: r2.find(html))?.groupValues?.get(1)
                if (!result.isNullOrBlank()) return decodeHtmlEntities(result.trim())
            }
            return null
        }

        val title = meta("og:title", "twitter:title")
            ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let { decodeHtmlEntities(it.trim()) }

        val description = meta("og:description", "twitter:description", "description")
        val rawImageUrl = meta("og:image", "twitter:image", "twitter:image:src")
        val siteName    = meta("og:site_name")

        if (title.isNullOrBlank() && rawImageUrl.isNullOrBlank()) return null

        val imageUrl = when {
            rawImageUrl.isNullOrBlank()      -> null
            rawImageUrl.startsWith("http")   -> rawImageUrl
            rawImageUrl.startsWith("//")     -> "https:$rawImageUrl"
            rawImageUrl.startsWith("/")      -> {
                val base = Uri.parse(sourceUrl)
                "${base.scheme}://${base.host}$rawImageUrl"
            }
            else -> rawImageUrl
        }

        return OgData(
            title       = title?.take(120),
            description = description?.take(240),
            imageUrl    = imageUrl,
            siteName    = siteName?.take(60),
            url         = sourceUrl
        )
    }

    private fun decodeHtmlEntities(text: String) = text
        .replace("&amp;",  "&")
        .replace("&lt;",   "<")
        .replace("&gt;",   ">")
        .replace("&quot;", "\"")
        .replace("&#39;",  "'")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }

    // ── Voice playback — delegated to GlobalVoicePlayer (survives navigation) ─
    val voicePlayState: StateFlow<VoicePlayState?> = app.globalVoicePlayer.state
        .map { gs -> gs?.let { VoicePlayState(it.messageId, it.isPlaying, it.positionMs, it.durationMs, it.downloadProgress) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun dismissChatNotification(chatId: String) {
        val nm = getApplication<SvoiApp>()
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(com.example.svoi.SvoiFirebaseMessagingService.notificationIdForChat(chatId))
        // Cancel group summary if no other chat notifications remain
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val remaining = nm.activeNotifications.filter {
                it.id != com.example.svoi.SvoiFirebaseMessagingService.SUMMARY_ID
            }
            if (remaining.isEmpty()) {
                nm.cancel(com.example.svoi.SvoiFirebaseMessagingService.SUMMARY_ID)
            }
        }
    }

    fun init(chatId: String) {
        pinnedNavigationJob?.cancel()
        pinnedNavigationJob = null
        _pinnedNavigationLoadingMessageId.value = null
        _scrollToMessageRequest.value = null
        if (this.chatId == chatId) {
            completedSecondaryMessageIds = emptyList()
            completedSecondaryAtMs = 0L
            if (canUseForegroundNetwork()) {
                viewModelScope.launch { loadMessages(scrollAfter = false) }
            }
            return
        }
        Log.d(CHAT_OPEN_LOG, "start chatId=$chatId")
        this.chatId = chatId
        lastAppliedServerMessageSignature = null
        lastAppliedServerMessageAtMs = 0L
        completedSecondaryMessageIds = emptyList()
        completedSecondaryAtMs = 0L
        inFlightSecondaryMessageIds = null
        readReceiptBaselineReady = false
        markAsReadAfterBaseline = false
        pendingReadReceiptIds.clear()
        _myReadMessageIds.value = emptySet()

        dismissChatNotification(chatId)

        viewModelScope.launch {
            // Wait for session to become available (handles race between SDK auto-refresh and
            // manual importSession — during this window currentUserOrNull() may be null even
            // though network requests succeed with the refreshed token).
            if (currentUserId.isEmpty()) {
                repeat(10) {
                    currentUserId = authRepo.currentUserId() ?: ""
                    if (currentUserId.isNotEmpty()) return@repeat
                    delay(300)
                }
                Log.d("ChatVM", "init: currentUserId after retry = '$currentUserId'")
            }
            // Fallback 1: decode userId from the stored JWT token — works fully offline,
            // no Supabase SDK needed. Handles VPN-blocked session restore where
            // currentUserOrNull() stays null even though tokens are valid.
            if (currentUserId.isEmpty()) {
                currentUserId = app.prefs.getUserIdFromStoredToken() ?: ""
                if (currentUserId.isNotEmpty())
                    Log.d("ChatVM", "init: currentUserId from JWT token = '$currentUserId'")
            }
            // Fallback 2: cached own profile (only present if user opened profile settings before)
            if (currentUserId.isEmpty()) {
                currentUserId = cache.loadOwnProfile()?.id ?: ""
                Log.w("ChatVM", "init: currentUserId fallback from profile cache = '$currentUserId'")
            }
            val cachedInfo         = cache.loadChatInfo(chatId)
            val cachedMessages     = cache.loadMessages(chatId)
            val cachedProfiles     = cache.loadProfileMap()
            val cachedPinned       = cache.loadPinnedContent(chatId)
            val cachedReactions    = cache.loadReactions(chatId) ?: emptyMap()
            val cachedVoiceListens = cache.loadVoiceListens(chatId)
            val cachedReadIds      = cache.loadReadIds(chatId) ?: emptySet()
            // Pre-populate OG cache from disk so link previews render immediately
            cache.loadOgData()?.let { ogCache.putAll(it) }

            // Restore cached info for TopAppBar while loading
            if (cachedInfo != null) {
                _chatName.value    = cachedInfo.name
                _isGroup.value     = cachedInfo.isGroup
                _memberCount.value = cachedInfo.memberCount
                otherUserIdVal     = cachedInfo.otherUserId
                _otherUserId.value = cachedInfo.otherUserId
            }
            if (cachedPinned != null) {
                _pinnedMessage.value        = cachedPinned.pinnedMessage
                _pinnedMessageContent.value = cachedPinned.messageContent
            }
            cachedProfiles.forEach { profileCache[it.key] = it.value }

            // Show spinner until messages are ready (server or cache fallback)
            _isLoading.value = true

            val revealMutex = Mutex()
            var revealDone = false

            // Helper: reveal chat from cached messages (with pre-cached enrichment data)
            suspend fun revealFromCache() {
                val items = buildUiItems(
                    cachedMessages ?: emptyList(),
                    cachedReactions,
                    cachedVoiceListens?.myListened ?: emptySet(),
                    cachedVoiceListens?.otherListened ?: emptySet(),
                    cachedReadIds
                )
                _messages.value = items
                _lastSeenMsgCount.value = items.size
                _firstUnreadIndex.value = -1
                _isLoading.value = false
                _scrollToBottomEvent.value++
                Log.d(CHAT_OPEN_LOG, "messages loaded count=${items.size} source=cache")
                revealDone = true
            }

            val hasCache = !cachedMessages.isNullOrEmpty()

            // Show cache immediately if available — server will merge silently in background.
            // This eliminates the spinner when navigating to a chat that has cached messages.
            if (hasCache) {
                revealFromCache()
            }

            // Server load (background coroutine)
            // loadChatInfo + loadPinnedMessage are independent — run in parallel.
            // loadMessages depends on historyFrom set by loadChatInfo, so runs after.
            val serverJob = launch {
                try {
                    coroutineScope {
                        launch { loadChatInfo() }
                        launch { loadPinnedMessage() }
                    }
                    loadMessages(scrollAfter = false)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ChatVM", "init: server load failed", e)
                }
            }

            // Fallback timer: show cache after 2.5s if server hasn't responded
            val fallbackJob = if (!revealDone && hasCache) {
                launch {
                    delay(2_500L)
                    revealMutex.withLock {
                        if (!revealDone) revealFromCache()
                    }
                }
            } else null

            // Wait for server to finish
            serverJob.join()
            fallbackJob?.cancel()

            revealMutex.withLock {
                if (!revealDone && _messages.value.isEmpty() && hasCache) {
                    // Server failed fast (offline) — fall back to cache
                    revealFromCache()
                } else if (!revealDone) {
                    // Server responded first with data (happy path)
                    val idx = _firstUnreadIndex.value
                    _lastSeenMsgCount.value = if (idx >= 0) idx else _messages.value.size
                    _isLoading.value = false
                    _scrollToBottomEvent.value++
                    revealDone = true
                } else {
                    // Cache was already shown, server caught up — silent merge
                    // loadMessages() already updated _messages via smart merge
                    val idx = _firstUnreadIndex.value
                    _lastSeenMsgCount.value = if (idx >= 0) idx else _messages.value.size
                    // Scroll to show unread messages that arrived from server after cache was shown
                    if (idx >= 0) _scrollToBottomEvent.value++
                }
            }

            // Clear unread separator after 5 s
            if (_firstUnreadIndex.value >= 0) {
                viewModelScope.launch {
                    delay(5_000L)
                    _firstUnreadIndex.value = -1
                }
            }

            observeNewMessages()
            observeUpdatedMessages()
            observeReadReceipts()
            observeVoiceListens()
            observeReactions()
            startTypingPolling()
            startChatDeletionWatch()
            Log.d(CHAT_OPEN_LOG, "ready")

            // Restore failed messages from persistent outbox (survive app restarts)
            val failedOutbox = app.outboxManager.getForChat(chatId)
            if (failedOutbox.isNotEmpty()) {
                val failedItems = failedOutbox.map { outbox ->
                    MessageUiItem(
                        message = Message(
                            id = outbox.localId, chatId = chatId, senderId = outbox.senderId,
                            content = outbox.content, type = "text", replyToId = outbox.replyToId,
                            createdAt = outbox.createdAt, silent = outbox.silent
                        ),
                        senderProfile = profileCache[currentUserId],
                        isOwn = true, isRead = false, isPending = false, isFailed = true
                    )
                }
                _messages.value = _messages.value + failedItems
            }

            // Flush outbox immediately if online, then watch for network recovery
            if (isOnline.value && app.isAppInForeground.value) flushOutbox()
            launch {
                var wasOnline = isOnline.value
                isOnline.collect { online ->
                    if (online && !wasOnline && app.isAppInForeground.value) flushOutbox()
                    wasOnline = online
                }
            }
        }
    }

    /** Called when navigating to a contact who has no chat yet. Loads their profile and shows
     *  the empty chat UI without creating a DB record. The chat is created on first send. */
    fun initDraft(targetUserId: String) {
        if (draftTargetUserId == targetUserId) return
        draftTargetUserId = targetUserId
        if (currentUserId.isEmpty()) {
            currentUserId = authRepo.currentUserId()
                ?: app.prefs.getUserIdFromStoredToken()
                ?: ""
        }
        viewModelScope.launch {
            val profile = userRepo.getProfile(targetUserId)
            if (profile != null) {
                _chatName.value = profile.displayName
                profileCache[targetUserId] = profile
            }
            _isGroup.value = false
            _hasMoreMessages.value = false
            _isLoading.value = false
            _scrollToBottomEvent.value++
        }
    }

    /** Creates the personal chat on the first send. Mutex-protected so concurrent sends
     *  (e.g. album uploads) only create the chat once; the rest wait and return true. */
    private suspend fun ensureChatCreated(): Boolean {
        if (draftTargetUserId == null) return true
        draftMutex.withLock {
            if (draftTargetUserId == null) return true // already created by a concurrent send
            val uid = draftTargetUserId!!
            val newChatId = chatRepo.createPersonalChat(uid) ?: return false
            chatId = newChatId
            draftTargetUserId = null
            // Update tracker so push notifications are suppressed while user is in this chat
            com.example.svoi.ActiveChatTracker.activeChatId = newChatId
            loadChatInfo()
            observeNewMessages()
            observeUpdatedMessages()
            observeReadReceipts()
            observeVoiceListens()
            observeReactions()
            startTypingPolling()
            startChatDeletionWatch()
        }
        return true
    }

    /** Poll every 8s to detect group deletion or partner leaving a personal chat */
    private fun startChatDeletionWatch() {
        chatDeletionWatchJob?.cancel()
        if (!app.isAppInForeground.value) return
        chatDeletionWatchJob = viewModelScope.launch {
            while (true) {
                delay(8_000L)
                if (!canUseForegroundNetwork()) continue
                if (_isGroup.value) {
                    val exists = chatRepo.getChat(chatId) != null
                    if (!exists) {
                        _isChatDeleted.value = true
                        break
                    }
                } else {
                    // Personal chat: check if partner soft-deleted the chat (left_at set)
                    val members = chatRepo.getChatMembers(chatId)
                    val otherMember = members.firstOrNull { it.userId != currentUserId }
                    if (otherMember?.leftAt != null) {
                        _isPartnerLeft.value = true
                    }
                }
            }
        }
    }

    private fun buildUiItems(
        raw: List<Message>,
        cachedReactions: Map<String, List<ReactionGroup>> = emptyMap(),
        cachedMyListened: Set<String> = emptySet(),
        cachedOtherListened: Set<String> = emptySet(),
        cachedReadIds: Set<String> = emptySet()
    ): List<MessageUiItem> {
        val myId = currentUserId
        val messageMap = raw.associateBy { it.id }
        return raw.map { msg ->
            val replyMsg = msg.replyToId?.let { messageMap[it] }
            val isOwn = msg.senderId == myId
            val isListened = msg.type == "voice" && if (isOwn) {
                msg.id in cachedOtherListened
            } else {
                msg.id in cachedMyListened
            }
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = isOwn,
                isRead = isOwn && msg.id in cachedReadIds,
                isListened = isListened,
                replyToMessage = replyMsg,
                replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] },
                reactions = cachedReactions[msg.id] ?: emptyList()
            )
        }
    }

    private suspend fun loadChatInfo() {
        val chat = chatRepo.getChat(chatId) ?: return
        _chat.value = chat
        _isGroup.value = chat.type == "group"

        val members = chatRepo.getChatMembers(chatId)
        val profiles = userRepo.getProfiles(members.map { it.userId })
        profiles.forEach { profileCache[it.id] = it }

        val myId = currentUserId
        // Store non-self member profiles for @mention suggestions (group chats only)
        if (chat.type == "group") {
            _groupMemberProfiles.value = profiles.filter { it.id != myId }
        }

        // Fetch historyFrom for the current user (only once — historyFrom never changes after join)
        if (historyFrom == null) {
            historyFrom = members.firstOrNull { it.userId == myId }?.historyFrom
        }

        // Load mute state from DB and sync to local prefs
        val myMembership = members.firstOrNull { it.userId == myId }
        val mutedInDb = myMembership?.muted == true
        _isMuted.value = mutedInDb
        app.themeManager.setChatMuted(chatId, mutedInDb)

        // Count only active (not left) members
        _memberCount.value = members.count { it.leftAt == null }

        if (chat.type == "personal") {
            // Partner left if their left_at is set
            val otherMember = members.firstOrNull { it.userId != myId }
            if (otherMember?.leftAt != null) {
                _isPartnerLeft.value = true
            }
            val other = profiles.firstOrNull { it.id != myId }
            _otherUserProfile.value = other
            _chatName.value = other?.displayName ?: "Пользователь"
            other?.let { otherProfile ->
                otherUserIdVal = otherProfile.id
                _otherUserId.value = otherProfile.id
                viewModelScope.launch {
                    delay(350L)
                    startPresencePolling(otherProfile.id)
                }
            }
        } else {
            _chatName.value = chat.name ?: "Группа"
            _otherUserProfile.value = null
            // Poll presence for active members (excluding self) so we can show "X в сети" in the header
            val otherMemberIds = members.filter { it.userId != myId && it.leftAt == null }.map { it.userId }
            viewModelScope.launch {
                delay(350L)
                startGroupPresencePolling(otherMemberIds)
            }
        }

        cache.saveChatInfo(CachedChatInfo(
            chatId = chatId,
            name = _chatName.value,
            isGroup = _isGroup.value,
            memberCount = _memberCount.value,
            otherUserId = otherUserIdVal
        ))
    }

    /** Tracks online count for group members (excluding self).
     *  Realtime subscription for instant updates + 30s periodic poll as fallback. */
    private fun countVisibleOnlineGroupMembers(presences: List<UserPresence>): Int =
        presences.count { presence ->
            val profile = profileCache[presence.userId]
            profile?.hideOnlineStatus != true && presence.isTrulyOnline()
        }

    private fun startGroupPresencePolling(memberIds: List<String>) {
        if (memberIds.isEmpty()) return
        currentGroupPresenceMemberIds = memberIds
        groupPresencePollingJob?.cancel()
        groupPresenceRealtimeJob?.cancel()
        if (!app.isAppInForeground.value) {
            Log.d("Presence", "group presence polling paused because app background")
            return
        }
        val memberIdSet = memberIds.toHashSet()

        // Periodic fallback: ensures correctness even if Realtime misses an event
        groupPresencePollingJob = viewModelScope.launch {
            if (app.supabaseChecker.isReachable.value) {
                val initial = userRepo.getPresences(memberIds)
                _groupOnlineCount.value = countVisibleOnlineGroupMembers(initial)
            }
            while (true) {
                delay(30_000L)
                if (!canUseForegroundNetwork()) continue
                val updated = userRepo.getPresences(memberIds)
                _groupOnlineCount.value = countVisibleOnlineGroupMembers(updated)
            }
        }

        // Realtime: instant update whenever any group member's presence row changes
        groupPresenceRealtimeJob = viewModelScope.launch {
            while (true) {
                if (!app.isAppInForeground.value) return@launch
                try {
                    userRepo.presenceUpdateFlowAll().collect { presence ->
                        if (presence.userId in memberIdSet) {
                            // Re-fetch all to get server-computed isTrulyOnline values
                            if (!canUseForegroundNetwork()) return@collect
                            val updated = userRepo.getPresences(memberIds)
                            _groupOnlineCount.value = countVisibleOnlineGroupMembers(updated)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!app.isAppInForeground.value) {
                        Log.d("Presence", "Group presence Realtime paused because app background")
                        return@launch
                    }
                    Log.w("Presence", "Group presence Realtime failed, retry in 3s: ${e.message}")
                    delay(3_000L)
                }
            }
        }
    }

    private fun startPresencePolling(userId: String) {
        if (activePresenceUserId == userId &&
            (personalPresencePollingJob?.isActive == true || personalPresenceRealtimeJob?.isActive == true)
        ) {
            Log.d("Presence", "skip duplicate presence polling userId=$userId")
            return
        }
        personalPresencePollingJob?.cancel()
        personalPresenceRealtimeJob?.cancel()
        activePresenceUserId = userId
        if (!app.isAppInForeground.value) {
            Log.d("Presence", "presence polling paused because app background")
            activePresenceUserId = null
            return
        }
        personalPresencePollingJob = viewModelScope.launch {
            if (app.supabaseChecker.isReachable.value) {
                _otherUserPresence.value = userRepo.getPresence(userId)
            }
            while (true) {
                delay(5_000L)
                if (!canUseForegroundNetwork()) continue
                val presence = userRepo.getPresence(userId)
                if (presence != null) _otherUserPresence.value = presence
            }
        }
        // Realtime on top: instant updates when presence changes. Retry on failure.
        personalPresenceRealtimeJob = viewModelScope.launch {
            while (true) {
                if (!app.isAppInForeground.value) return@launch
                try {
                    userRepo.presenceUpdateFlow(userId).collect { presence ->
                        Log.d("Presence", "realtime update for $userId: $presence")
                        _otherUserPresence.value = presence
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!app.isAppInForeground.value) {
                        Log.d("Presence", "Realtime presence paused because app background")
                        return@launch
                    }
                    Log.w("Presence", "Realtime presence failed, retry in 3s: ${e.message}")
                    delay(3_000L)
                }
            }
        }
    }

    private suspend fun loadMessages(scrollAfter: Boolean = true) {
        _hasMoreMessages.value = true
        val raw = messageRepo.getMessages(chatId, limit = 50, historyFrom = historyFrom)
        if (raw.isEmpty()) return  // offline — keep cached messages shown

        val serverMessageIds = raw.map { it.id }
        val serverSignature = raw.joinToString(separator = "|") { msg ->
            "${msg.id}:${msg.updatedAt}:${msg.editedAt}:${msg.deletedForAll}"
        }
        val cachedReactions = cache.loadReactions(chatId) ?: emptyMap()
        val cachedVoiceListens = cache.loadVoiceListens(chatId)
        val cachedReadIds = cache.loadReadIds(chatId) ?: emptySet()
        val quickItems = buildUiItems(
            raw,
            cachedReactions,
            cachedVoiceListens?.myListened ?: emptySet(),
            cachedVoiceListens?.otherListened ?: emptySet(),
            cachedReadIds
        )
        val newLastId = raw.lastOrNull()?.id
        val nowMs = System.currentTimeMillis()

        val shouldStartSecondary = serverLoadApplyMutex.withLock {
            val isImmediateDuplicateServerResult =
                serverSignature == lastAppliedServerMessageSignature &&
                    nowMs - lastAppliedServerMessageAtMs < SECONDARY_DUPLICATE_GUARD_MS
            if (isImmediateDuplicateServerResult) {
                Log.d(CHAT_OPEN_LOG, "skip duplicate server result count=${raw.size}")
                false
            } else {
                val isSameServerSignature = serverSignature == lastAppliedServerMessageSignature
                lastAppliedServerMessageSignature = serverSignature
                lastAppliedServerMessageAtMs = nowMs

                // Fast merge: show message structure before secondary enrichment finishes.
                val current = _messages.value
                val shouldApplyQuickMerge = !isSameServerSignature || current.isEmpty()
                val merged = if (!shouldApplyQuickMerge) {
                    current
                } else if (current.isNotEmpty() && current.map { it.message.id } == serverMessageIds) {
                    val quickById = quickItems.associateBy { it.message.id }
                    current.map { old ->
                        val updated = quickById[old.message.id]
                        if (updated != null && updated != old) updated else old
                    }
                } else {
                    quickItems
                }
                if (merged != current) {
                    _messages.value = merged
                }

                lastKnownMessageId = newLastId
                if (scrollAfter) _scrollToBottomEvent.value++
                Log.d(CHAT_OPEN_LOG, "messages loaded count=${raw.size} source=server")

                val isRecentlyCompletedSecondary =
                    completedSecondaryMessageIds == serverMessageIds &&
                        nowMs - completedSecondaryAtMs < SECONDARY_DUPLICATE_GUARD_MS
                when {
                    inFlightSecondaryMessageIds == serverMessageIds -> {
                        Log.d(CHAT_OPEN_LOG, "skip duplicate secondary data in-flight count=${raw.size}")
                        false
                    }
                    isRecentlyCompletedSecondary -> {
                        Log.d(CHAT_OPEN_LOG, "skip duplicate secondary data completed count=${raw.size}")
                        false
                    }
                    else -> {
                        inFlightSecondaryMessageIds = serverMessageIds
                        Log.d(CHAT_OPEN_LOG, "secondary data started")
                        true
                    }
                }
            }
        }

        // Save immediately — don't wait for secondary data or OG prefetch.
        cache.saveMessages(chatId, raw)
        cache.saveProfiles(profileCache.values)

        if (!shouldStartSecondary) return

        viewModelScope.launch(Dispatchers.IO) {
            val secondaryResult = runCatching {
                // Find first unread incoming message after the list is already visible.
                if (_firstUnreadIndex.value < 0) {
                    val myId = currentUserId
                    val incomingIds = raw.filter { it.senderId != myId }.map { it.id }
                    val alreadyReadByMe = messageRepo.getReadMessageIdsByUser(incomingIds, myId)
                    val idx = raw.indexOfFirst { it.senderId != myId && it.id !in alreadyReadByMe }
                    _firstUnreadIndex.value = idx
                    _myReadMessageIds.value = alreadyReadByMe
                    Log.d("UnreadSep", "firstUnreadIndex=$idx, incoming=${incomingIds.size}, alreadyRead=${alreadyReadByMe.size}")
                }

                val enriched = enrichMessages(raw)
                val latest = _messages.value
                val enrichedMerged = if (latest.isNotEmpty() && latest.map { it.message.id } == raw.map { it.id }) {
                    val enrichedById = enriched.associateBy { it.message.id }
                    latest.map { old ->
                        val updated = enrichedById[old.message.id]
                        if (updated != null && updated != old) updated else old
                    }
                } else {
                    enriched
                }
                if (enrichedMerged != latest) {
                    _messages.value = enrichedMerged
                }
            }
            secondaryResult.exceptionOrNull()?.let { e ->
                Log.w(CHAT_OPEN_LOG, "secondary data failed: ${e.message}")
            }
            serverLoadApplyMutex.withLock {
                if (inFlightSecondaryMessageIds == serverMessageIds) {
                    if (secondaryResult.isSuccess) {
                        completedSecondaryMessageIds = serverMessageIds
                        completedSecondaryAtMs = System.currentTimeMillis()
                    }
                    inFlightSecondaryMessageIds = null
                }
            }
            readReceiptBaselineReady = true
            if (markAsReadAfterBaseline) {
                markAsReadAfterBaseline = false
                markAsRead()
            }
        }

        // OG previews are fetched lazily from visible messages after the first chat frame.
    }

    private suspend fun enrichMessages(raw: List<Message>): List<MessageUiItem> = coroutineScope {
        val senderIds = raw.mapNotNull { it.senderId }.distinct()
        val forwardedFromIds = raw.mapNotNull { it.forwardedFromUserId }.distinct()
        val allIds = (senderIds + forwardedFromIds).distinct()
        val missing = allIds.filter { it !in profileCache }

        val myId = currentUserId
        val myMessageIds = raw.filter { it.senderId == myId }.map { it.id }
        val voiceIds = raw.filter { it.type == "voice" }.map { it.id }
        val allMsgIds = raw.map { it.id }

        // All 5 fetches are independent — run in parallel
        val dProfiles     = async { if (missing.isNotEmpty()) userRepo.getProfiles(missing) else emptyList() }
        val dRead         = async { messageRepo.getReadMessageIds(myMessageIds) }
        val dMyListens    = async { if (voiceIds.isNotEmpty()) messageRepo.getMyListenedVoiceIds(voiceIds) else emptySet() }
        val dOtherListens = async { if (voiceIds.isNotEmpty()) messageRepo.getOtherListenedVoiceIds(voiceIds) else emptySet() }
        val dReactions    = async { messageRepo.getReactions(allMsgIds) }

        dProfiles.await().forEach { profileCache[it.id] = it }
        val readIds               = dRead.await()
        val myListenedVoiceIds    = dMyListens.await()
        val otherListenedVoiceIds = dOtherListens.await()
        val allReactions          = dReactions.await()
        val reactionsByMsg        = allReactions.groupBy { it.messageId }

        // Pre-compute reaction groups per message (used for both UI and cache)
        val allReactionGroups = raw.associate { msg ->
            val msgReactions = reactionsByMsg[msg.id] ?: emptyList()
            msg.id to msgReactions.groupBy { it.emoji }.map { (emoji, list) ->
                ReactionGroup(emoji, list.size, list.any { it.userId == myId })
            }.sortedByDescending { it.count }
        }

        // Persist reactions, voice listen state and read IDs to disk (background, non-blocking)
        viewModelScope.launch {
            cache.saveReactions(chatId, allReactionGroups)
            if (voiceIds.isNotEmpty()) {
                cache.saveVoiceListens(chatId, myListenedVoiceIds, otherListenedVoiceIds)
            }
            if (readIds.isNotEmpty()) {
                cache.saveReadIds(chatId, readIds)
            }
        }

        val messageMap = raw.associateBy { it.id }
        raw.map { msg ->
            val replyMsg = msg.replyToId?.let { messageMap[it] ?: messageRepo.getMessage(it) }
            val isOwn = msg.senderId == myId
            val isListened = msg.type == "voice" && if (isOwn) {
                msg.id in otherListenedVoiceIds
            } else {
                msg.id in myListenedVoiceIds
            }
            MessageUiItem(
                message = msg,
                senderProfile = msg.senderId?.let { profileCache[it] },
                isOwn = isOwn,
                isRead = msg.id in readIds,
                isListened = isListened,
                replyToMessage = replyMsg,
                replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                forwardedFromProfile = msg.forwardedFromUserId?.let { profileCache[it] },
                reactions = allReactionGroups[msg.id] ?: emptyList()
            )
        }
    }

    /** Called by ChatScreen when user reaches the bottom — clears the unread badge */
    fun markAsRead() {
        if (!readReceiptBaselineReady) {
            markAsReadAfterBaseline = true
            return
        }
        val incomingIds = _messages.value.filter { !it.isOwn }.map { it.message.id }.toSet()
        val newIds = incomingIds - _myReadMessageIds.value - pendingReadReceiptIds
        _lastSeenMsgCount.value = _messages.value.size
        if (newIds.isNotEmpty()) {
            pendingReadReceiptIds.addAll(newIds)
            sendReadReceipts(newIds)
        }
    }

    private fun sendReadReceipts(newIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = withContext(NonCancellable) {
                messageRepo.markMessagesAsRead(chatId, newIds.toList())
            }
            withContext(Dispatchers.Main) {
                pendingReadReceiptIds.removeAll(newIds)
                if (success) {
                    _myReadMessageIds.value = _myReadMessageIds.value + newIds
                }
            }
        }
    }

    private suspend fun loadPinnedMessage() {
        val pinned = chatRepo.getPinnedMessage(chatId)
        val content = pinned?.let { messageRepo.getMessage(it.messageId) }
        _pinnedMessage.value = pinned
        _pinnedMessageContent.value = content
        cache.savePinnedContent(chatId, pinned, content)
        // Загружаем профиль отправителя для overlay
        val senderId = content?.senderId
        if (senderId != null) {
            _pinnedSenderProfile.value = profileCache[senderId]
                ?: userRepo.getProfile(senderId)
        } else {
            _pinnedSenderProfile.value = null
        }
    }

    fun pinMessage(messageId: String) {
        viewModelScope.launch {
            val success = chatRepo.pinMessage(chatId, messageId)
            if (success) {
                val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
                messageRepo.sendSystemMessage(chatId, "$myName закрепил(а) сообщение", messageId)
                loadPinnedMessage()
            }
        }
    }

    fun unpinMessage() {
        viewModelScope.launch {
            val oldPinnedId = _pinnedMessage.value?.messageId
            val success = chatRepo.unpinMessage(chatId)
            if (success) {
                val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
                messageRepo.sendSystemMessage(chatId, "$myName открепил(а) сообщение", oldPinnedId)
                _pinnedMessage.value = null
                _pinnedMessageContent.value = null
            }
        }
    }

    fun scrollToMessage(messageId: String) {
        Log.d("PinnedNav", "requested messageId=$messageId")
        val current = _messages.value
        if (current.any { it.message.id == messageId }) {
            Log.d("PinnedNav", "found in current messages messageId=$messageId")
            emitPinnedScrollRequest(messageId)
            return
        }

        if (pinnedNavigationJob?.isActive == true &&
            _pinnedNavigationLoadingMessageId.value == messageId
        ) {
            return
        }

        pinnedNavigationJob?.cancel()
        val requestedChatId = chatId
        _pinnedNavigationLoadingMessageId.value = messageId
        Log.d("PinnedNav", "loading anchor window messageId=$messageId")
        pinnedNavigationJob = viewModelScope.launch {
            try {
                when (val result = messageRepo.loadMessagesAround(
                    chatId = requestedChatId,
                    targetMessageId = messageId,
                    beforeLimit = 25,
                    afterLimit = 25,
                    historyFrom = historyFrom
                )) {
                    is MessageAnchorWindowResult.Found -> {
                        if (chatId != requestedChatId) return@launch
                        val containsTarget = result.messages.any { it.id == messageId }
                        Log.d(
                            "PinnedNav",
                            "anchor loaded count=${result.messages.size}, containsTarget=$containsTarget"
                        )
                        if (!containsTarget) {
                            Log.d("PinnedNav", "not found / unavailable")
                            _error.value = "Закреплённое сообщение недоступно"
                            return@launch
                        }

                        val mergedRaw = (_messages.value.map { it.message } + result.messages)
                            .distinctBy { it.id }
                            .sortedWith(compareBy<Message> { it.createdAt ?: "" }.thenBy { it.id })
                        _messages.value = enrichMessages(mergedRaw)
                        emitPinnedScrollRequest(messageId)
                    }

                    MessageAnchorWindowResult.NotFound -> {
                        if (chatId != requestedChatId) return@launch
                        Log.d("PinnedNav", "not found / unavailable")
                        _error.value = "Закреплённое сообщение недоступно"
                    }
                }
            } finally {
                if (_pinnedNavigationLoadingMessageId.value == messageId) {
                    _pinnedNavigationLoadingMessageId.value = null
                }
                if (pinnedNavigationJob == coroutineContext[Job]) {
                    pinnedNavigationJob = null
                }
            }
        }
    }

    fun clearScrollToMessageEvent() {
        _scrollToMessageRequest.value = null
    }

    private fun emitPinnedScrollRequest(messageId: String) {
        val request = PinnedScrollRequest(
            requestId = ++pinnedScrollRequestId,
            messageId = messageId,
            highlight = true
        )
        Log.d(
            "PinnedNav",
            "emitting scroll request requestId=${request.requestId} messageId=$messageId"
        )
        _scrollToMessageRequest.value = request
    }

    /** Re-starts the 2-second highlight timer. Call this when the message is actually visible
     *  on screen — avoids the timer expiring during history loading before the scroll happens. */
    fun refreshHighlight(messageId: String) {
        _highlightedMessageId.value = messageId
        viewModelScope.launch {
            delay(2_000L)
            if (_highlightedMessageId.value == messageId) _highlightedMessageId.value = null
        }
    }

    private fun observeNewMessages() {
        if (messageInsertJob?.isActive == true || !app.isAppInForeground.value) return
        messageInsertJob = viewModelScope.launch {
            try {
                messageRepo.messageInsertFlow(chatId).collect { newMsg ->
                // New incoming message — clear typing indicator for that user
                if (newMsg.senderId != currentUserId) {
                    _typingUsers.value = _typingUsers.value.filter { it.userId != newMsg.senderId }
                }

                val profile = newMsg.senderId?.let { id ->
                    profileCache.getOrPut(id) {
                        userRepo.getProfile(id) ?: Profile(id = id)
                    }
                }
                val forwardedFromProfile = newMsg.forwardedFromUserId?.let { id ->
                    profileCache.getOrPut(id) {
                        userRepo.getProfile(id) ?: Profile(id = id)
                    }
                }
                val replyMsg = newMsg.replyToId?.let { messageRepo.getMessage(it) }
                val item = MessageUiItem(
                    message = newMsg,
                    senderProfile = profile,
                    isOwn = newMsg.senderId == currentUserId,
                    isRead = false,
                    replyToMessage = replyMsg,
                    replyToSenderProfile = replyMsg?.senderId?.let { profileCache[it] },
                    forwardedFromProfile = forwardedFromProfile
                )
                // Dedup: skip if already loaded (can happen during the brief window
                // between loadMessages() completing and the Realtime subscription activating)
                if (_messages.value.any { it.message.id == newMsg.id }) return@collect

                // If this is our own message, try to replace a pending placeholder instead of
                // appending — avoids the flash where the message disappears then reappears.
                val pendingIdx = if (newMsg.senderId == currentUserId) {
                    _messages.value.indexOfFirst { existing ->
                        existing.message.id.startsWith("pending_") &&
                            !existing.isFailed &&
                            existing.message.content == newMsg.content &&
                            existing.message.replyToId == newMsg.replyToId
                    }
                } else -1

                val updated = if (pendingIdx >= 0) {
                    // Preserve the pending item's stableKey so LazyColumn sees the same key
                    // and just recomposes in place — no remove+insert animation.
                    val pendingKey = _messages.value[pendingIdx].stableKey
                    _messages.value.toMutableList().also { it[pendingIdx] = item.copy(stableKey = pendingKey) }
                } else {
                    _messages.value + item
                }
                _messages.value = updated
                lastKnownMessageId = newMsg.id
                // Скролл вниз управляется из ChatScreen: только если пользователь уже внизу

                // Trigger entrance animation only for truly new messages.
                // If we replaced a pending placeholder, the item was already visible — skip animation.
                if (pendingIdx < 0) {
                    val msgId = newMsg.id
                    _animatingMessageIds.value = _animatingMessageIds.value + msgId
                    viewModelScope.launch {
                        delay(900L)
                        _animatingMessageIds.value = _animatingMessageIds.value - msgId
                    }
                }

                // Не помечаем прочитанным здесь — это делает ChatScreen когда пользователь внизу
                cache.saveMessages(chatId, updated.map { it.message })
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun observeUpdatedMessages() {
        if (messageUpdateJob?.isActive == true || !app.isAppInForeground.value) return
        messageUpdateJob = viewModelScope.launch {
            try {
                messageRepo.messageUpdateFlow(chatId).collect { updated ->
                    _messages.value = _messages.value.map { item ->
                        if (item.message.id == updated.id) item.copy(message = updated) else item
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun observeReadReceipts() {
        if (readReceiptJob?.isActive == true || !app.isAppInForeground.value) return
        readReceiptJob = viewModelScope.launch {
            try {
                messageRepo.messageReadFlow(chatId).collect { read ->
                    // Only update if it's one of our messages being read
                    val idx = _messages.value.indexOfFirst { it.message.id == read.messageId && it.isOwn }
                    if (idx >= 0 && !_messages.value[idx].isRead) {
                        Log.d("ReadReceipts", "realtime: message ${read.messageId} marked read by ${read.userId}")
                        _messages.value = _messages.value.toMutableList().also {
                            it[idx] = it[idx].copy(isRead = true)
                        }
                        // Persist to disk so offline view reflects read status
                        cache.saveReadIds(chatId, setOf(read.messageId))
                    }
                }
            } catch (_: Exception) {
                // Realtime unavailable — fall back to polling
                while (true) {
                    delay(10_000L)
                    if (!canUseForegroundNetwork()) continue
                    val unreadOwnIds = _messages.value.filter { it.isOwn && !it.isRead }.map { it.message.id }
                    if (unreadOwnIds.isNotEmpty()) {
                        val readIds = messageRepo.getReadMessageIds(unreadOwnIds)
                        if (readIds.isNotEmpty()) {
                            _messages.value = _messages.value.map { item ->
                                if (item.message.id in readIds) item.copy(isRead = true) else item
                            }
                            cache.saveReadIds(chatId, readIds)
                        }
                    }
                }
            }
        }
    }

    // ── Reactions ──────────────────────────────────────────────────────────────

    fun toggleReaction(messageId: String, emoji: String) {
        // Optimistic update
        val myId = currentUserId
        _messages.value = _messages.value.map { item ->
            if (item.message.id != messageId) return@map item
            val existing = item.reactions.toMutableList()
            val group = existing.find { it.emoji == emoji }
            val updated = if (group != null && group.hasMyReaction) {
                // Removing own reaction — always allowed
                if (group.count == 1) existing.filter { it.emoji != emoji }
                else existing.map { if (it.emoji == emoji) it.copy(count = it.count - 1, hasMyReaction = false) else it }
            } else {
                // Adding reaction — check max 3 per user per message
                val myReactionCount = existing.count { it.hasMyReaction }
                if (myReactionCount >= 3) return@map item
                if (group != null) {
                    existing.map { if (it.emoji == emoji) it.copy(count = it.count + 1, hasMyReaction = true) else it }
                } else {
                    existing + ReactionGroup(emoji, 1, true)
                }
            }
            item.copy(reactions = updated.sortedByDescending { it.count })
        }
        viewModelScope.launch(Dispatchers.IO) {
            messageRepo.toggleReaction(messageId, emoji)
        }
    }

    private fun refreshReactionsForMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val reactions = messageRepo.getReactions(listOf(messageId))
            val myId = currentUserId
            val groups = reactions.groupBy { it.emoji }.map { (emoji, list) ->
                ReactionGroup(emoji, list.size, list.any { it.userId == myId })
            }.sortedByDescending { it.count }
            withContext(Dispatchers.Main) {
                _messages.value = _messages.value.map { item ->
                    if (item.message.id == messageId) item.copy(reactions = groups) else item
                }
            }
        }
    }

    private fun observeReactions() {
        // React to inserts
        if (reactionInsertJob?.isActive != true && app.isAppInForeground.value) reactionInsertJob = viewModelScope.launch {
            try {
                messageRepo.reactionInsertFlow().collect { reaction ->
                    if (reaction.userId != currentUserId) {
                        refreshReactionsForMessage(reaction.messageId)
                    }
                }
            } catch (_: Exception) {}
        }
        // React to deletes — we don't get the old record, so refresh all visible messages
        if (reactionDeleteJob?.isActive != true && app.isAppInForeground.value) reactionDeleteJob = viewModelScope.launch {
            try {
                messageRepo.reactionDeleteFlow().collect {
                    // Refresh reactions for all currently visible messages that have reactions
                    val msgIds = _messages.value.filter { it.reactions.isNotEmpty() }.map { it.message.id }
                    if (msgIds.isNotEmpty()) {
                        val reactions = messageRepo.getReactions(msgIds)
                        val myId = currentUserId
                        val reactionsByMsg = reactions.groupBy { it.messageId }
                        withContext(Dispatchers.Main) {
                            _messages.value = _messages.value.map { item ->
                                if (item.reactions.isEmpty() && item.message.id !in reactionsByMsg) return@map item
                                val groups = (reactionsByMsg[item.message.id] ?: emptyList())
                                    .groupBy { it.emoji }
                                    .map { (emoji, list) -> ReactionGroup(emoji, list.size, list.any { it.userId == myId }) }
                                    .sortedByDescending { it.count }
                                item.copy(reactions = groups)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun observeVoiceListens() {
        if (voiceListenJob?.isActive == true || !app.isAppInForeground.value) return
        voiceListenJob = viewModelScope.launch {
            try {
                messageRepo.voiceListenInsertFlow().collect { listen ->
                    // Update own voice message: someone else just listened to it
                    val idx = _messages.value.indexOfFirst {
                        it.message.id == listen.messageId && it.isOwn && !it.isListened
                    }
                    if (idx >= 0) {
                        _messages.value = _messages.value.toMutableList().also {
                            it[idx] = it[idx].copy(isListened = true)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startTypingPolling() {
        typingPollingJob?.cancel()
        if (!app.isAppInForeground.value || chatId.isEmpty()) {
            Log.d("Typing", "polling stopped because app background")
            return
        }
        typingPollingJob = viewModelScope.launch {
            while (true) {
                delay(3_000L)
                if (!app.isAppInForeground.value) {
                    _typingUsers.value = emptyList()
                    Log.d("Typing", "polling stopped because app background")
                    return@launch
                }
                if (app.supabaseChecker.shouldShowOfflineBanner.value) {
                    _typingUsers.value = emptyList()
                    continue
                }
                if (!app.supabaseChecker.isReachable.value) continue
                if (chatId.isNotEmpty()) {
                    val typing = messageRepo.getTypingUsers(chatId, currentUserId)
                    _typingUsers.value = typing.map { TypingInfo(it.userId, it.displayName, it.status) }
                }
            }
        }
    }

    /** Called when the user changes input text — sends/debounces typing indicator */
    fun onInputTextChanged(text: String) {
        typingJob?.cancel()
        if (text.isBlank()) {
            if (canUseForegroundNetwork()) {
                viewModelScope.launch { messageRepo.clearTyping(chatId, currentUserId) }
            }
            return
        }
        typingJob = viewModelScope.launch {
            delay(300L) // debounce: don't spam server on every keystroke
            if (!canUseForegroundNetwork()) return@launch
            val displayName = profileCache[currentUserId]?.displayName ?: ""
            messageRepo.setTyping(chatId, currentUserId, displayName)
            delay(4_000L)
            if (canUseForegroundNetwork()) {
                messageRepo.clearTyping(chatId, currentUserId)
            }
        }
    }

    /**
     * Called whenever input text or cursor position changes.
     * Detects an active @mention query and updates [mentionSuggestions].
     * [cursorPos] is the current cursor position in [text].
     */
    fun onMentionQueryChanged(text: String, cursorPos: Int) {
        if (!_isGroup.value) {
            _mentionSuggestions.value = emptyList()
            return
        }
        val query = getActiveMentionQuery(text, cursorPos)
        _mentionSuggestions.value = if (query != null) {
            _groupMemberProfiles.value.filter { profile ->
                query.isEmpty() || profile.displayName?.contains(query, ignoreCase = true) == true
            }
        } else {
            emptyList()
        }
    }

    /** Returns the mention query string (text after the last '@' up to cursor), or null if not in mention mode. */
    private fun getActiveMentionQuery(text: String, cursorPos: Int): String? {
        if (cursorPos <= 0) return null
        val beforeCursor = text.substring(0, cursorPos)
        val lastAtIdx = beforeCursor.lastIndexOf('@')
        if (lastAtIdx < 0) return null
        val afterAt = beforeCursor.substring(lastAtIdx + 1)
        // If there's a newline after @, no active mention
        if ('\n' in afterAt) return null
        return afterAt
    }

    /**
     * Resolves @mentions in [text] to user IDs from the known group member list.
     * Used before sending to populate [mentionedUserIds] in the message.
     */
    fun resolveMentionedUserIds(text: String): List<String> {
        if (!_isGroup.value) return emptyList()
        return _groupMemberProfiles.value.filter { profile ->
            val name = profile.displayName ?: return@filter false
            text.contains("@$name", ignoreCase = true)
        }.map { it.id }
    }

    fun clearUnreadSeparator() {
        _firstUnreadIndex.value = -1
    }

    override fun onCleared() {
        super.onCleared()
        if (chatId.isNotEmpty()) {
            if (canUseForegroundNetwork()) {
                viewModelScope.launch { messageRepo.clearTyping(chatId, currentUserId) }
            }
        }
        voiceRecorder.cancel()
    }

    fun setReplyTo(message: Message?) { _replyTo.value = message }
    fun setEditing(message: Message?) {
        _editingMessage.value = message
        if (message != null) _replyTo.value = null
    }

    fun sendText(content: String, silent: Boolean = false) {
        if (content.isBlank()) return
        val replyId = _replyTo.value?.id
        val editing = _editingMessage.value
        val trimmed = content.trim()

        // Edit flow: no pending UI, just send
        if (editing != null) {
            viewModelScope.launch {
                if (canUseForegroundNetwork()) messageRepo.clearTyping(chatId, currentUserId)
                typingJob?.cancel()
                messageRepo.editMessage(editing.id, trimmed)
                _editingMessage.value = null
            }
            return
        }

        // Resolve @mentions before clearing suggestions
        val mentionedIds = resolveMentionedUserIds(trimmed)
        _mentionSuggestions.value = emptyList()

        // New message: add optimistic pending item immediately so the user sees it right away
        val localId = "pending_${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()

        _messages.value = _messages.value + MessageUiItem(
            message = Message(
                id = localId, chatId = chatId, senderId = currentUserId,
                content = trimmed, type = "text", replyToId = replyId,
                createdAt = now, silent = silent
            ),
            senderProfile = profileCache[currentUserId],
            isOwn = true, isRead = false, isPending = true
        )
        _scrollToOwnMessageEvent.value++
        _replyTo.value = null

        viewModelScope.launch {
            if (draftTargetUserId != null && !ensureChatCreated()) {
                _messages.value = _messages.value.map {
                    if (it.message.id == localId) it.copy(isPending = false, isFailed = true) else it
                }
                return@launch
            }
            if (canUseForegroundNetwork()) messageRepo.clearTyping(chatId, currentUserId)
            typingJob?.cancel()

            val sent = messageRepo.sendTextMessage(chatId, currentUserId, trimmed, replyId, silent, mentionedIds)
            if (sent) {
                // Keep the pending item visible but remove the spinner — realtime will replace it
                // with the real message. This avoids the flash (disappear → reappear).
                _messages.value = _messages.value.map {
                    if (it.message.id == localId) it.copy(isPending = false) else it
                }
            } else {
                // Mark as failed and save to persistent outbox for later retry
                _messages.value = _messages.value.map {
                    if (it.message.id == localId) it.copy(isPending = false, isFailed = true) else it
                }
                app.outboxManager.add(
                    OutboxMessage(
                        localId = localId, chatId = chatId, content = trimmed,
                        replyToId = replyId, silent = silent, createdAt = now,
                        senderId = currentUserId
                    )
                )
            }
        }
    }

    /** Retry sending a message that previously failed. */
    fun retryFailedMessage(localId: String) {
        val outbox = app.outboxManager.getForChat(chatId).find { it.localId == localId } ?: return
        // Restore to "sending" state
        _messages.value = _messages.value.map {
            if (it.message.id == localId) it.copy(isPending = true, isFailed = false) else it
        }
        viewModelScope.launch {
            val sent = messageRepo.sendTextMessage(chatId, outbox.senderId, outbox.content, outbox.replyToId, outbox.silent)
            if (sent) {
                app.outboxManager.remove(localId)
                _messages.value = _messages.value.filter { it.message.id != localId }
            } else {
                _messages.value = _messages.value.map {
                    if (it.message.id == localId) it.copy(isPending = false, isFailed = true) else it
                }
            }
        }
    }

    /** Cancel a failed message — removes it from the outbox and the UI. */
    fun cancelFailedMessage(localId: String) {
        app.outboxManager.remove(localId)
        _messages.value = _messages.value.filter { it.message.id != localId }
    }

    /**
     * Try to send all queued messages for this chat, in order.
     * Called automatically when network is restored.
     */
    private fun flushOutbox() {
        val pending = app.outboxManager.getForChat(chatId)
        if (pending.isEmpty()) return
        viewModelScope.launch {
            for (outbox in pending) {
                // Show "sending" state in UI
                _messages.value = _messages.value.map {
                    if (it.message.id == outbox.localId) it.copy(isPending = true, isFailed = false) else it
                }
                val sent = messageRepo.sendTextMessage(
                    chatId, outbox.senderId, outbox.content, outbox.replyToId, outbox.silent
                )
                if (sent) {
                    app.outboxManager.remove(outbox.localId)
                    _messages.value = _messages.value.filter { it.message.id != outbox.localId }
                } else {
                    // Still no network — restore failed state and stop trying
                    _messages.value = _messages.value.map {
                        if (it.message.id == outbox.localId) it.copy(isPending = false, isFailed = true) else it
                    }
                    break
                }
            }
        }
    }

    // ── Staged media & files ──────────────────────────────────────────────────

    fun addStagedMedia(uris: List<Uri>, context: Context) {
        val validItems = mutableListOf<StagedMedia>()

        for (uri in uris) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            validItems.add(StagedMedia(uri, isVideo = mimeType.startsWith("video/")))
        }

        if (validItems.isNotEmpty()) {
            _stagedMedia.value = (_stagedMedia.value + validItems).take(10)
        }
    }

    fun removeStagedMedia(index: Int) {
        _stagedMedia.value = _stagedMedia.value.filterIndexed { i, _ -> i != index }
    }

    fun replaceStagedMedia(index: Int, uri: Uri, context: Context) {
        val list = _stagedMedia.value.toMutableList()
        if (index < 0 || index >= list.size) return
        val mimeType = context.contentResolver.getType(uri)
        list[index] = StagedMedia(uri, mimeType?.startsWith("video/") == true)
        _stagedMedia.value = list
    }

    fun clearStagedMedia() {
        _stagedMedia.value = emptyList()
        _uploadProgresses.value = emptyList()
    }

    private fun getVideoNameFromUri(uri: Uri, context: Context): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (!c.moveToFirst()) return@use null
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) c.getString(nameIdx) else null
            } ?: "video_${System.currentTimeMillis()}.mp4"
        } catch (e: Exception) { "video_${System.currentTimeMillis()}.mp4" }
    }

    /** Send all staged content (photos as album, videos individually, + optional text).
     *  Clears staged state immediately, then uploads in background. */
    fun sendWithAttachments(text: String, media: List<StagedMedia>, context: Context, silent: Boolean = false) {
        val replyId = _replyTo.value?.id
        val myId = currentUserId
        val myProfile = profileCache[myId]

        val photos = media.filter { !it.isVideo }
        val videos = media.filter { it.isVideo }

        _stagedMedia.value = emptyList()
        _replyTo.value = null

        // ── Photos (album or single) ───────────────────────────────────────────
        if (photos.isNotEmpty()) {
            val pendingId = "pending_${java.util.UUID.randomUUID()}"
            val now = java.time.Instant.now().toString()
            val pendingMsg = Message(
                id = pendingId, chatId = chatId, senderId = myId,
                content = text.trim().ifBlank { null },
                type = if (photos.size == 1) "photo" else "album",
                createdAt = now
            )
            val pendingItem = MessageUiItem(
                message = pendingMsg, senderProfile = myProfile, isOwn = true, isRead = false,
                isPending = true, pendingLocalUris = photos.map { it.uri.toString() }
            )
            _messages.value = _messages.value + pendingItem
            _scrollToOwnMessageEvent.value++
            _uploadProgresses.value = List(photos.size) { 0f }

            val displayName = myProfile?.displayName ?: ""
            if (activeUploadCount.incrementAndGet() == 1) {
                if (canUseForegroundNetwork()) {
                    viewModelScope.launch { messageRepo.setTyping(chatId, myId, displayName, "uploading_media") }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                _isSending.value = true
                try {
                    if (draftTargetUserId != null && !ensureChatCreated()) {
                        _messages.value = _messages.value.filter { it.message.id != pendingId }
                        _uploadProgresses.value = emptyList()
                        _error.value = "Не удалось создать чат"
                        return@launch
                    }
                    doUploadAndSendPhotos(
                        pendingId = pendingId,
                        photoUris = photos.map { it.uri },
                        caption = text.trim().ifBlank { null },
                        replyId = replyId,
                        silent = silent,
                        context = context
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    markMediaFailed(pendingId, PendingMediaContext(
                        uris = photos.map { it.uri.toString() }, isVideo = false,
                        caption = text.trim().ifBlank { null }, replyToId = replyId, silent = silent
                    ))
                } finally {
                    _isSending.value = false
                    _uploadProgresses.value = emptyList()
                    if (activeUploadCount.decrementAndGet() == 0 && canUseForegroundNetwork()) {
                        messageRepo.clearTyping(chatId, myId)
                    }
                }
            }
        }

        // ── Videos (each as separate message) ─────────────────────────────────
        val caption = text.trim().ifBlank { null }
        videos.forEach { staged -> sendVideoInternal(staged, context, replyId, caption, silent) }
    }

    private fun sendVideoInternal(staged: StagedMedia, context: Context, replyId: String?, caption: String? = null, silent: Boolean = false) {
        val myId = currentUserId
        val mimeType = context.contentResolver.getType(staged.uri) ?: "video/mp4"
        val name = getVideoNameFromUri(staged.uri, context)

        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()
        val pendingMsg = Message(id = pendingId, chatId = chatId, senderId = myId, type = "video",
            content = caption, fileName = name, mimeType = mimeType, createdAt = now)
        val pendingItem = MessageUiItem(message = pendingMsg, senderProfile = profileCache[myId],
            isOwn = true, isRead = false, isPending = true)
        _messages.value = _messages.value + pendingItem
        _scrollToOwnMessageEvent.value++

        val displayName = profileCache[myId]?.displayName ?: ""
        _uploadProgresses.value = listOf(0f)
        if (activeUploadCount.incrementAndGet() == 1) {
            if (canUseForegroundNetwork()) {
                viewModelScope.launch { messageRepo.setTyping(chatId, myId, displayName, "uploading_media") }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (draftTargetUserId != null && !ensureChatCreated()) {
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                    _error.value = "Не удалось создать чат"
                    return@launch
                }
                doUploadAndSendVideo(
                    pendingId = pendingId,
                    uri = staged.uri,
                    name = name,
                    mimeType = mimeType,
                    caption = caption,
                    replyId = replyId,
                    silent = silent,
                    context = context
                )
            } catch (e: OutOfMemoryError) {
                markMediaFailed(pendingId, PendingMediaContext(
                    uris = listOf(staged.uri.toString()), isVideo = true,
                    caption = caption, replyToId = replyId, silent = silent,
                    mimeType = mimeType, originalFileName = name
                ))
                _error.value = "Не удалось загрузить файл. Попробуйте файл меньшего размера или повторите позже."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                markMediaFailed(pendingId, PendingMediaContext(
                    uris = listOf(staged.uri.toString()), isVideo = true,
                    caption = caption, replyToId = replyId, silent = silent,
                    mimeType = mimeType, originalFileName = name
                ))
            } finally {
                _uploadProgresses.value = emptyList()
                if (activeUploadCount.decrementAndGet() == 0 && canUseForegroundNetwork()) {
                    messageRepo.clearTyping(chatId, myId)
                }
            }
        }
    }

    // ── Media upload helpers ───────────────────────────────────────────────────

    /** Marks a pending media message as failed and stores the context needed to retry. */
    private fun markMediaFailed(pendingId: String, ctx: PendingMediaContext) {
        _messages.value = _messages.value.map { item ->
            if (item.message.id != pendingId) item
            else item.copy(isPending = false, isFailed = true, pendingMediaContext = ctx)
        }
        _uploadProgresses.value = emptyList()
    }

    /**
     * Uploads photos and sends the message. Suspends until complete or failed.
     * On failure marks the pending item as isFailed instead of removing it.
     * Must be called from Dispatchers.IO.
     */
    private suspend fun doUploadAndSendPhotos(
        pendingId: String,
        photoUris: List<Uri>,
        caption: String?,
        replyId: String?,
        silent: Boolean,
        context: Context
    ) {
        val uploadedUrls = mutableListOf<String>()
        photoUris.forEachIndexed { idx, uri ->
            val bytes = compressImage(uri, context)
            if (bytes == null) {
                markMediaFailed(pendingId, PendingMediaContext(
                    uris = photoUris.map { it.toString() }, isVideo = false,
                    caption = caption, replyToId = replyId, silent = silent
                ))
                return
            }
            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            val url = messageRepo.uploadFileWithSimulatedProgress(chatId, fileName, bytes) { progress ->
                val progs = _uploadProgresses.value.toMutableList()
                if (idx < progs.size) { progs[idx] = progress; _uploadProgresses.value = progs }
            }
            if (url == null) {
                markMediaFailed(pendingId, PendingMediaContext(
                    uris = photoUris.map { it.toString() }, isVideo = false,
                    caption = caption, replyToId = replyId, silent = silent
                ))
                return
            }
            uploadedUrls.add(url)
        }
        if (photoUris.size == 1) {
            messageRepo.sendPhotoMessage(chatId, uploadedUrls[0], replyId, caption, silent)
        } else {
            messageRepo.sendAlbumMessage(chatId, uploadedUrls, caption, replyId, silent)
        }
        // Mark placeholder as sent (isPending=false) with the CDN URL so the photo stays visible.
        // Do NOT filter/remove — that races with the Realtime delivery which may have already
        // replaced this item with the confirmed message. Realtime dedup will replace in-place.
        _messages.value = _messages.value.map { item ->
            if (item.message.id != pendingId) item
            else {
                val updatedMsg = if (uploadedUrls.size == 1)
                    item.message.copy(fileUrl = uploadedUrls[0])
                else
                    item.message.copy(photoUrls = uploadedUrls)
                item.copy(message = updatedMsg, isPending = false)
            }
        }
    }

    /**
     * Uploads video and sends the message. Suspends until complete or failed.
     * On failure marks the pending item as isFailed instead of removing it.
     * Must be called from Dispatchers.IO.
     */
    private suspend fun doUploadAndSendVideo(
        pendingId: String,
        uri: Uri,
        name: String,
        mimeType: String,
        caption: String?,
        replyId: String?,
        silent: Boolean,
        context: Context
    ) {
        val pendingContext = PendingMediaContext(
            uris = listOf(uri.toString()), isVideo = true,
            caption = caption, replyToId = replyId, silent = silent,
            mimeType = mimeType, originalFileName = name
        )
        val tempFile = try {
            copyVideoUriToCache(uri, name, context)
        } catch (e: IOException) {
            markMediaFailed(pendingId, pendingContext)
            _error.value = "Не удалось загрузить файл"
            return
        } catch (e: OutOfMemoryError) {
            markMediaFailed(pendingId, pendingContext)
            _error.value = "Не удалось загрузить файл. Попробуйте файл меньшего размера или повторите позже."
            return
        }
        val ext = name.substringAfterLast('.', "mp4").ifBlank { "mp4" }
        var uploadSucceeded = false
        try {
            val fileName = "video_${System.currentTimeMillis()}.$ext"
            val fileSize = tempFile.length()
            val useResumable = fileSize > VIDEO_RESUMABLE_THRESHOLD_BYTES
            val onProgress: (Float) -> Unit = { progress ->
                _uploadProgresses.value = listOf(progress.coerceIn(0f, 1f))
            }
            val url = if (useResumable) {
                messageRepo.uploadFileResumable(chatId, fileName, tempFile, onProgress)
            } else {
                messageRepo.uploadFileFromDisk(chatId, fileName, tempFile, onProgress)
            }
            if (url != null) {
                uploadSucceeded = true
                messageRepo.sendVideoMessage(chatId, url, name, fileSize, mimeType, replyId, caption, silent)
                // Mark as sent with CDN URL — same race-free pattern as photos
                _messages.value = _messages.value.map { item ->
                    if (item.message.id != pendingId) item
                    else item.copy(message = item.message.copy(fileUrl = url, content = caption), isPending = false)
                }
            } else {
                markMediaFailed(pendingId, pendingContext)
                _error.value = "Не удалось загрузить файл"
            }
        } catch (e: OutOfMemoryError) {
            markMediaFailed(pendingId, pendingContext)
            _error.value = "Не удалось загрузить файл. Попробуйте файл меньшего размера или повторите позже."
        } finally {
            if (uploadSucceeded) {
                Log.d("VideoUpload", "deleting temp after verified success")
            } else {
                Log.d("VideoUpload", "deleting temp after final failed/cancelled")
            }
            tempFile.delete()
        }
    }

    private fun copyVideoUriToCache(uri: Uri, name: String, context: Context): File {
        val ext = name.substringAfterLast('.', "mp4").ifBlank { "mp4" }
            .filter { it.isLetterOrDigit() }
            .ifBlank { "mp4" }
        val uploadDir = File(context.cacheDir, "video_uploads").apply { mkdirs() }
        val tempFile = File.createTempFile("upload_video_", ".$ext", uploadDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open selected video")
            input.use { source ->
                tempFile.outputStream().use { target ->
                    source.copyTo(target, bufferSize = VIDEO_UPLOAD_BUFFER_SIZE)
                }
            }
            Log.d(
                "VideoUpload",
                "temp created path=${tempFile.absolutePath} exists=${tempFile.exists()} size=${tempFile.length()}"
            )
            return tempFile
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * Retries a failed media upload. Validates the URI is still readable before
     * re-entering the upload flow. Updates the existing list item in-place
     * (no jumping to bottom, same stableKey).
     */
    fun retryFailedMediaMessage(localId: String, context: Context) {
        val item = _messages.value.find { it.message.id == localId } ?: return
        val ctx = item.pendingMediaContext ?: return

        // Validate URI still readable
        val firstUri = Uri.parse(ctx.uris.first())
        val readable = try {
            context.contentResolver.openInputStream(firstUri)?.close(); true
        } catch (_: Exception) { false }

        if (!readable) {
            _messages.value = _messages.value.filter { it.message.id != localId }
            _error.value = "Файл недоступен. Выберите заново."
            return
        }

        // Optimistic: restore to pending state
        _messages.value = _messages.value.map {
            if (it.message.id == localId) it.copy(isPending = true, isFailed = false, pendingMediaContext = null)
            else it
        }
        _uploadProgresses.value = List(ctx.uris.size) { 0f }

        val myId = currentUserId
        if (activeUploadCount.incrementAndGet() == 1) {
            val displayName = profileCache[myId]?.displayName ?: ""
            if (canUseForegroundNetwork()) {
                viewModelScope.launch { messageRepo.setTyping(chatId, myId, displayName, "uploading_media") }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            try {
                if (ctx.isVideo) {
                    Log.d("VideoUpload", "retry will recreate temp from original Uri")
                    doUploadAndSendVideo(
                        pendingId = localId,
                        uri = firstUri,
                        name = ctx.originalFileName ?: "video.mp4",
                        mimeType = ctx.mimeType ?: "video/mp4",
                        caption = ctx.caption,
                        replyId = ctx.replyToId,
                        silent = ctx.silent,
                        context = context
                    )
                } else {
                    doUploadAndSendPhotos(
                        pendingId = localId,
                        photoUris = ctx.uris.map { Uri.parse(it) },
                        caption = ctx.caption,
                        replyId = ctx.replyToId,
                        silent = ctx.silent,
                        context = context
                    )
                }
            } catch (e: OutOfMemoryError) {
                markMediaFailed(localId, ctx)
                _error.value = "Не удалось загрузить файл. Попробуйте файл меньшего размера или повторите позже."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                markMediaFailed(localId, ctx)
            } finally {
                _isSending.value = false
                _uploadProgresses.value = emptyList()
                if (activeUploadCount.decrementAndGet() == 0 && canUseForegroundNetwork()) {
                    messageRepo.clearTyping(chatId, myId)
                }
            }
        }
    }

    private fun compressImage(uri: Uri, context: Context): ByteArray? {
        return try {
            // Read bytes once — reuse for both EXIF and bitmap decoding
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null

            // Read EXIF orientation
            val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            // Apply rotation if needed
            val rotated = if (rotation != 0f) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
                android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    .also { if (it != original) original.recycle() }
            } else original

            val maxDim = 960
            val scaled = if (rotated.width > maxDim || rotated.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / rotated.width, maxDim.toFloat() / rotated.height)
                android.graphics.Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * ratio).toInt(),
                    (rotated.height * ratio).toInt(),
                    true
                ).also { if (it != rotated) rotated.recycle() }
            } else rotated

            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            if (scaled != rotated) scaled.recycle()
            out.toByteArray()
        } catch (e: Exception) { null }
    }

    fun clearError() { _error.value = null }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                messageRepo.deleteMessageForAll(messageId)
            } else {
                _messages.value = _messages.value.filter { it.message.id != messageId }
            }
        }
    }

    fun forwardMessage(messageId: String, toChatId: String) {
        viewModelScope.launch {
            messageRepo.forwardMessage(messageId, toChatId)
        }
    }

    // ── Selection mode ────────────────────────────────────────────────────────

    fun toggleSelection(messageId: String) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (messageId in current) current.remove(messageId) else current.add(messageId)
        _selectedMessageIds.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun loadChatsForForward() {
        viewModelScope.launch {
            _chatsForForward.value = chatRepo.getChatsForUser()
        }
    }

    fun forwardSelectedMessages(toChatId: String) {
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            ids.forEach { messageId -> messageRepo.forwardMessage(messageId, toChatId) }
        }
        clearSelection()
    }

    fun forwardSingleMessage(messageId: String, toChatId: String) {
        viewModelScope.launch {
            messageRepo.forwardMessage(messageId, toChatId)
        }
    }

    fun deleteSelectedMessages(forEveryone: Boolean) {
        val ids = _selectedMessageIds.value.toList()
        viewModelScope.launch {
            if (forEveryone) {
                ids.forEach { messageRepo.deleteMessageForAll(it) }
            } else {
                _messages.value = _messages.value.filter { it.message.id !in ids }
            }
        }
        clearSelection()
    }

    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMoreMessages.value) return
        if (!isOnline.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val current = _messages.value
            val oldestCreatedAt = current.firstOrNull()?.message?.createdAt
            val older = if (oldestCreatedAt != null) {
                messageRepo.getMessagesBefore(
                    chatId = chatId,
                    beforeCreatedAt = oldestCreatedAt,
                    limit = 30,
                    historyFrom = historyFrom
                )
            } else {
                messageRepo.getMessages(
                    chatId, limit = 30, offset = current.size, historyFrom = historyFrom
                )
            }
            if (older.isEmpty()) {
                _hasMoreMessages.value = false
            } else {
                if (older.size < 30) _hasMoreMessages.value = false
                // Добавляем ID старых входящих сообщений в "прочитанные" —
                // при скролле к истории они уже были просмотрены раньше,
                // поэтому не должны влиять на счётчик непрочитанных на FAB.
                val olderIncomingIds = older
                    .filter { it.senderId != currentUserId }
                    .map { it.id }
                    .toSet()
                if (olderIncomingIds.isNotEmpty()) {
                    _myReadMessageIds.value = _myReadMessageIds.value + olderIncomingIds
                }
                // Смещаем firstUnreadIndex: мы prepend'им older.size элементов,
                // поэтому все существующие индексы сдвигаются вниз.
                if (_firstUnreadIndex.value >= 0) {
                    _firstUnreadIndex.value += older.size
                }
                _messages.value = enrichMessages(older) + current
            }
            _isLoadingMore.value = false
        }
    }

    // ── Voice recording methods ───────────────────────────────────────────────

    fun startVoiceRecording() {
        if (_voiceRecordState.value != VoiceRecordState.Idle) return
        pauseVoice() // stop any playing voice
        val started = voiceRecorder.start()
        if (!started) { _error.value = "Не удалось начать запись"; return }
        _voiceRecordState.value = VoiceRecordState.Recording()
        voiceTimerJob?.cancel()
        voiceTimerJob = viewModelScope.launch {
            while (voiceRecorder.isRecording) {
                _voiceElapsedMs.value = voiceRecorder.elapsedMs
                delay(100)
            }
        }
    }

    fun lockRecording() {
        val s = _voiceRecordState.value
        if (s is VoiceRecordState.Recording && !s.isLocked) {
            _voiceRecordState.value = VoiceRecordState.Recording(isLocked = true)
        }
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        voiceRecorder.cancel()
        _voiceRecordState.value = VoiceRecordState.Idle
        _voiceElapsedMs.value = 0L
    }

    fun sendVoiceRecording(context: android.content.Context, silent: Boolean = false) {
        if (voiceRecorder.elapsedMs < 500L) { cancelVoiceRecording(); return }
        voiceTimerJob?.cancel()
        _voiceRecordState.value = VoiceRecordState.Idle
        _voiceElapsedMs.value = 0L
        val result = voiceRecorder.stop() ?: return
        val (file, durationSec, amplitudeSamples) = result
        val waveformData = amplitudeSamples.encodeWaveform()
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        val myId = currentUserId
        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()
        val pendingMsg = Message(id = pendingId, chatId = chatId, senderId = myId,
            type = "voice", duration = durationSec, createdAt = now)
        _messages.value = _messages.value + MessageUiItem(
            message = pendingMsg, senderProfile = profileCache[myId],
            isOwn = true, isRead = false, isPending = true)
        _scrollToOwnMessageEvent.value++
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (draftTargetUserId != null && !ensureChatCreated()) {
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                    _error.value = "Не удалось создать чат"
                    return@launch
                }
                val bytes = file.readBytes(); file.delete()
                val url = messageRepo.uploadFile(chatId, "voice_${System.currentTimeMillis()}.m4a", bytes)
                if (url != null) {
                    messageRepo.sendVoiceMessage(chatId, url, durationSec, waveformData, replyId, silent)
                    // Mark as sent but keep in list — realtime will replace in-place (same stableKey),
                    // preventing a remove+insert that would trigger the entrance animation twice.
                    _messages.value = _messages.value.map {
                        if (it.message.id == pendingId) it.copy(isPending = false) else it
                    }
                } else {
                    _messages.value = _messages.value.filter { it.message.id != pendingId }
                    _error.value = "Ошибка загрузки голосового"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ChatVM", "sendVoiceRecording exception: ${e.message}", e)
                _messages.value = _messages.value.filter { it.message.id != pendingId }
                _error.value = "Ошибка: ${e.message}"
            }
        }
    }

    // ── Voice playback methods ────────────────────────────────────────────────

    fun playVoice(messageId: String, url: String, durationSec: Int) {
        val item = _messages.value.find { it.message.id == messageId }
        // Mark as listened when the recipient presses play (mirrors Telegram behaviour)
        if (item != null && !item.isOwn && !item.isListened) {
            _messages.value = _messages.value.map {
                if (it.message.id == messageId) it.copy(isListened = true) else it
            }
            viewModelScope.launch(Dispatchers.IO) {
                messageRepo.markVoiceListened(messageId)
            }
        }
        val title = item?.senderProfile?.displayName ?: "Голосовое сообщение"
        val queue = _messages.value.mapNotNull { messageItem ->
            val message = messageItem.message
            val voiceUrl = message.fileUrl ?: return@mapNotNull null
            if (message.type != "voice") return@mapNotNull null
            VoiceQueueItem(
                messageId = message.id,
                chatId = message.chatId,
                url = voiceUrl,
                title = messageItem.senderProfile?.displayName ?: "Голосовое сообщение",
                durationSec = message.duration ?: 0
            )
        }
        app.globalVoicePlayer.play(messageId, url, durationSec, title, queue)
    }

    fun pauseVoice() = app.globalVoicePlayer.pause()

    fun resumeVoice() = app.globalVoicePlayer.resume()

    fun seekVoice(positionMs: Int) = app.globalVoicePlayer.seek(positionMs)

    fun stopVoice() = app.globalVoicePlayer.stop()

    // ── Notifications mute ────────────────────────────────────────────────────

    fun toggleMute() {
        viewModelScope.launch {
            val newMuted = !_isMuted.value
            _isMuted.value = newMuted
            app.themeManager.setChatMuted(chatId, newMuted)
            chatRepo.setMuted(chatId, newMuted)
        }
    }

    // ── Leave group / Delete personal chat ────────────────────────────────────

    fun leaveGroup(onLeft: () -> Unit) {
        viewModelScope.launch {
            val myName = profileCache[currentUserId]?.displayName ?: "Пользователь"
            messageRepo.sendSystemMessage(chatId, "$myName вышел(а) из чата")
            chatRepo.removeMember(chatId, currentUserId)
            onLeft()
        }
    }

    fun deletePersonalChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            chatRepo.markAsLeft(chatId)
            onDeleted()
        }
    }

}

/**
 * Encodes raw MediaRecorder amplitude samples into a compact 40-char string (digits 0–9).
 * Used for waveform visualization in voice message bubbles.
 * Returns empty string if input is empty or all-zero.
 */
private fun List<Int>.encodeWaveform(bars: Int = 40): String {
    if (isEmpty()) return ""
    val max = maxOrNull()?.takeIf { it > 0 } ?: return ""
    val step = size.toFloat() / bars
    return (0 until bars).joinToString("") { i ->
        val idx = (i * step).toInt().coerceIn(0, size - 1)
        ((this[idx].toFloat() / max) * 9).toInt().coerceIn(0, 9).toString()
    }
}
