package com.yeex.dlof.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Plays a room's live-stream link ([com.yeex.dlof.data.model.Room.liveStreamUrl],
 * settable by the room owner only — see [com.yeex.dlof.ui.room.RoomScreen]).
 *
 * Direct media URLs (.m3u8 HLS, .mpd DASH, .mp4/.webm files — the typical
 * shape of an actual TV-channel/live encoder feed) play natively through
 * ExoPlayer with playback controls. Anything else (a YouTube/Twitch page
 * link, etc.) is loaded in a WebView instead, since ExoPlayer can only play
 * a raw media stream, not a host site's player page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveStreamPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDirectStream = remember(url) {
        val lower = url.substringBefore('?').substringBefore('#').lowercase()
        lower.endsWith(".m3u8") || lower.endsWith(".mpd") ||
            lower.endsWith(".mp4") || lower.endsWith(".webm")
    }

    if (isDirectStream) {
        val exoPlayer = remember(url) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
        }
        DisposableEffect(url) {
            onDispose { exoPlayer.release() }
        }
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
    } else {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            update = { view -> view.loadUrl(url) }
        )
    }
}
