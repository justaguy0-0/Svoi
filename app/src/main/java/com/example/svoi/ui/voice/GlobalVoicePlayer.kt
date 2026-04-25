package com.example.svoi.ui.voice

import android.media.MediaPlayer
import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

data class GlobalVoiceState(
    val messageId: String,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val downloadProgress: Float = -1f  // 0-1 while downloading, -1 when done/cached
)

/**
 * Singleton voice player that lives on SvoiApp.
 * Survives ViewModel lifecycle — allows playback to continue when navigating away from chat.
 *
 * Voice files are cached locally in [cacheDir] as voice_<messageId>.m4a.
 * On first play the file is downloaded; subsequent plays (including offline) use the local copy.
 */
class GlobalVoicePlayer(private val cacheDir: File) {

    private val _state = MutableStateFlow<GlobalVoiceState?>(null)
    val state: StateFlow<GlobalVoiceState?> = _state

    // Set of messageIds whose voice files are cached locally
    private val _cachedVoiceIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedVoiceIds: StateFlow<Set<String>> = _cachedVoiceIds

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Scan existing cached files on startup
        val existing = cacheDir.listFiles()
            ?.filter { it.name.startsWith("voice_") && it.name.endsWith(".m4a") && !it.name.endsWith("_tmp.m4a") && it.length() > 0 }
            ?.mapNotNull { f ->
                val name = f.nameWithoutExtension // voice_<messageId>
                if (name.length > 6) name.removePrefix("voice_") else null
            }
            ?.toSet() ?: emptySet()
        _cachedVoiceIds.value = existing
    }

    /** Called when playback finishes naturally (not on stop/pause). messageId of the finished message. */
    var onCompletion: ((messageId: String) -> Unit)? = null

    fun play(messageId: String, url: String, durationSec: Int, title: String) {
        val cur = _state.value
        if (cur?.messageId == messageId) {
            if (cur.isPlaying) pause() else resume()
            return
        }
        stopInternal()
        val player = MediaPlayer()
        mediaPlayer = player
        val isCached = File(cacheDir, "voice_$messageId.m4a").let { it.exists() && it.length() > 0 }
        _state.value = GlobalVoiceState(
            messageId, title, false, 0, durationSec * 1000,
            downloadProgress = if (isCached) -1f else 0f
        )
        Log.d("VoicePlayer", "play: start messageId=$messageId isCached=$isCached url=${url.takeLast(60)}")
        scope.launch(Dispatchers.IO) {
            try {
                var lastLoggedPct = -1
                val localPath = resolveLocalFile(messageId, url) { progress ->
                    if (mediaPlayer === player) {
                        _state.value = _state.value?.copy(downloadProgress = progress)
                        val pct = (progress * 100).toInt()
                        if (pct / 10 > lastLoggedPct / 10) {
                            lastLoggedPct = pct
                            Log.d("VoicePlayer", "download progress: $pct% for $messageId")
                        }
                    }
                }
                // Race condition guard: another play() may have released this player already
                if (mediaPlayer !== player) {
                    Log.d("VoicePlayer", "play: player replaced, aborting for $messageId")
                    return@launch
                }
                _state.value = _state.value?.copy(downloadProgress = -1f)
                if (localPath != null) {
                    Log.d("VoicePlayer", "play: setDataSource LOCAL $localPath")
                    player.setDataSource(localPath)
                } else {
                    Log.d("VoicePlayer", "play: setDataSource STREAM ${url.takeLast(60)}")
                    player.setDataSource(url)
                }
                Log.d("VoicePlayer", "play: calling prepare() for $messageId")
                player.prepare()
                val dur = player.duration.takeIf { it > 0 } ?: (durationSec * 1000)
                Log.d("VoicePlayer", "play: prepared OK, duration=${dur}ms for $messageId")
                withContext(Dispatchers.Main) {
                    _state.value = GlobalVoiceState(messageId, title, true, 0, dur)
                    player.start()
                    startProgressUpdates()
                    player.setOnCompletionListener {
                        val finishedId = _state.value?.messageId
                        Log.d("VoicePlayer", "play: completed $finishedId")
                        stopInternal()
                        if (finishedId != null) onCompletion?.invoke(finishedId)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VoicePlayer", "play: error for $messageId — ${e::class.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    _state.value = null
                    mediaPlayer = null
                }
            }
        }
    }

    fun pause() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        progressJob?.cancel()
        _state.value = _state.value?.copy(isPlaying = false)
    }

    fun resume() {
        val cur = _state.value ?: return
        try { mediaPlayer?.start() } catch (_: Exception) { return }
        _state.value = cur.copy(isPlaying = true)
        startProgressUpdates()
    }

    fun seek(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _state.value = _state.value?.copy(positionMs = positionMs)
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        progressJob?.cancel()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        _state.value = null
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

    /**
     * Returns the absolute path of a locally cached voice file for [messageId].
     * If the file doesn't exist yet, downloads it from [url] and caches it.
     * Returns null if offline and no local copy exists (caller falls back to streaming).
     */
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
                // Per-read timeout: 30s without any data → stall detection
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
            null  // no local copy — caller uses URL as fallback
        }
    }
}
