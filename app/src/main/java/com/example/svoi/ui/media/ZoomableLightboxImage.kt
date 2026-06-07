package com.example.svoi.ui.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

private const val MIN_LIGHTBOX_SCALE = 1f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_LIGHTBOX_SCALE = 4.5f

@Composable
internal fun ZoomableLightboxImage(
    pageKey: Any,
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        var scale by remember(pageKey) { mutableFloatStateOf(MIN_LIGHTBOX_SCALE) }
        var offset by remember(pageKey) { mutableStateOf(Offset.Zero) }

        fun Offset.clamped(currentScale: Float): Offset {
            if (currentScale <= MIN_LIGHTBOX_SCALE) return Offset.Zero
            val maxX = containerWidth * (currentScale - MIN_LIGHTBOX_SCALE) / 2f
            val maxY = containerHeight * (currentScale - MIN_LIGHTBOX_SCALE) / 2f
            return Offset(
                x = x.coerceIn(-maxX, maxX),
                y = y.coerceIn(-maxY, maxY)
            )
        }

        LaunchedEffect(scale) {
            onZoomChanged(scale > MIN_LIGHTBOX_SCALE + 0.01f)
        }
        DisposableEffect(pageKey) {
            onDispose { onZoomChanged(false) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageKey, containerWidth, containerHeight) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale <= MIN_LIGHTBOX_SCALE + 0.01f) {
                                scale = DOUBLE_TAP_SCALE.coerceAtMost(MAX_LIGHTBOX_SCALE)
                                offset = Offset.Zero
                            } else {
                                scale = MIN_LIGHTBOX_SCALE
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(pageKey, containerWidth, containerHeight) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            val oldScale = scale
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val shouldHandle = pressedCount > 1 || oldScale > MIN_LIGHTBOX_SCALE + 0.01f

                            if (shouldHandle) {
                                val newScale = (oldScale * zoomChange)
                                    .coerceIn(MIN_LIGHTBOX_SCALE, MAX_LIGHTBOX_SCALE)
                                val scaledOffset = if (abs(oldScale) > 0f) {
                                    offset * (newScale / oldScale)
                                } else {
                                    offset
                                }
                                scale = newScale
                                offset = (scaledOffset + panChange).clamped(newScale)

                                event.changes.forEach { change ->
                                    if (change.pressed) change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (scale <= MIN_LIGHTBOX_SCALE + 0.01f) {
                            scale = MIN_LIGHTBOX_SCALE
                            offset = Offset.Zero
                        } else {
                            offset = offset.clamped(scale)
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            content()
        }
    }
}
