package com.yeex.dlof

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yeex.dlof.navigation.YeexNavGraph
import com.yeex.dlof.ui.theme.YeexTheme
import com.yeex.dlof.util.LocaleUtil

class MainActivity : ComponentActivity() {

    // Applies the saved in-app language (ar/en/es) before any resources are
    // resolved, so the whole activity — including its string resources and
    // layout direction (RTL for ar) — starts in the right language, and
    // survives ProfileScreen's LocaleUtil.saveLanguage() + recreate() flow.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YeexTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YeexNavGraph()
                }
            }
        }
    }
}
