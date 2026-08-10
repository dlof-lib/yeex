package com.yeex.dlof.util

import android.content.Context

/**
 * "الكلمات المكتومة" — a device-local list of words/phrases whose comments
 * [com.yeex.dlof.ui.comments.CommentsSheet] hides for this viewer only.
 * Deliberately local-only (like [SettingsPrefsStore]) rather than synced to
 * /users/{uid}: it's a personal filter, not something that needs to follow
 * the account across devices, and keeping it out of the publicly-readable
 * /users node avoids exposing a person's muted-word list to anyone browsing
 * their profile data.
 */
object MutedWordsStore {
    private const val PREFS = "yeex_prefs"
    private const val KEY = "muted_words"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<String> =
        (prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()).sorted()

    fun add(context: Context, word: String) {
        val trimmed = word.trim().lowercase()
        if (trimmed.isBlank()) return
        val current = prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()
        prefs(context).edit().putStringSet(KEY, current + trimmed).apply()
    }

    fun remove(context: Context, word: String) {
        val current = prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()
        prefs(context).edit().putStringSet(KEY, current - word).apply()
    }

    /** True if [text] contains any muted word/phrase as a case-insensitive substring. */
    fun matches(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return getAll(context).any { lower.contains(it) }
    }
}
