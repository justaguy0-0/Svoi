package com.example.svoi.ui.voice

import android.media.MediaPlayer
import android.util.Log
import com.example.svoi.data.local.VoicePlaybackSpeed
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
    private val cacheDir: File,
    initialSpeed: VoicePlaybackSpeed = VoicePlaybackSpeed.NORMAL,
    private val onSpeedChanged: (VoicePlaybackSpeed) -> Unit = {}
) {

    private val _state = MutableStateFlow<GlobalVoiceState?>(null)
    val state: StateFlow<GlobalVoiceState?> = _state

    private val _cachedVoiceIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedVoiceIds: StateFlow<Set<String>> = _cachedVoiceIds

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var playbackSpeed: VoicePlaybackSpeed = initialSpeed
    private var isPlayerPrepared: Boolean = false
    private var currentQueue: List<VoiceQueueItem> = emptyList()
    private var currentIndex: Int = -1
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        progressJob?.cancel()
        _state.value = _state.value?.copy(isPlaying = false)
    }

    fun resume() {
        val cur = _state.value ?: return
        try {
            mediaPlayer?.let { player ->
                applyPlaybackSpeed(player)
                player.start()
            }
        } catch (_: Exception) { return }
        _state.value = cur.copy(isPlaying = true, playbackSpeed = playbackSpeed)
        startProgressUpdates()
    }

    fun setPlaybackSpeed(speed: VoicePlaybackSpeed) {
        playbackSpeed = speed
        onSpeedChanged(speed)
        mediaPlayer?.let { applyPlaybackSpeed(it) }
        _state.value = _state.value?.copy(playbackSpeed = speed)
    }

    fun seek(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _state.value = _state.value?.copy(positionMs = positionMs)
    }

    fun stop() {
        stopPlayback(clearQueue = true)
    }

    private fun playQueueItem(item: VoiceQueueItem) {
        stopPlayback(clearQueue = false)
        val player = MediaPlayer()
        isPlayerPrepared = false
        mediaPlayer = player
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
        Log.d("VoicePlayer", "play: start messageId=${item.messageId} isCached=$isCached url=${item.url.takeLast(60)}")
        scope.launch(Dispatchers.IO) {
            try {
                var lastLoggedPct = -1
                val localPath = resolveLocalFile(item.messageId, item.url) { progress ->
                    if (mediaPlayer === player) {
                        _state.value = _state.value?.copy(downloadProgress = progress)
                        val pct = (progress * 100).toInt()
                        if (pct / 10 > lastLoggedPct / 10) {
                            lastLoggedPct = pct
                            Log.d("VoicePlayer", "download progress: $pct% for ${item.messageId}")
                        }
                    }
                }
                if (mediaPlayer !== player) {
                    Log.d("VoicePlayer", "play: player replaced, aborting for ${item.messageId}")
                    return@launch
                }
                _state.value = _state.value?.copy(downloadProgress = -1f)
                if (localPath != null) {
                    Log.d("VoicePlayer", "play: setDataSource LOCAL $localPath")
                    player.setDataSource(localPath)
                } else {
                    Log.d("VoicePlayer", "play: setDataSource STREAM ${item.url.takeLast(60)}")
                    player.setDataSource(item.url)
                }
                Log.d("VoicePlayer", "play: calling prepare() for ${item.messageId}")
                player.prepare()
                isPlayerPrepared = true
                val dur = player.duration.takeIf { it > 0 } ?: (item.durationSec * 1000)
                Log.d("VoicePlayer", "play: prepared OK, duration=${dur}ms for ${item.messageId}")
                withContext(Dispatchers.Main) {
                    applyPlaybackSpeed(player)
                    _state.value = GlobalVoiceState(item.messageId, item.title, true, 0, dur, playbackSpeed)
                    player.start()
                    startProgressUpdates()
                    player.setOnCompletionListener {
                        val finishedId = _state.value?.messageId
                        Log.d("VoicePlayer", "play: completed $finishedId")
                        playNextInQueueOrStop()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VoicePlayer", "play: error for ${item.messageId} - ${e::class.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    stopPlayback(clearQueue = false)
                    playNextInQueueOrStop()
                }
            }
        }
    }

    private fun playNextInQueueOrStop() {
        val nextIndex = currentIndex + 1
        val next = currentQueue.getOrNull(nextIndex)
        if (next == null) {
            stopPlayback(clearQueue = true)
            return
        }
        currentIndex = nextIndex
        playQueueItem(next)
    }

    private fun stopPlayback(clearQueue: Boolean) {
        progressJob?.cancel()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isPlayerPrepared = false
        _state.value = null
        if (clearQueue) {
            currentQueue = emptyList()
            currentIndex = -1
        }
    }

    private fun applyPlaybackSpeed(player: MediaPlayer) {
        if (!isPlayerPrepared) return
        try {
            player.playbackParams = player.playbackParams
                .setSpeed(playbackSpeed.value)
                .setPitch(1.0f)
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
                _state.value = _state.value?.copy(
                    positionMs = mediaPlayer?.currentPosition ?: break
                )
                delay(100)
            }
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
