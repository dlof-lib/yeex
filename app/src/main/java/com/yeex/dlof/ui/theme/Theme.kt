package com.yeex.dlof.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YeexCrimson = Color(0xFFC81D3D)
val YeexBlack = Color(0xFF0E0E10)
val YeexDarkSurface = Color(0xFF18181B)
val YeexWhite = Color(0xFFF5F5F5)
val YeexGray = Color(0xFF8A8A8E)

private val DarkColors = darkColorScheme(
    primary = YeexCrimson,
    background = YeexBlack,
    surface = YeexDarkSurface,
    onPrimary = YeexWhite,
    onBackground = YeexWhite,
    onSurface = YeexWhite
)

private val LightColors = lightColorScheme(
    primary = YeexCrimson,
    background = YeexWhite,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = YeexBlack,
    onSurface = YeexBlack
)

@Composable
fun YeexTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
