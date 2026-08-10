package com.yeex.dlof.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * Device-only preferences for the Settings & Privacy screen that have no
 * server-side equivalent:
 *  - [themeMode]: "system" | "light" | "dark", read by MainActivity when
 *    building [com.yeex.dlof.ui.theme.YeexTheme] on every recomposition —
 *    held as a Compose [androidx.compose.runtime.MutableState] (rather than
 *    a plain SharedPreferences read like [LocaleUtil]) so flipping it in
 *    Settings repaints the whole app immediately, with no
 *    Activity.recreate() needed (unlike the language switcher, changing
 *    color scheme doesn't need a Configuration change).
 *  - Per-category notification toggles: this app has no push/FCM
 *    infrastructure yet, so these are stored locally as the preference a
 *    future notification system would read, rather than being no-ops.
 *
 * Same "yeex_prefs" file as [LocaleUtil] — different keys, no collision.
 */
object SettingsPrefsStore {
    private const val PREFS = "yeex_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_NOTIFY_LIKES = "notify_likes"
    private const val KEY_NOTIFY_COMMENTS = "notify_comments"
    private const val KEY_NOTIFY_TEKERS = "notify_tekers"
    private const val KEY_NOTIFY_ROOMS = "notify_rooms"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    /** Read once at process start (see MainActivity), then updated in-memory as the source of truth for [YeexTheme]. */
    val themeMode = mutableStateOf(THEME_SYSTEM)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call once, before the first [com.yeex.dlof.ui.theme.YeexTheme] composition (MainActivity.onCreate). */
    fun init(context: Context) {
        themeMode.value = prefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        themeMode.value = mode
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    data class NotificationPrefs(
        val likes: Boolean = true,
        val comments: Boolean = true,
        val tekers: Boolean = true,
        val rooms: Boolean = true
    )

    fun getNotificationPrefs(context: Context): NotificationPrefs {
        val p = prefs(context)
        return NotificationPrefs(
            likes = p.getBoolean(KEY_NOTIFY_LIKES, true),
            comments = p.getBoolean(KEY_NOTIFY_COMMENTS, true),
            tekers = p.getBoolean(KEY_NOTIFY_TEKERS, true),
            rooms = p.getBoolean(KEY_NOTIFY_ROOMS, true)
        )
    }

    fun setNotificationPref(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    const val NOTIFY_LIKES_KEY = KEY_NOTIFY_LIKES
    const val NOTIFY_COMMENTS_KEY = KEY_NOTIFY_COMMENTS
    const val NOTIFY_TEKERS_KEY = KEY_NOTIFY_TEKERS
    const val NOTIFY_ROOMS_KEY = KEY_NOTIFY_ROOMS
}
