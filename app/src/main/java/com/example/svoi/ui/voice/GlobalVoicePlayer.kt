package com.example.svoi.ui.voice

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.svoi.data.local.VoicePlaybackSpeed
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GlobalVoiceState(
    val messageId: String,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val playbackSpeed: VoicePlaybackSpeed,
    val downloadProgress: Float = -1f
)

data class VoiceQueueItem(
    val messageId: String,
    val chatId: String,
    val url: String,
    val title: String,
    val durationSec: Int
)

/**
 * Singleton voice player that lives on SvoiApp.
 * Survives ViewModel lifecycle and keeps the active voice queue across screens.
 *
 * Voice files are cached locally in [cacheDir] as voice_<messageId>.m4a.
 * On first play the file is downloaded; subsequent plays, including offline, use the local copy.
 */
class GlobalVoicePlayer(
    context: Context,
    private val cacheDir: File,
    initialSpeed: VoicePlaybackSpeed = VoicePlaybackSpeed.NORMAL,
    private val onSpeedChanged: (VoicePlaybackSpeed) -> Unit = {}
) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<GlobalVoiceState?>(null)
    val state: StateFlow<GlobalVoiceState?> = _state

    private val _cachedVoiceIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedVoiceIds: StateFlow<Set<String>> = _cachedVoiceIds

    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val pendingControllerActions = mutableListOf<(MediaController) -> Unit>()
    private var progressJob: Job? = null
    private var prepareJob: Job? = null
    private var playbackSpeed: VoicePlaybackSpeed = initialSpeed
    private var currentQueue: List<VoiceQueueItem> = emptyList()
    private var currentIndex: Int = -1
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateStateFromController()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStateFromController()
            if (isPlaying) startProgressUpdates() else progressJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStateFromController()
            if (playbackState == Player.STATE_ENDED) {
                stopPlayback(clearQueue = true, releaseController = true)
            }
        }
    }

    init {
        val existing = cacheDir.listFiles()
            ?.filter { it.name.startsWith("voice_") && it.name.endsWith(".m4a") && !it.name.endsWith("_tmp.m4a") && it.length() > 0 }
            ?.mapNotNull { f ->
                val name = f.nameWithoutExtension
                if (name.length > 6) name.removePrefix("voice_") else null
            }
            ?.toSet() ?: emptySet()
        _cachedVoiceIds.value = existing
    }

    fun play(
        messageId: String,
        url: String,
        durationSec: Int,
        title: String,
        queue: List<VoiceQueueItem> = emptyList()
    ) {
        val cur = _state.value
        if (cur?.messageId == messageId) {
            if (cur.isPlaying) pause() else resume()
            return
        }

        val fallbackItem = VoiceQueueItem(
            messageId = messageId,
            chatId = "",
            url = url,
            title = title,
            durationSec = durationSec
        )
        val nextQueue = queue.filter { it.url.isNotBlank() }.ifEmpty { listOf(fallbackItem) }
        currentQueue = nextQueue
        currentIndex = nextQueue.indexOfFirst { it.messageId == messageId }
            .takeIf { it >= 0 }
            ?: 0
        playQueueItem(nextQueue.getOrElse(currentIndex) { fallbackItem })
    }

    fun pause() {
        withController { it.pause() }
        progressJob?.cancel()
        _state.value = _state.value?.copy(isPlaying = false)
    }

    fun resume() {
        val cur = _state.value ?: return
        withController { controller ->
            applyPlaybackSpeed(controller)
            controller.play()
        }
        _state.value = cur.copy(isPlaying = true, playbackSpeed = playbackSpeed)
        startProgressUpdates()
    }

    fun setPlaybackSpeed(speed: VoicePlaybackSpeed) {
        playbackSpeed = speed
        onSpeedChanged(speed)
        mediaController?.let { applyPlaybackSpeed(it) }
        _state.value = _state.value?.copy(playbackSpeed = speed)
    }

    fun seek(positionMs: Int) {
        withController { it.seekTo(positionMs.toLong()) }
        _state.value = _state.value?.copy(positionMs = positionMs)
    }

    fun stop() {
        stopPlayback(clearQueue = true, releaseController = true)
    }

    private fun playQueueItem(item: VoiceQueueItem) {
        stopPlayback(clearQueue = false, releaseController = false)
        val isCached = File(cacheDir, "voice_${item.messageId}.m4a").let { it.exists() && it.length() > 0 }
        _state.value = GlobalVoiceState(
            item.messageId,
            item.title,
            false,
            0,
            item.durationSec * 1000,
            playbackSpeed,
            downloadProgress = if (isCached) -1f else 0f
        )
        Log.d("VoicePlayer", "play: start messageId=${item.messageId} isCached=$isCached")
        prepareJob?.cancel()
        prepareJob = scope.launch(Dispatchers.IO) {
            try {
                var lastLoggedPct = -1
                val localPath = resolveLocalFile(item.messageId, item.url) { progress ->
                    if (_state.value?.messageId == item.messageId) {
                        _state.value = _state.value?.copy(downloadProgress = progress)
                        val pct = (progress * 100).toInt()
                        if (pct / 10 > lastLoggedPct / 10) {
                            lastLoggedPct = pct
                            Log.d("VoicePlayer", "download progress: $pct% for ${item.messageId}")
                        }
                    }
                }
                if (_state.value?.messageId != item.messageId) {
                    return@launch
                }
                _state.value = _state.value?.copy(downloadProgress = -1f)
                withContext(Dispatchers.Main) {
                    val startIndex = currentQueue.indexOfFirst { it.messageId == item.messageId }
                        .takeIf { it >= 0 }
                        ?: currentIndex.coerceAtLeast(0)
                    val mediaItems = currentQueue.map { queueItem ->
                        queueItem.toMediaItem(
                            uri = if (queueItem.messageId == item.messageId && localPath != null) {
                                Uri.fromFile(File(localPath))
                            } else {
                                cachedOrRemoteUri(queueItem)
                            }
                        )
                    }
                    withController { controller ->
                        controller.setMediaItems(mediaItems, startIndex, 0L)
                        applyPlaybackSpeed(controller)
                        controller.prepare()
                        controller.play()
                    }
                    _state.value = GlobalVoiceState(
                        item.messageId,
                        item.title,
                        true,
                        0,
                        item.durationSec * 1000,
                        playbackSpeed
                    )
                    startProgressUpdates()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VoicePlayer", "play: error for ${item.messageId} - ${e::class.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    stopPlayback(clearQueue = false, releaseController = false)
                    playNextInQueueOrStop()
                }
            }
        }
    }

    private fun playNextInQueueOrStop() {
        val nextIndex = currentIndex + 1
        val next = currentQueue.getOrNull(nextIndex)
        if (next == null) {
            stopPlayback(clearQueue = true, releaseController = true)
            return
        }
        currentIndex = nextIndex
        playQueueItem(next)
    }

    private fun stopPlayback(clearQueue: Boolean, releaseController: Boolean) {
        prepareJob?.cancel()
        progressJob?.cancel()
        mediaController?.run {
            stop()
            clearMediaItems()
        }
        _state.value = null
        if (clearQueue) {
            currentQueue = emptyList()
            currentIndex = -1
        }
        if (releaseController) {
            releaseController()
        }
    }

    private fun applyPlaybackSpeed(controller: MediaController) {
        try {
            controller.playbackParameters = PlaybackParameters(playbackSpeed.value, 1.0f)
        } catch (e: Exception) {
            Log.w("VoicePlayer", "playback speed unsupported: ${e::class.simpleName}: ${e.message}")
            playbackSpeed = VoicePlaybackSpeed.NORMAL
            onSpeedChanged(playbackSpeed)
            _state.value = _state.value?.copy(playbackSpeed = playbackSpeed)
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_state.value?.isPlaying == true) {
                updateStateFromController()
                delay(100)
            }
        }
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        val duration = controller.duration.takeIf { it > 0L }?.toInt()
            ?: _state.value?.durationMs
            ?: 0
        val messageId = item.mediaId
        val title = item.mediaMetadata.artist?.toString()
            ?: _state.value?.title
            ?: ""
        currentIndex = currentQueue.indexOfFirst { it.messageId == messageId }
        _state.value = GlobalVoiceState(
            messageId = messageId,
            title = title,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L).toInt(),
            durationMs = duration,
            playbackSpeed = playbackSpeed
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) {
            action(controller)
            return
        }
        pendingControllerActions += action
        connectController()
    }

    private fun connectController() {
        if (mediaController != null || controllerFuture != null) return
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, VoiceMessagePlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(controllerListener)
                    val actions = pendingControllerActions.toList()
                    pendingControllerActions.clear()
                    actions.forEach { it(controller) }
                } catch (e: Exception) {
                    pendingControllerActions.clear()
                    Log.e("VoicePlayer", "controller connection failed: ${e::class.simpleName}: ${e.message}")
                    _state.value = null
                } finally {
                    controllerFuture = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseController() {
        mediaController?.removeListener(controllerListener)
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        pendingControllerActions.clear()
    }

    private fun VoiceQueueItem.toMediaItem(uri: Uri): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(messageId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Голосовое сообщение")
                    .setDisplayTitle("Голосовое сообщение")
                    .setArtist(title)
                    .setSubtitle("Свои")
                    .setDescription("Свои")
                    .build()
            )
            .build()

    private fun cachedOrRemoteUri(item: VoiceQueueItem): Uri {
        val cached = File(cacheDir, "voice_${item.messageId}.m4a")
        return if (cached.exists() && cached.length() > 0) {
            Uri.fromFile(cached)
        } else {
            Uri.parse(item.url)
        }
    }

    private fun resolveLocalFile(
        messageId: String,
        url: String,
        onProgress: (Float) -> Unit = {}
    ): String? {
        val file = File(cacheDir, "voice_$messageId.m4a")
        if (file.exists() && file.length() > 0) return file.absolutePath
        return try {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "voice_${messageId}_tmp.m4a")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            Log.d("VoiceCache", "download: connecting for $messageId url=${url.takeLast(60)}")
            conn.connect()
            val contentLength = conn.contentLength.toLong()
            Log.d("VoiceCache", "download: connected, contentLength=${contentLength}B for $messageId")
            val downloadStart = System.currentTimeMillis()
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var totalBytes = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        totalBytes += n
                        if (contentLength > 0L) {
                            onProgress((totalBytes.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                        val elapsed = System.currentTimeMillis() - downloadStart
                        if (elapsed > 180_000L) {
                            Log.w("VoiceCache", "download: total timeout (${elapsed}ms) for $messageId after ${totalBytes}B")
                            throw IOException("voice download total timeout after ${elapsed}ms")
                        }
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - downloadStart
            tmp.renameTo(file)
            Log.d("VoiceCache", "download: done voice_$messageId.m4a (${file.length()}B) in ${elapsed}ms")
            _cachedVoiceIds.value = _cachedVoiceIds.value + messageId
            file.absolutePath
        } catch (e: Exception) {
            Log.w("VoiceCache", "download failed for $messageId, will stream: ${e::class.simpleName}: ${e.message}")
            null
        }
    }
}
