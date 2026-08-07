package com.yeex.dlof.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Encodes picked media as Base64 for inline storage in the Realtime Database
 * (per the "b64" storage requirement — no Firebase Storage / Blaze plan
 * needed). Images are downscaled and JPEG-compressed first to keep the
 * payload well under the ~10MB RTDB node practical limit; short 5–10s videos
 * should already be small but are size-checked before upload.
 */
object MediaBase64 {

    private const val MAX_IMAGE_DIMENSION = 1080
    private const val JPEG_QUALITY = 80
    // Must stay under database.rules.json's mediaBase64 char cap (8,500,000).
    // Base64 inflates bytes by 4/3, so 6MB raw -> ~8,000,000 chars, safely under that cap.
    const val MAX_VIDEO_BYTES = 6 * 1024 * 1024

    fun encodeImage(resolver: ContentResolver, uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: error("cannot open image")
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val scaled = downscale(original, MAX_IMAGE_DIMENSION)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Returns null (caller should reject) if the clip exceeds [MAX_VIDEO_BYTES]. */
    fun encodeVideoIfSmallEnough(resolver: ContentResolver, uri: Uri): String? {
        val input = resolver.openInputStream(uri) ?: error("cannot open video")
        val bytes = input.use { it.readBytes() }
        if (bytes.size > MAX_VIDEO_BYTES) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeToBitmap(base64: String): Bitmap {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun downscale(src: Bitmap, maxDimension: Int): Bitmap {
        val ratio = maxDimension.toFloat() / maxOf(src.width, src.height)
        if (ratio >= 1f) return src
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
