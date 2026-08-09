package com.yeex.dlof.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Plain (unencrypted) local-only "recent searches" list for [com.yeex.dlof.ui.search.SearchScreen] —
 * just the raw query text people typed, nothing sensitive, so unlike
 * [com.yeex.dlof.data.local.LocalAccountStore] this intentionally doesn't
 * need Keystore-backed encryption at rest.
 *
 * Newest first, capped at [MAX_ENTRIES] so this can't grow forever, and
 * de-duplicated case-insensitively so re-searching something already in
 * the list just bumps it to the top instead of listing it twice.
 */
object SearchHistoryStore {
    private const val PREFS_NAME = "yeex_search_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 12

    // A control character essentially never typed into a search box, used
    // to join/split entries without needing a JSON dependency for a dozen
    // short strings.
    private const val SEPARATOR = "\u0001"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Most-recent-first list of past search queries. */
    fun recent(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    /** Records [query] as the newest entry, removing any earlier
     * case-insensitive duplicate first. No-ops for a blank query. */
    fun record(context: Context, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return recent(context)
        val existing = recent(context).filterNot { it.equals(trimmed, ignoreCase = true) }
        val updated = (listOf(trimmed) + existing).take(MAX_ENTRIES)
        prefs(context).edit().putString(KEY_ENTRIES, updated.joinToString(SEPARATOR)).apply()
        return updated
    }

    /** Removes a single entry (case-insensitive match). */
    fun remove(context: Context, query: String): List<String> {
        val updated = recent(context).filterNot { it.equals(query, ignoreCase = true) }
        prefs(context).edit().putString(KEY_ENTRIES, updated.joinToString(SEPARATOR)).apply()
        return updated
    }

    /** Clears the whole history. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply()
    }
}
