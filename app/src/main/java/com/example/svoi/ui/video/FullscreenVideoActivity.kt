package com.example.svoi.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ImageButton
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.svoi.R

class FullscreenVideoActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var videoUrl: String = ""
    private var startPositionMs: Long = 0L
    private var playWhenReady: Boolean = true
    private var resultReturned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_video)

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        startPositionMs = savedInstanceState?.getLong(STATE_POSITION_MS)
            ?: intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        playWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY)
            ?: intent.getBooleanExtra(EXTRA_PLAY_WHEN_READY, true)

        val root = findViewById<FrameLayout>(R.id.fullscreen_video_root)
        playerView = findViewById(R.id.fullscreen_player_view)
        val closeButton = findViewById<ImageButton>(R.id.fullscreen_video_close)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        closeButton.setOnClickListener { finishWithResult() }
        root.setBackgroundColor(android.graphics.Color.BLACK)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult()
                }
            }
        )

        initializePlayer()
    }

    override fun onStart() {
        super.onStart()
        if (player == null) initializePlayer()
    }

    override fun onPause() {
        savePlaybackState()
        player?.pause()
        super.onPause()
    }

    override fun onStop() {
        savePlaybackState()
        releasePlayer()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePlaybackState()
        outState.putLong(STATE_POSITION_MS, startPositionMs)
        outState.putBoolean(STATE_PLAY_WHEN_READY, playWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (!resultReturned) {
            finishWithResult()
            return
        }
        super.finish()
    }

    private fun initializePlayer() {
        if (videoUrl.isBlank()) {
            finishWithResult()
            return
        }
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            seekTo(startPositionMs.coerceAtLeast(0L))
            this.playWhenReady = this@FullscreenVideoActivity.playWhenReady
            prepare()
            if (this@FullscreenVideoActivity.playWhenReady) play()
        }
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.useController = true
    }

    private fun savePlaybackState() {
        player?.let { exoPlayer ->
            startPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playWhenReady = exoPlayer.playWhenReady
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ViewCompat.requestApplyInsets(findViewById(R.id.fullscreen_video_root))
    }

    private fun finishWithResult() {
        savePlaybackState()
        resultReturned = true
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_VIDEO_URL, videoUrl)
                .putExtra(EXTRA_CURRENT_POSITION_MS, startPositionMs)
                .putExtra(EXTRA_PLAY_WHEN_READY, playWhenReady)
        )
        releasePlayer()
        super.finish()
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_VIDEO_URL = "videoUrl"
        const val EXTRA_START_POSITION_MS = "startPositionMs"
        const val EXTRA_CURRENT_POSITION_MS = "currentPositionMs"
        const val EXTRA_PLAY_WHEN_READY = "playWhenReady"

        private const val STATE_POSITION_MS = "statePositionMs"
        private const val STATE_PLAY_WHEN_READY = "statePlayWhenReady"

        fun createIntent(
            context: Context,
            videoUrl: String,
            startPositionMs: Long,
            playWhenReady: Boolean
        ): Intent = Intent(context, FullscreenVideoActivity::class.java)
            .putExtra(EXTRA_VIDEO_URL, videoUrl)
            .putExtra(EXTRA_START_POSITION_MS, startPositionMs)
            .putExtra(EXTRA_PLAY_WHEN_READY, playWhenReady)
    }
}
