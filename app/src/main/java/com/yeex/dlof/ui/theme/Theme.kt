package com.yeex.dlof.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Brand identity — deep violet/black base with a purple → pink signature
// gradient (avatar rings, primary buttons, active tab, "جديد" accents). ----
val YeexNavy = Color(0xFF6D28D9)          // kept as name for compatibility; now violet
val YeexNavyDark = Color(0xFF0B0A14)
val YeexNavyLight = Color(0xFF9333EA)
val YeexAccent = Color(0xFF9B5CF6)        // primary purple

// Second gradient stop — hot pink/magenta, echoes the verified badge without
// being the exact same hue.
val YeexPink = Color(0xFFEE2A8B)

// Reserved exclusively for the verification checkmark — never used as a
// general UI/button color so the badge stays visually unique.
val YeexCrimson = Color(0xFFF0195A)

// Reaction-rail colors — distinct from YeexCrimson so the verified badge
// stays visually unique to verification only.
val YeexLike = Color(0xFFFF2D55)
val YeexLikeGlow = Color(0xFFFF6B8B)
val YeexDislike = Color(0xFF8B93FF)

val YeexBlack = Color(0xFF0A0912)
val YeexDarkSurface = Color(0xFF17151F)
val YeexDarkSurfaceVariant = Color(0xFF1E1B29)
val YeexDarkCard = Color(0xFF1B1826)
val YeexWhite = Color(0xFFF6F5FA)
val YeexGray = Color(0xFF9B96A8)

/** The signature purple → pink brand gradient used across avatar rings, the
 * publish/tek buttons, and highlighted chips. */
val YeexBrandGradient = Brush.linearGradient(listOf(YeexAccent, YeexPink))
fun yeexBrandGradient(): Brush = Brush.linearGradient(listOf(YeexNavyLight, YeexAccent, YeexPink))

private val DarkColors = darkColorScheme(
    primary = YeexAccent,
    onPrimary = YeexWhite,
    primaryContainer = YeexDarkSurfaceVariant,
    onPrimaryContainer = YeexAccent,
    secondary = YeexPink,
    onSecondary = YeexWhite,
    background = YeexBlack,
    onBackground = YeexWhite,
    surface = YeexDarkSurface,
    onSurface = YeexWhite,
    surfaceVariant = YeexDarkSurfaceVariant,
    onSurfaceVariant = YeexGray,
    outline = Color(0xFF322D42),
    error = Color(0xFFFF5470)
)

private val LightColors = lightColorScheme(
    primary = YeexNavyLight,
    onPrimary = Color.White,
    secondary = YeexPink,
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
