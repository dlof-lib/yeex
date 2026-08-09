package com.yeex.dlof.ui.components

import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VideoPlayer"

/**
 * Progress + seek handle for a [VideoPlayer], kept as a separate holder
 * (rather than piling more callback params onto VideoPlayer itself) so a
 * scrubber UI like [VideoProgressBar] can read/drive playback position
 * without VideoPlayer needing any opinion about how that's drawn.
 *
 * [positionMs]/[durationMs] are `mutableStateOf` so a scrubber recomposes
 * live as playback advances. [isScrubbing] is set by the scrubber itself
 * while the person has a finger down on it — VideoPlayer's own position
 * polling checks this and backs off while true, so it doesn't fight a
 * drag-in-progress by snapping position back to "wherever the player
 * actually is" every ~200ms.
 */
class VideoPlayerState {
    var positionMs by mutableStateOf(0L)
        internal set
    var durationMs by mutableStateOf(0L)
        internal set
    var isScrubbing by mutableStateOf(false)

    internal var player: ExoPlayer? = null

    /** Seeks the underlying player (if attached) and immediately reflects
     * the new position locally, so a dragged scrubber thumb doesn't wait a
     * poll cycle to "catch up" to where the finger already is. */
    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0L, durationMs.coerceAtLeast(0L))
        positionMs = clamped
        player?.seekTo(clamped)
    }
}

/** Creates a [VideoPlayerState] scoped to [key] (typically the paragraph
 * id), so switching to a different video starts a fresh progress/seek
 * handle instead of carrying over the previous video's position. */
@Composable
fun rememberVideoPlayerState(key: Any? = Unit): VideoPlayerState =
    remember(key) { VideoPlayerState() }

/**
 * Plays a short VIDEO-type paragraph stored inline as Base64 (see
 * [com.yeex.dlof.util.MediaBase64.encodeVideoIfSmallEnough]).
 *
 * ExoPlayer needs a Uri, not raw bytes, so the decoded MP4 bytes are written
 * once to a per-paragraph file under the app cache dir and played from
 * there. This intentionally replaces the old (broken) attempt to decode
 * video bytes with [android.graphics.BitmapFactory], which always returned
 * null for MP4 data and crashed the app right after publishing a video.
 */
@Composable
fun VideoPlayer(
    paragraphId: String,
    mediaBase64: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    muted: Boolean = false,
    // Whether this is the page the user has actually settled on. FeedScreen's
    // HorizontalPager composes the current page *and* the page it's mid-swipe
    // towards at the same time, so without this every VideoPlayer used to
    // start itself with playWhenReady = true unconditionally — two videos'
    // audio would overlap for the length of the swipe gesture, and a video
    // kept playing (and using CPU/battery) even after being swiped away.
    isActive: Boolean = true,
    // A tap-to-pause request from the person, independent of [isActive] —
    // ANDed together below so leaving the page always wins over a manual
    // pause, and manual pause is remembered while the page stays active.
    isPaused: Boolean = false,
    // 0.5x/1x/1.5x/2x from the speed-cycle chip in ParagraphCard.
    playbackSpeed: Float = 1f,
    // Fired every time playback reaches the end and seamlessly restarts
    // (REPEAT_MODE_ONE) — lets the caller flash a brief TikTok-style
    // "looped" indicator instead of the video just silently jumping back
    // to frame 0 with no feedback.
    onLoop: () -> Unit = {},
    // Optional progress/seek handle — see [VideoPlayerState]. Pass one in
    // (via [rememberVideoPlayerState]) to drive a [VideoProgressBar]; left
    // null this behaves exactly as before.
    state: VideoPlayerState? = null
) {
    val context = LocalContext.current

    // Decode once per paragraph and cache the mp4 bytes to a stable file so
    // re-compositions don't re-decode ~megabytes of Base64 every frame.
    var videoFile by remember(paragraphId) { mutableStateOf<File?>(null) }
    LaunchedEffectOnce(paragraphId) {
        videoFile = decodeToCacheFile(context.cacheDir, paragraphId, mediaBase64)
    }

    val file = videoFile
    if (file == null) return

    val exoPlayer = remember(paragraphId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toUri()))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            // Only autoplay if this page is already the active/settled one —
            // see isActive doc above.
            playWhenReady = isActive && !isPaused
            prepare()
        }
    }

    // Pause/resume as the pager settles on or away from this page, or as the
    // person taps the video to pause/resume it themselves.
    LaunchedEffect(isActive, isPaused) {
        exoPlayer.playWhenReady = isActive && !isPaused
        if (!isActive) exoPlayer.seekTo(0)
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Also stop playback while the app itself is backgrounded, so audio
    // doesn't keep running behind other apps or the lock screen.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive, isPaused) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = isActive && !isPaused
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keeps the listener below reacting to the latest onLoop lambda without
    // having to re-register it (and without needing onLoop as a `remember`
    // key, which would tear down/rebuild the whole player on every
    // recomposition where the caller passes a fresh lambda instance).
    val currentOnLoop by androidx.compose.runtime.rememberUpdatedState(onLoop)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    currentOnLoop()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Hands the ExoPlayer instance to the caller's [VideoPlayerState] (if
    // any) so a scrubber outside this composable can call seekTo() on it,
    // and detaches it again on dispose so a stale reference can't be
    // seeked into after this player is released below.
    DisposableEffect(exoPlayer, state) {
        state?.player = exoPlayer
        onDispose {
            if (state?.player == exoPlayer) state?.player = null
        }
    }

    // Polls playback position/duration a few times a second to keep
    // [state] fresh for a scrubber to render. Skipped entirely while the
    // person is actively dragging that scrubber (state.isScrubbing) so
    // this doesn't overwrite a position the finger has already moved past
    // — VideoPlayerState.seekTo already updates positionMs synchronously
    // on every drag step, this loop would otherwise just fight it with
    // whatever value the player itself reports mid-seek.
    LaunchedEffect(exoPlayer, state) {
        if (state == null) return@LaunchedEffect
        while (true) {
            if (!state.isScrubbing) {
                runCatching {
                    val duration = exoPlayer.duration
                    state.durationMs = if (duration > 0) duration else 0L
                    state.positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
            }
            delay(200)
        }
    }

    DisposableEffect(paragraphId) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}

private fun decodeToCacheFile(cacheDir: File, paragraphId: String, base64: String): File? {
    return try {
        val dir = File(cacheDir, "paragraph_videos").apply { mkdirs() }
        val file = File(dir, "$paragraphId.mp4")
        if (!file.exists()) {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            FileOutputStream(file).use { it.write(bytes) }
        }
        file
    } catch (e: Exception) {
        Log.e(TAG, "failed to decode video for paragraph $paragraphId", e)
        null
    }
}

private fun File.toUri() = android.net.Uri.fromFile(this)

/** Small helper so this file doesn't need an extra import block juggling act. */
@Composable
private fun LaunchedEffectOnce(key: Any?, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}
