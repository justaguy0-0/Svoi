package com.example.svoi.ui.profile

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Button
import com.example.svoi.ui.components.SvoiLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.svoi.util.toRegistrationDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: UserProfileViewModel = viewModel()
) {
    LaunchedEffect(userId) { viewModel.load(userId) }

    val profile by viewModel.profile.collectAsState()
    val presence by viewModel.presence.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val chatIdWithUser by viewModel.chatIdWithUser.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Entrance animation
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) appeared = true }
    val avatarScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = tween(350),
        label = "avatarScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(400),
        label = "contentAlpha"
    )

    // ── Media state ───────────────────────────────────────────────────────────
    val mediaViewModel: ChatMediaViewModel = viewModel()
    LaunchedEffect(chatIdWithUser) { chatIdWithUser?.let { mediaViewModel.load(it) } }
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
                title = {},
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { SvoiLoader() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                // ── Profile header (scrolls away) ──────────────────────────────
                item(key = "profile_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SvoiDimens.ScreenHorizontalPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))

                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.scale(avatarScale)
                        ) {
                            Avatar(
                                emoji = profile?.emoji ?: "😊",
                                bgColor = profile?.bgColor ?: "#5C6BC0",
                                letter = profile?.displayName ?: "",
                                size = SvoiDimens.AvatarXLarge,
                                fontSize = 44.sp
                            )
                            val isOnline = presence?.isTrulyOnline() == true
                            if (isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(OnlineGreen, CircleShape)
                                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = profile?.displayName ?: "Пользователь",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        val isOnline = presence?.isTrulyOnline() == true
                        Text(
                            text = if (isOnline) "в сети" else "не в сети",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!profile?.statusText.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = profile?.statusText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        val regDate = profile?.createdAt?.toRegistrationDate()
                        if (!regDate.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = SvoiShapes.Chip,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "В Свои с $regDate",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    val chatId = viewModel.getOrCreateChat(userId)
                                    if (chatId != null) onOpenChat(chatId)
                                }
                            },
                            shape = SvoiShapes.Button,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SvoiDimens.ButtonHeight)
                        ) {
                            Icon(
                                Icons.Default.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Написать", style = MaterialTheme.typography.titleSmall)
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── Media section (only when chat exists) ──────────────────────
                if (chatIdWithUser != null) {
                    stickyHeader(key = "media_tab_bar") {
                        HorizontalDivider()
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
                            ) { SvoiLoader() }
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

                    item(key = "media_bottom_spacer") { Spacer(Modifier.height(16.dp)) }
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
}
