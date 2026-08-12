package com.yeex.dlof

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yeex.dlof.navigation.YeexNavGraph
import com.yeex.dlof.ui.common.NoInternetScreen
import com.yeex.dlof.ui.theme.YeexTheme
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.NetworkUtil
import com.yeex.dlof.util.PendingShareBridge
import com.yeex.dlof.util.ScreenshotWatcher
import com.yeex.dlof.util.SettingsPrefsStore

class MainActivity : ComponentActivity() {

    // Requested lazily, only the moment the person actually turns on
    // "اقتراح نشر لقطات الشاشة" in Settings (see the DisposableEffect in
    // onCreate) — asking for a media-read permission before it's needed
    // would be a confusing, unexplained prompt on first launch.
    private val requestImagesPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if denied, the feature simply stays off — nothing else to do here */ }

    // Applies the saved in-app language (ar/en/es) before any resources are
    // resolved, so the whole activity — including its string resources and
    // layout direction (RTL for ar) — starts in the right language, and
    // survives ProfileScreen's LocaleUtil.saveLanguage() + recreate() flow.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() per the core-splashscreen contract.
        // This is the brief OS-level splash (Theme.Yeex.Splash); it hands off
        // immediately to the branded Compose SplashScreen (see NavGraph's
        // Routes.SPLASH) which owns the actual animated logo + hold + navigate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Loads the saved "المظهر" (theme) choice from the "مظهر التطبيق" section of
        // Settings & Privacy before the first composition — see SettingsPrefsStore.
        SettingsPrefsStore.init(this)
        // Lets the feed (ParagraphCard) draw its media behind the status/nav
        // bars for a true full-screen TikTok-style look; NoInternetScreen and
        // other non-feed screens still get correct inset padding via Compose's
        // own systemBars insets in each screen that needs it.
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            val themeMode by SettingsPrefsStore.themeMode
            val textScale by SettingsPrefsStore.textScale
            val screenshotSuggestEnabled by SettingsPrefsStore.screenshotSuggestEnabled
            YeexTheme(
                darkTheme = when (themeMode) {
                    SettingsPrefsStore.THEME_DARK -> true
                    SettingsPrefsStore.THEME_LIGHT -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                fontScale = textScale
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current

                    // Starts/stops with the Settings toggle, and only while
                    // this Surface is actually composed (i.e. the activity
                    // is alive) — DisposableEffect's onDispose always runs
                    // the matching ScreenshotWatcher.stop(), so turning the
                    // feature off (or the activity going away) never leaves
                    // a dangling ContentObserver registered.
                    DisposableEffect(screenshotSuggestEnabled) {
                        if (screenshotSuggestEnabled) {
                            ensureImagesPermission()
                            ScreenshotWatcher.start(context) { uri ->
                                PendingShareBridge.offerScreenshot(uri)
                            }
                        }
                        onDispose { ScreenshotWatcher.stop(context) }
                    }

                    val isOnline by NetworkUtil.rememberIsOnline(context)
                    if (isOnline) {
                        YeexNavGraph()
                    } else {
                        NoInternetScreen(onRetry = { /* state re-evaluates automatically via the live network callback */ })
                    }
                }
            }
        }
    }

    // singleTask launch mode (see AndroidManifest) routes a repeat "Share to
    // YEEX" into this instead of a fresh onCreate — must re-parse the intent
    // here or a second share while YEEX is already open would be silently
    // dropped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Handles being launched as a share target from another app ("مشاركة" ->
     * YEEX). Only offers the content to [PendingShareBridge]/ShareTargetSheet
     * — publishing itself still goes through the normal composer flow once
     * the person picks "فقرة" or "موضوع", same as a manually-picked image.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        when {
            type.startsWith("image/") -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val caption = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (uri != null) PendingShareBridge.offerExternalShare(imageUri = uri, text = caption)
            }
            type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) PendingShareBridge.offerExternalShare(imageUri = null, text = text)
            }
        }
    }

    private fun ensureImagesPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) requestImagesPermission.launch(permission)
    }
}
