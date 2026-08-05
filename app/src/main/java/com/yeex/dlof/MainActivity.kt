package com.yeex.dlof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yeex.dlof.navigation.YeexNavGraph
import com.yeex.dlof.ui.theme.YeexTheme

class MainActivity : ComponentActivity() {
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
