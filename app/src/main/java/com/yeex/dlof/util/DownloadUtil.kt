package com.yeex.dlof.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Saves a (watermarked) bitmap, or a raw video file, into the device's
 * public gallery, in a dedicated "yeex" album, so downloaded paragraphs are
 * easy to find and never overwrite the app's private cache.
 */
object DownloadUtil {

    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/yeex")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val url = MediaStore.Images.Media.insertImage(
                    context.contentResolver, bitmap, displayName, "yeex paragraph download"
                )
                url != null
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Same destination/behavior as [saveVideoToGallery] but streams an
     * existing file on disk instead of taking the whole clip as a
     * [ByteArray] — used for [com.yeex.dlof.util.VideoWatermarkUtil]'s
     * output, which can be sizable once re-encoded, so it's copied in
     * chunks rather than held fully in memory.
     */
    fun saveVideoFileToGallery(context: Context, sourceFile: File, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/yeex")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "yeex")
                if (!dir.exists() && !dir.mkdirs()) return false
                val file = File(dir, "$displayName.mp4")
                FileInputStream(sourceFile).use { input -> file.outputStream().use { out -> input.copyTo(out) } }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                )
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Saves a paragraph's raw MP4 bytes into the device's public "Movies/yeex"
     * album, unwatermarked. Kept as the fallback path for when
     * [com.yeex.dlof.util.VideoWatermarkUtil]'s re-encode fails (bad codec,
     * OOM on a low-end device, etc.) — see the `ParagraphCard` download
     * action, which tries the watermarked export first and only drops to
     * this on failure so a download never silently fails outright.
     */
    fun saveVideoToGallery(context: Context, videoBytes: ByteArray, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/yeex")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(videoBytes)
                } ?: return false
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                // Pre-Q: no scoped-storage IS_PENDING dance — write straight into
                // the public Movies/yeex folder and ask the media scanner to
                // pick it up, same fallback shape as the pre-Q image path above.
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "yeex")
                if (!dir.exists() && !dir.mkdirs()) return false
                val file = File(dir, "$displayName.mp4")
                FileOutputStream(file).use { it.write(videoBytes) }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                )
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
