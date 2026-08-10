package com.yeex.dlof.util

import android.content.Context
import coil.Coil
import java.io.File
import java.util.Locale

/**
 * Backs "مسح ذاكرة التخزين المؤقت" in Settings & Privacy. Two things make up
 * "cache" in this app:
 *  1. Coil's disk + memory cache (every avatar/banner/thumbnail bitmap Coil
 *     has decoded — see [coil.Coil.imageLoader]).
 *  2. [Context.getCacheDir] itself — where [DataExportUtil] and other
 *     transient work write temp files.
 * Deliberately does NOT touch [Context.getFilesDir] or the encrypted
 * multi-account store ([com.yeex.dlof.data.local.LocalAccountStore]) —
 * clearing cache should never sign anyone out or forget a saved account.
 */
object CacheUtil {

    private fun dirSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Human-readable current size ("12.4 MB" / "340 KB"), for display next to the clear button. */
    fun currentSizeLabel(context: Context): String {
        val bytes = dirSizeBytes(context.cacheDir) + dirSizeBytes(context.externalCacheDir)
        return formatBytes(bytes)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** Clears Coil's caches plus the app's own cache directories. Safe to call from a coroutine off the main thread. */
    fun clear(context: Context) {
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { context.cacheDir?.deleteRecursively() }
        runCatching { context.externalCacheDir?.deleteRecursively() }
    }
}
