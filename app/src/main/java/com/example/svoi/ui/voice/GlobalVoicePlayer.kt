package com.example.svoi.ui.voice

import android.media.MediaPlayer
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

data class GlobalVoiceState(
    val messageId: String,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int
)

/**
 * Singleton voice player that lives on SvoiApp.
 * Survives ViewModel lifecycle — allows playback to continue when navigating away from chat.
 */
class GlobalVoicePlayer {

    private val _state = MutableStateFlow<GlobalVoiceState?>(null)
    val state: StateFlow<GlobalVoiceState?> = _state

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        _state.value = GlobalVoiceState(messageId, title, false, 0, durationSec * 1000)
        scope.launch(Dispatchers.IO) {
            try {
                player.setDataSource(url)
                player.prepare()
                val dur = player.duration.takeIf { it > 0 } ?: (durationSec * 1000)
                withContext(Dispatchers.Main) {
                    _state.value = GlobalVoiceState(messageId, title, true, 0, dur)
                    player.start()
                    startProgressUpdates()
                    player.setOnCompletionListener {
                        val finishedId = _state.value?.messageId
                        stopInternal()
                        if (finishedId != null) onCompletion?.invoke(finishedId)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
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
}
