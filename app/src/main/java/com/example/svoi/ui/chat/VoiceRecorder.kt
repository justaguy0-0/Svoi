package com.example.svoi.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0

    val isRecording: Boolean get() = recorder != null
    val elapsedMs: Long get() = if (recorder != null) System.currentTimeMillis() - startTimeMs else 0

    fun start(): Boolean {
        if (isRecording) return false
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(24000)   // 24 kHz — чистая речь
            mr.setAudioEncodingBitRate(64000) // 64 kbps ≈ 480 KB/мин
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "start failed: ${e.message}")
            recorder = null
            outputFile?.delete()
            outputFile = null
            false
        }
    }

    /** Returns (file, durationSeconds) or null on error */
    fun stop(): Pair<File, Int>? {
        val mr = recorder ?: return null
        val durationMs = System.currentTimeMillis() - startTimeMs
        return try {
            mr.stop()
            mr.release()
            recorder = null
            val duration = (durationMs / 1000).toInt().coerceAtLeast(1)
            val file = outputFile ?: return null
            outputFile = null
            Pair(file, duration)
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "stop failed: ${e.message}")
            recorder = null
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    fun getMaxAmplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
}
