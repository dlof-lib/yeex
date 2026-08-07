package com.yeex.dlof.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.Locale

/**
 * Applies and persists an in-app language choice (ar / en / es) independent
 * of the phone's system locale. The project has no androidx.appcompat
 * dependency (pure Compose), so this uses the plain framework approach —
 * wrapping the base Context with an updated Configuration in
 * MainActivity.attachBaseContext — rather than AppCompatDelegate.
 *
 * Persisted choice takes priority over User.language on Firebase: it's what
 * lets someone pick a language before ever logging in, and keeps working
 * offline. When the user changes it from ProfileScreen we write through to
 * both SharedPreferences (for next app start) and /users/{uid}/language.
 */
object LocaleUtil {
    private const val PREFS = "yeex_prefs"
    private const val KEY_LANGUAGE = "app_language"
    val SUPPORTED = listOf("ar", "en", "es")
    const val DEFAULT_LANGUAGE = "ar"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSavedLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun saveLanguage(context: Context, languageCode: String) {
        require(languageCode in SUPPORTED) { "Unsupported language: $languageCode" }
        prefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /** Wraps [base] with a Configuration pinned to the saved (or given) language. */
    fun wrapContext(base: Context, languageCode: String = getSavedLanguage(base)): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(config, base.resources.displayMetrics)
            base
        }
    }
}
