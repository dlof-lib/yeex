package com.yeex.dlof.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

/**
 * Optional, opt-in ("اقتراح نشر لقطات الشاشة") watcher that notices when
 * the person takes a new screenshot while YEEX is in the foreground
 * (off by default, see [SettingsPrefsStore.screenshotSuggestEnabled]), and
 * hands it to [PendingShareBridge] so [com.yeex.dlof.ui.share.ShareTargetSheet]
 * can offer to publish it — nothing is read or uploaded unless the person
 * explicitly taps "نشر" afterwards.
 *
 * Uses a [ContentObserver] on the shared images collection rather than
 * polling: MediaStore notifies observers the moment a new row is inserted
 * (which is also how the system's own "screenshot taken" toast/edit-shortcut
 * works). Every new row is filtered down to "was this actually a
 * screenshot, and was it just taken" before ever surfacing it, so a normal
 * photo import/download never triggers the prompt.
 */
object ScreenshotWatcher {

    private const val TAG = "ScreenshotWatcher"

    /** How recent DATE_ADDED must be (seconds, MediaStore's unit) for a matching row to count as "just captured" rather than a pre-existing file the query happened to also match. */
    private const val RECENCY_WINDOW_SECONDS = 10L

    private var observer: ContentObserver? = null

    /** Starts watching; safe to call multiple times (a second call is a no-op while already registered). Call [stop] from the same lifecycle owner's onDispose. */
    fun start(context: Context, onScreenshotDetected: (Uri) -> Unit) {
        if (observer != null) return
        val resolver = context.applicationContext.contentResolver
        val handler = Handler(Looper.getMainLooper())
        val newObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                runCatching { checkLatestForScreenshot(resolver, onScreenshotDetected) }
                    .onFailure { Log.w(TAG, "screenshot check failed", it) }
            }
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            newObserver
        )
        observer = newObserver
    }

    fun stop(context: Context) {
        val current = observer ?: return
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(current) }
        observer = null
    }

    private fun checkLatestForScreenshot(resolver: ContentResolver, onScreenshotDetected: (Uri) -> Unit) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        val nowSeconds = System.currentTimeMillis() / 1000
        // ORDER BY date_added DESC LIMIT 1 — only ever the most recent
        // insert matters; a burst of unrelated inserts just means older
        // rows are (correctly) ignored rather than re-triggering the prompt.
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)).orEmpty()
            val path = runCatching {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
            }.getOrNull().orEmpty()
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

            val isRecent = nowSeconds - dateAdded <= RECENCY_WINDOW_SECONDS
            val looksLikeScreenshot = name.contains("screenshot", ignoreCase = true) ||
                path.contains("screenshot", ignoreCase = true)

            if (isRecent && looksLikeScreenshot) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                onScreenshotDetected(uri)
            }
        }
    }
}
