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
    // Mandatory profile-picture size: every avatar is center-cropped to a
    // square then forced to exactly this many pixels on each side, so all
    // avatars render identically everywhere (UserAvatar, search results,
    // switch-account rows) regardless of what the person picked.
    const val AVATAR_DIMENSION = 512
    private const val AVATAR_JPEG_QUALITY = 85
    // Mandatory banner size: every banner is center-cropped to this exact
    // 3:1 aspect ratio then forced to these exact pixel dimensions.
    const val BANNER_WIDTH = 1200
    const val BANNER_HEIGHT = 400
    private const val BANNER_JPEG_QUALITY = 82
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

    /**
     * Same size check as [encodeVideoIfSmallEnough] but reads from a local
     * file — used for the trimmed-clip scratch file [VideoTrimUtil] produces,
     * which (unlike a picked content:// [Uri]) isn't re-readable via the
     * resolver.
     */
    fun encodeVideoFileIfSmallEnough(file: java.io.File): String? {
        val bytes = file.readBytes()
        if (bytes.size > MAX_VIDEO_BYTES) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Encodes a picked account-icon image. Enforces the mandatory
     * [AVATAR_DIMENSION]×[AVATAR_DIMENSION] square: the picked image is
     * center-cropped to a square (whatever its original aspect ratio) and
     * then forced to exactly that pixel size, so every avatar stored is
     * identical in shape and size — never stretched, never partial.
     */
    fun encodeAvatar(resolver: ContentResolver, uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: error("cannot open image")
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val square = cropCenterSquare(original)
        val fixed = Bitmap.createScaledBitmap(square, AVATAR_DIMENSION, AVATAR_DIMENSION, true)
        val out = ByteArrayOutputStream()
        fixed.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Encodes a picked banner image. Enforces the mandatory 3:1
     * [BANNER_WIDTH]×[BANNER_HEIGHT] size: the picked image is
     * center-cropped to that exact aspect ratio, then forced to that exact
     * pixel size — same "always identical shape" guarantee as [encodeAvatar].
     */
    fun encodeBanner(resolver: ContentResolver, uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: error("cannot open image")
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val cropped = cropCenterAspect(original, BANNER_WIDTH.toFloat() / BANNER_HEIGHT.toFloat())
        val fixed = Bitmap.createScaledBitmap(cropped, BANNER_WIDTH, BANNER_HEIGHT, true)
        val out = ByteArrayOutputStream()
        fixed.compress(Bitmap.CompressFormat.JPEG, BANNER_JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // A single Moment paragraph can carry several per-step photos inside the
    // same paragraph node, so each one is downscaled well below the regular
    // per-paragraph MAX_IMAGE_DIMENSION to keep the combined payload under
    // database.rules.json's node size limits.
    private const val MOMENT_STEP_IMAGE_DIMENSION = 640
    private const val MOMENT_STEP_JPEG_QUALITY = 70

    /** Encodes a photo attached to a single [com.yeex.dlof.data.model.MomentStep]. */
    fun encodeMomentStepImage(resolver: ContentResolver, uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: error("cannot open image")
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val scaled = downscale(original, MOMENT_STEP_IMAGE_DIMENSION)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, MOMENT_STEP_JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Returns null (instead of crashing) if [base64] can't be decoded as an
     * image — e.g. it's actually VIDEO bytes (raw MP4), or the string is
     * corrupt. Callers MUST treat null as "no image to show" rather than
     * assuming decoding an image always succeeds.
     */
    fun decodeToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun downscale(src: Bitmap, maxDimension: Int): Bitmap {
        val ratio = maxDimension.toFloat() / maxOf(src.width, src.height)
        if (ratio >= 1f) return src
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Center-crops [src] to a 1:1 square, keeping the middle of whichever dimension is longer. */
    private fun cropCenterSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    /** Center-crops [src] to [targetRatio] (width / height), trimming whichever side is oversized. */
    private fun cropCenterAspect(src: Bitmap, targetRatio: Float): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        return if (srcRatio > targetRatio) {
            val newWidth = (src.height * targetRatio).toInt().coerceIn(1, src.width)
            val x = (src.width - newWidth) / 2
            Bitmap.createBitmap(src, x, 0, newWidth, src.height)
        } else {
            val newHeight = (src.width / targetRatio).toInt().coerceIn(1, src.height)
            val y = (src.height - newHeight) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newHeight)
        }
    }
}
