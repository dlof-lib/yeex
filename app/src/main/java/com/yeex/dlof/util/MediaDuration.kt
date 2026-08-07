package com.yeex.dlof.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Reads a picked video's duration so [com.yeex.dlof.ui.create.CreateParagraphScreen]
 * can enforce the product spec's 5–10 second clip length *before* spending time/
 * bandwidth Base64-encoding it. This is a real duration check (frames/track
 * metadata), not just a file-size heuristic.
 */
object MediaDuration {
    const val MIN_VIDEO_MS = 5_000L
    const val MAX_VIDEO_MS = 10_000L

    /** Returns duration in ms, or null if it couldn't be read (caller should reject). */
    fun getDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
