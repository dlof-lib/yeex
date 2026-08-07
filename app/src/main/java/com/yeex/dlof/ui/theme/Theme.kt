package com.yeex.dlof.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand identity — derived from the app icon (navy cat mark).
val YeexNavy = Color(0xFF12185A)
val YeexNavyDark = Color(0xFF0B0E3C)
val YeexNavyLight = Color(0xFF2C3480)
val YeexAccent = Color(0xFF4C57C9)

// Reserved exclusively for the verification checkmark — never used as a
// general UI/button color so the badge stays visually unique.
val YeexCrimson = Color(0xFFC81D3D)

val YeexBlack = Color(0xFF0E0E10)
val YeexDarkSurface = Color(0xFF181A2E)
val YeexWhite = Color(0xFFF5F5F5)
val YeexGray = Color(0xFF8A8A8E)

private val DarkColors = darkColorScheme(
    primary = YeexNavyLight,
    onPrimary = YeexWhite,
    secondary = YeexAccent,
    background = YeexBlack,
    surface = YeexDarkSurface,
    onBackground = YeexWhite,
    onSurface = YeexWhite
)

private val LightColors = lightColorScheme(
    primary = YeexNavy,
    onPrimary = Color.White,
    secondary = YeexAccent,
    background = YeexWhite,
    surface = Color.White,
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
