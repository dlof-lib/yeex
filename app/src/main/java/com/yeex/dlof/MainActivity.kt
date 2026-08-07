package com.yeex.dlof

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.installSplashScreen
import com.yeex.dlof.navigation.YeexNavGraph
import com.yeex.dlof.ui.common.NoInternetScreen
import com.yeex.dlof.ui.theme.YeexTheme
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.NetworkUtil

class MainActivity : ComponentActivity() {

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
        // Lets the feed (ParagraphCard) draw its media behind the status/nav
        // bars for a true full-screen TikTok-style look; NoInternetScreen and
        // other non-feed screens still get correct inset padding via Compose's
        // own systemBars insets in each screen that needs it.
        enableEdgeToEdge()
        setContent {
            YeexTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
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
}
