package com.example.svoi.ui.media

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.svoi.util.toMessageTime
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PHOTO_GAP = 2.dp
private val VIDEO_GAP = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMediaScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatMediaViewModel = viewModel()
) {
    LaunchedEffect(chatId) { viewModel.load(chatId) }

    val photos by viewModel.photos.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val voices by viewModel.voices.collectAsState()
    val totalPhotos by viewModel.totalPhotos.collectAsState()
    val totalVideos by viewModel.totalVideos.collectAsState()
    val totalVoices by viewModel.totalVoices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Lightbox state for full-screen photo view
    var lightboxUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column {
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
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { page ->
                when (page) {
                    0 -> PhotosTab(
                        sections = photos,
                        onPhotoClick = { url -> lightboxUrl = url }
                    )
                    1 -> VideosTab(sections = videos)
                    2 -> VoicesTab(sections = voices)
                }
            }
        }
    }

    // Full-screen photo lightbox
    lightboxUrl?.let { url ->
        Dialog(
            onDismissRequest = { lightboxUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { lightboxUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { lightboxUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ── Photos Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosTab(
    sections: List<MediaSection>,
    onPhotoClick: (String) -> Unit
) {
    if (sections.isEmpty()) {
        MediaEmptyState("Нет фотографий")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "photo_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val photos = section.items.filterIsInstance<MediaItem.Photo>()
            val rows = photos.chunked(3)
            itemsIndexed(
                rows,
                key = { rowIdx, _ ->
                    "photo_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
                }
            ) { _, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PHOTO_GAP),
                    horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP)
                ) {
                    row.forEach { item ->
                        PhotoThumbnail(
                            url = item.url,
                            modifier = Modifier.weight(1f),
                            onClick = { onPhotoClick(item.url) }
                        )
                    }
                    // Fill empty cells in last row
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(PHOTO_GAP))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PhotoThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
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
private fun VideosTab(sections: List<MediaSection>) {
    if (sections.isEmpty()) {
        MediaEmptyState("Нет видеозаписей")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "video_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val videos = section.items.filterIsInstance<MediaItem.Video>()
            val rows = videos.chunked(2)
            itemsIndexed(
                rows,
                key = { rowIdx, _ ->
                    "video_row_${section.yearLabel}_${section.monthLabel}_$rowIdx"
                }
            ) { _, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VIDEO_GAP),
                    horizontalArrangement = Arrangement.spacedBy(VIDEO_GAP)
                ) {
                    row.forEach { item ->
                        VideoThumbnail(
                            url = item.url,
                            duration = item.duration,
                            modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
        // Play icon
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Воспроизвести",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        // Duration
        if (duration != null) {
            val mins = duration / 60
            val secs = duration % 60
            Text(
                text = "$mins:${secs.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

// ── Voices Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoicesTab(sections: List<MediaSection>) {
    if (sections.isEmpty()) {
        MediaEmptyState("Нет голосовых сообщений")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "voice_hdr_${section.yearLabel}_${section.monthLabel}") {
                MonthHeader(section.monthLabel, section.yearLabel)
            }
            val voices = section.items.filterIsInstance<MediaItem.Voice>()
            itemsIndexed(
                voices,
                key = { _, item -> "voice_${item.messageId}" }
            ) { _, item ->
                VoiceItem(item)
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VoiceItem(item: MediaItem.Voice) {
    val durationText = item.duration?.let { d ->
        val m = d / 60; val s = d % 60; "$m:${s.toString().padStart(2, '0')}"
    } ?: "—"

    val dateText = runCatching {
        val instant = Instant.parse(item.createdAt)
        val zone = ZoneId.systemDefault()
        val date = DateTimeFormatter.ofPattern("d MMMM", Locale("ru")).withZone(zone).format(instant)
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
        "$date, $time"
    }.getOrDefault("")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.KeyboardVoice,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Голосовое сообщение",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            durationText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ── Shared composables ──────────────────────────────────────────────────────

@Composable
private fun MonthHeader(monthLabel: String, yearLabel: String) {
    val currentYear = remember {
        java.time.LocalDate.now().year.toString()
    }
    val displayLabel = if (yearLabel == currentYear) monthLabel
                       else "$monthLabel $yearLabel"
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
private fun MediaEmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
