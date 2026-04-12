package com.example.svoi.ui.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import com.example.svoi.ui.components.SvoiLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.svoi.ui.chat.FullscreenVideoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PHOTO_GAP = 2.dp
private val VIDEO_GAP = 4.dp

// ── Standalone screen (kept for potential future use) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMediaScreen(
    chatId: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вложения", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        AttachmentsPane(
            chatId = chatId,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

// ── Embeddable pane ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AttachmentsPane(
    chatId: String,
    modifier: Modifier = Modifier,
    mediaViewModel: ChatMediaViewModel = viewModel()
) {
    LaunchedEffect(chatId) { if (chatId.isNotEmpty()) mediaViewModel.load(chatId) }

    val photos by mediaViewModel.photos.collectAsState()
    val videos by mediaViewModel.videos.collectAsState()
    val voices by mediaViewModel.voices.collectAsState()
    val totalPhotos by mediaViewModel.totalPhotos.collectAsState()
    val totalVideos by mediaViewModel.totalVideos.collectAsState()
    val totalVoices by mediaViewModel.totalVoices.collectAsState()
    val isLoading by mediaViewModel.isLoading.collectAsState()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val context = LocalContext.current

    // ── Photo lightbox ────────────────────────────────────────────────────
    val allPhotoUrls = remember(photos) {
        photos.flatMap { s -> s.items.filterIsInstance<MediaItem.Photo>().map { it.url } }
    }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    // ── Video fullscreen ──────────────────────────────────────────────────
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }

    // ── Voice player ──────────────────────────────────────────────────────
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

    // ── Layout ────────────────────────────────────────────────────────────
    Column(modifier = modifier) {
        // Tab row
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            listOf(
                "Фото" to totalPhotos,
                "Видео" to totalVideos,
                "Голосовые" to totalVoices
            ).forEachIndexed { index, (label, count) ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (pagerState.currentPage == index)
                                    FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (count > 0) {
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                SvoiLoader(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> PhotosTab(
                            sections = photos,
                            allUrls = allPhotoUrls,
                            onPhotoClick = { idx -> lightboxIndex = idx }
                        )
                        1 -> VideosTab(
                            sections = videos,
                            onVideoClick = { url -> fullscreenVideoUrl = url }
                        )
                        2 -> VoicesTab(
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

    // ── Fullscreen video ──────────────────────────────────────────────────
    fullscreenVideoUrl?.let { url ->
        FullscreenVideoPlayer(url = url, onDismiss = { fullscreenVideoUrl = null })
    }

    // ── Photo lightbox ────────────────────────────────────────────────────
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
}

// ── Photos Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosTab(
    sections: List<MediaSection>,
    allUrls: List<String>,
    onPhotoClick: (Int) -> Unit
) {
    if (sections.isEmpty()) { MediaEmptyState("Нет фотографий"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "photo_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val sectionPhotos = section.items.filterIsInstance<MediaItem.Photo>()
            val rows = sectionPhotos.chunked(3)
            itemsIndexed(rows, key = { rowIdx, _ ->
                "photo_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
            }) { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PHOTO_GAP),
                    horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP)
                ) {
                    row.forEach { item ->
                        val globalIdx = allUrls.indexOf(item.url)
                        PhotoThumbnail(
                            url = item.url,
                            modifier = Modifier.weight(1f),
                            onClick = { if (globalIdx >= 0) onPhotoClick(globalIdx) }
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(PHOTO_GAP))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PhotoThumbnail(url: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// ── Videos Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideosTab(sections: List<MediaSection>, onVideoClick: (String) -> Unit) {
    if (sections.isEmpty()) { MediaEmptyState("Нет видеозаписей"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "video_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val sectionVideos = section.items.filterIsInstance<MediaItem.Video>()
            val rows = sectionVideos.chunked(2)
            itemsIndexed(rows, key = { rowIdx, _ ->
                "video_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
            }) { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = VIDEO_GAP),
                    horizontalArrangement = Arrangement.spacedBy(VIDEO_GAP)
                ) {
                    row.forEach { item ->
                        VideoThumbnail(
                            url = item.url,
                            duration = item.duration,
                            modifier = Modifier.weight(1f),
                            onClick = { onVideoClick(item.url) }
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(VIDEO_GAP))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VideoThumbnail(
    url: String,
    duration: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Box(
            modifier = Modifier.align(Alignment.Center).size(38.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Воспроизвести",
                tint = Color.White, modifier = Modifier.size(24.dp))
        }
        if (duration != null) {
            val mins = duration / 60; val secs = duration % 60
            Text(
                text = "$mins:${secs.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

// ── Voices Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoicesTab(
    sections: List<MediaSection>,
    playingUrl: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: (String) -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    if (sections.isEmpty()) { MediaEmptyState("Нет голосовых сообщений"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "voice_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val sectionVoices = section.items.filterIsInstance<MediaItem.Voice>()
            itemsIndexed(sectionVoices, key = { _, item -> "voice_${item.messageId}" }) { _, item ->
                VoiceItem(
                    item = item,
                    isPlaying = playingUrl == item.url && isPlaying,
                    positionMs = if (playingUrl == item.url) positionMs else 0L,
                    durationMs = if (playingUrl == item.url && durationMs > 0) durationMs
                                 else ((item.duration ?: 0) * 1000L).coerceAtLeast(1000L),
                    onPlay = { onPlay(item.url) },
                    onPause = onPause,
                    onSeek = onSeek
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VoiceItem(
    item: MediaItem.Voice,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val displaySec = if (positionMs > 0) (positionMs / 1000).toInt() else item.duration ?: 0
    val timeStr = run { val m = displaySec / 60; val s = displaySec % 60; "$m:${s.toString().padStart(2, '0')}" }
    val dateText = runCatching {
        val instant = Instant.parse(item.createdAt)
        DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
            .withZone(ZoneId.systemDefault()).format(instant)
    }.getOrDefault("")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { if (isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Голосовое сообщение",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Slider(
                value = progress,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(28.dp).offset(y = (-2).dp)
            )
            Text(
                dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = (-6).dp)
            )
        }
    }
}

// ── Photo Lightbox ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaImageLightbox(
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { urls.size })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
            ) { page ->
                SubcomposeAsyncImage(
                    model = urls[page],
                    contentDescription = "Изображение",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(48.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Icon(Icons.Default.BrokenImage, null,
                            tint = Color.White.copy(0.5f),
                            modifier = Modifier.align(Alignment.Center).size(48.dp))
                    }
                )
            }
            if (urls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentUrl = urls.getOrElse(pagerState.currentPage) { urls.first() }
                IconButton(onClick = { onDownload(currentUrl) }) {
                    Icon(Icons.Default.Download, "Скачать", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

// ── Shared composables ──────────────────────────────────────────────────────

// ── Internal API for LazyColumn embedding ─────────────────────────────────

@Composable
internal fun MediaTypeTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    totalPhotos: Int,
    totalVideos: Int,
    totalVoices: Int
) {
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
        listOf("Фото" to totalPhotos, "Видео" to totalVideos, "Голосовые" to totalVoices)
            .forEachIndexed { index, (label, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (count > 0) {
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == index)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.addPhotoSections(
    sections: List<MediaSection>,
    allUrls: List<String>,
    onPhotoClick: (Int) -> Unit
) {
    if (sections.isEmpty()) {
        item(key = "photo_empty") {
            MediaEmptyState("Нет фотографий", Modifier.fillMaxWidth().height(200.dp))
        }
        return
    }
    sections.forEach { section ->
        stickyHeader(key = "photo_hdr_${section.yearLabel}_${section.monthLabel}") {
            MonthHeader(section.monthLabel, section.yearLabel)
        }
        val rows = section.items.filterIsInstance<MediaItem.Photo>().chunked(3)
        itemsIndexed(rows, key = { rowIdx, _ ->
            "photo_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
        }) { _, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PHOTO_GAP),
                horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP)
            ) {
                row.forEach { item ->
                    val globalIdx = allUrls.indexOf(item.url)
                    PhotoThumbnail(item.url, Modifier.weight(1f)) {
                        if (globalIdx >= 0) onPhotoClick(globalIdx)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(PHOTO_GAP))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.addVideoSections(
    sections: List<MediaSection>,
    onVideoClick: (String) -> Unit
) {
    if (sections.isEmpty()) {
        item(key = "video_empty") {
            MediaEmptyState("Нет видеозаписей", Modifier.fillMaxWidth().height(200.dp))
        }
        return
    }
    sections.forEach { section ->
        stickyHeader(key = "video_hdr_${section.yearLabel}_${section.monthLabel}") {
            MonthHeader(section.monthLabel, section.yearLabel)
        }
        val rows = section.items.filterIsInstance<MediaItem.Video>().chunked(2)
        itemsIndexed(rows, key = { rowIdx, _ ->
            "video_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
        }) { _, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = VIDEO_GAP),
                horizontalArrangement = Arrangement.spacedBy(VIDEO_GAP)
            ) {
                row.forEach { item ->
                    VideoThumbnail(item.url, item.duration, Modifier.weight(1f)) {
                        onVideoClick(item.url)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(VIDEO_GAP))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.addVoiceSections(
    sections: List<MediaSection>,
    playingUrl: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlay: (String) -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    if (sections.isEmpty()) {
        item(key = "voice_empty") {
            MediaEmptyState("Нет голосовых сообщений", Modifier.fillMaxWidth().height(200.dp))
        }
        return
    }
    sections.forEach { section ->
        stickyHeader(key = "voice_hdr_${section.yearLabel}_${section.monthLabel}") {
            MonthHeader(section.monthLabel, section.yearLabel)
        }
        val voices = section.items.filterIsInstance<MediaItem.Voice>()
        itemsIndexed(voices, key = { _, item -> "voice_${item.messageId}" }) { _, item ->
            VoiceItem(
                item = item,
                isPlaying = playingUrl == item.url && isPlaying,
                positionMs = if (playingUrl == item.url) positionMs else 0L,
                durationMs = if (playingUrl == item.url && durationMs > 0) durationMs
                             else ((item.duration ?: 0) * 1000L).coerceAtLeast(1000L),
                onPlay = { onPlay(item.url) },
                onPause = onPause,
                onSeek = onSeek
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
internal fun MonthHeader(monthLabel: String, yearLabel: String) {
    val currentYear = remember { java.time.LocalDate.now().year.toString() }
    val displayLabel = if (yearLabel == currentYear) monthLabel else "$monthLabel $yearLabel"
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaEmptyState(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.BrokenImage, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
