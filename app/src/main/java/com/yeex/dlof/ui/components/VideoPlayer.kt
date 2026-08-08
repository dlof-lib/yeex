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
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VideoPlayer"

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
    isActive: Boolean = true
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
            playWhenReady = isActive
            prepare()
        }
    }

    // Pause/resume as the pager settles on or away from this page, instead
    // of leaving every composed page's player running simultaneously.
    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.seekTo(0)
    }

    // Also stop playback while the app itself is backgrounded, so audio
    // doesn't keep running behind other apps or the lock screen.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = isActive
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
