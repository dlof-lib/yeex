package com.yeex.dlof.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.resumeWith
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

/**
 * Burns the yeex watermark into every frame of a downloaded video — the
 * "علامة مائية على كل فريمات الفيديو" item the README lists as scaffolded
 * (see [WatermarkUtil]'s class doc). Two overlay layers, both driven by
 * [WatermarkUtil] so the look matches the photo/PDF stamp exactly:
 *
 *  1. A static, full-frame layer ([WatermarkUtil.renderFrameOverlay]) —
 *     the diagonal "yeex" tile + author badge, identical to what a photo
 *     download gets, replayed on every decoded frame.
 *  2. A small avatar+handle "bubble" ([WatermarkUtil.renderAuthorBubble])
 *     that jumps between the four corners every couple of seconds — the
 *     "فيديو مدمج بداخل كل الفيديوهات مثل تيك توك" behavior: short-video
 *     apps move their downloaded-clip badge around specifically so it can't
 *     be cropped or covered by pausing it in one spot.
 *
 * Implementation-wise this is a full decode → composite → re-encode pass
 * via Media3's `Transformer` (already a project dependency via
 * media3-exoplayer — `media3-transformer`/`media3-effect` are the matching
 * modules from that same library, not a new stack like ffmpeg-kit). It is
 * therefore slower than [VideoTrimUtil]'s stream copy and should always run
 * off the main thread with progress shown to the user, same as the other
 * download actions in `ParagraphCard`.
 */
@UnstableApi
object VideoWatermarkUtil {

    /**
     * Reads [sourceUri]'s dimensions/duration, renders the two overlay
     * layers to match, and writes a watermarked copy to [outputFile].
     * Returns false (with [outputFile] cleaned up) if the source can't be
     * read or the export fails for any reason — callers should fall back to
     * saving the original, unwatermarked bytes rather than losing the
     * download entirely.
     */
    suspend fun applyWatermark(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        appLabel: String,
        authorIdentifier: String,
        authorDisplayName: String,
        authorAvatar: Bitmap?,
        authorVerified: Boolean,
        likeCount: Long,
        viewCount: Long
    ): Boolean {
        val metadata = readMetadata(context, sourceUri) ?: return false
        val (width, height, durationMs) = metadata
        if (width <= 0 || height <= 0) return false

        val frameOverlayBitmap = WatermarkUtil.renderFrameOverlay(
            width = width,
            height = height,
            appLabel = appLabel,
            authorIdentifier = authorIdentifier,
            authorDisplayName = authorDisplayName,
            authorAvatar = authorAvatar,
            authorVerified = authorVerified,
            likeCount = likeCount,
            viewCount = viewCount
        )
        val bubbleDiameter = (width * 0.24f).toInt().coerceAtLeast(1)
        val bubbleBitmap = WatermarkUtil.renderAuthorBubble(
            diameterPx = bubbleDiameter,
            appLabel = appLabel,
            authorIdentifier = authorIdentifier.ifBlank { appLabel },
            authorAvatar = authorAvatar
        )

        return export(
            context = context,
            sourceUri = sourceUri,
            outputFile = outputFile,
            frameOverlayBitmap = frameOverlayBitmap,
            bubbleBitmap = bubbleBitmap,
            bubbleWidth = bubbleDiameter,
            bubbleHeightPx = bubbleBitmap.height,
            videoWidth = width,
            videoHeight = height,
            durationUs = durationMs.coerceAtLeast(1L) * 1000L
        )
    }

    private data class VideoMeta(val width: Int, val height: Int, val durationMs: Long)

    private fun readMetadata(context: Context, uri: Uri): VideoMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            // Transformer's output is already rotation-corrected, so the
            // overlay bitmaps need to be sized for the *displayed*
            // width/height, not the raw encoded ones, when the clip carries
            // a 90/270 rotation.
            if (rotation == 90 || rotation == 270) {
                val tmp = w; w = h; h = tmp
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (w <= 0 || h <= 0) null else VideoMeta(w, h, duration)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun export(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        frameOverlayBitmap: Bitmap,
        bubbleBitmap: Bitmap,
        bubbleWidth: Int,
        bubbleHeightPx: Int,
        videoWidth: Int,
        videoHeight: Int,
        durationUs: Long
    ): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val frameOverlay = StaticBitmapOverlay(frameOverlayBitmap)
            val bubbleOverlay = BouncingBitmapOverlay(
                bitmap = bubbleBitmap,
                bitmapWidthPx = bubbleWidth,
                bitmapHeightPx = bubbleHeightPx,
                videoWidthPx = videoWidth,
                videoHeightPx = videoHeight
            )
            val overlayEffect = OverlayEffect(ImmutableList.of(frameOverlay, bubbleOverlay))

            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
                .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                .build()

            if (outputFile.exists()) outputFile.delete()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(true))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        outputFile.delete()
                        if (cont.isActive) cont.resumeWith(Result.success(false))
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
                outputFile.delete()
            }
        } catch (e: Exception) {
            outputFile.delete()
            if (cont.isActive) cont.resumeWith(Result.success(false))
        }
    }

    /** Full-frame stamp that never moves or changes — the video counterpart of the photo/PDF watermark. */
    private class StaticBitmapOverlay(private val bitmap: Bitmap) : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap
    }

    /**
     * Same bitmap every frame, but [getOverlaySettings] moves its anchor
     * between the four corners every [SEGMENT_US] — the "bounces around the
     * screen" behavior other short-video apps use on downloaded clips so
     * the badge can't be cropped or covered by pausing on one spot.
     */
    private class BouncingBitmapOverlay(
        private val bitmap: Bitmap,
        bitmapWidthPx: Int,
        bitmapHeightPx: Int,
        videoWidthPx: Int,
        videoHeightPx: Int
    ) : BitmapOverlay() {

        companion object {
            private const val SEGMENT_US = 2_500_000L
            private const val MARGIN_FRACTION = 0.06f
        }

        // Media3's overlay anchors are normalized [-1, 1] across the frame,
        // with the overlay's own size already accounted for in its scale —
        // so the "how far the bubble can travel" margin is derived from how
        // big the bubble is relative to the frame, not a fixed constant.
        private val halfWidthFrac = (bitmapWidthPx.toFloat() / videoWidthPx) / 2f
        private val halfHeightFrac = (bitmapHeightPx.toFloat() / videoHeightPx) / 2f
        private val edgeX = (1f - MARGIN_FRACTION - halfWidthFrac).coerceIn(0.1f, 0.92f)
        private val edgeY = (1f - MARGIN_FRACTION - halfHeightFrac).coerceIn(0.1f, 0.92f)

        private val anchors = listOf(
            edgeX to edgeY,
            -edgeX to edgeY,
            -edgeX to -edgeY,
            edgeX to -edgeY
        )

        override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val index = ((presentationTimeUs / SEGMENT_US) % anchors.size).toInt()
            val (ax, ay) = anchors[index]
            return OverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(ax, ay)
                .build()
        }
    }
}
