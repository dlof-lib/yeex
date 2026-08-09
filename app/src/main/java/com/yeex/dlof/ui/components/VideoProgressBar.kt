package com.yeex.dlof.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yeex.dlof.ui.theme.YeexAccent
import kotlin.math.roundToLong

/**
 * A thin, TikTok/Reels-style scrub bar pinned to the bottom edge of a
 * playing [VideoPlayer]: a barely-there progress line while idle, that
 * thickens into a comfortably draggable scrubber the instant the person
 * touches it, with a floating current-time readout above the thumb while
 * dragging.
 *
 * Deliberately laid out left-to-right regardless of the app's active
 * locale — a video's playback position is a universal
 * "left = start, right = end" convention that short-video apps (TikTok,
 * Instagram, YouTube Shorts) keep LTR even in Arabic UIs, so this opts out
 * of the app's normal RTL mirroring via [LocalLayoutDirection] rather than
 * inheriting it, which would otherwise make dragging feel backwards.
 *
 * Reads/drives playback through [state] (see [VideoPlayerState] /
 * [rememberVideoPlayerState]) — this composable never touches ExoPlayer
 * directly.
 */
@Composable
fun VideoProgressBar(
    state: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val duration = state.durationMs
    val fraction = if (duration > 0) {
        (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val trackHeight by animateDpAsState(if (isPressed) 4.dp else 2.dp, label = "videoProgressTrackHeight")
    val touchTargetHeight = 28.dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(touchTargetHeight)
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (state.durationMs <= 0L) return@awaitEachGesture
                        isPressed = true
                        state.isScrubbing = true
                        val width = size.width.toFloat().coerceAtLeast(1f)

                        fun seekToX(x: Float) {
                            val frac = (x / width).coerceIn(0f, 1f)
                            state.seekTo((frac * state.durationMs).roundToLong())
                        }

                        seekToX(down.position.x)
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            seekToX(change.position.x)
                        }
                        isPressed = false
                        state.isScrubbing = false
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // ---- Track (full width, translucent) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.28f))
            )

            // ---- Filled portion up to current playback position ----
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.0001f))
                    .height(trackHeight)
                    .align(Alignment.CenterStart)
                    .background(if (isPressed) YeexAccent else Color.White)
            )

            // ---- Draggable thumb + floating time readout, only while pressed ----
            AnimatedVisibility(visible = isPressed, enter = fadeIn(), exit = fadeOut()) {
                ScrubThumbAndClock(fraction = fraction, positionMs = state.positionMs, durationMs = state.durationMs)
            }
        }
    }
}

/** The dot that tracks the current drag position plus a small "mm:ss /
 * mm:ss" pill floating above it — both positioned via [fraction] across
 * the full width of the parent [Box], using [androidx.compose.ui.layout.Layout]
 * semantics through simple weighted spacers rather than pixel math, so this
 * stays correct across any screen width without reading LocalDensity. */
@Composable
private fun ScrubThumbAndClock(fraction: Float, positionMs: Long, durationMs: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(fraction.coerceIn(0.0001f, 0.9999f)))
        Box(contentAlignment = Alignment.Center) {
            // Time pill, floating above the thumb.
            Box(
                modifier = Modifier
                    .offset(y = (-26).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .wrapContentSize()
            ) {
                Text(
                    "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
            // Thumb dot.
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.weight((1f - fraction).coerceIn(0.0001f, 0.9999f)))
    }
}

/** "0:07" / "1:23" style — matches [com.yeex.dlof.ui.components.CompactExpiryCountdown]'s
 * compact minute:second convention rather than a locale-dependent formatter. */
private fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
