package com.yeex.dlof.ui.create

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One selectable category for a Moment stage: a short, storage-safe [key]
 * (persisted in [com.yeex.dlof.data.model.MomentStep.icon]) paired with the
 * real vector icon drawn for it everywhere in the UI.
 *
 * This used to be a raw emoji palette drawn with plain `Text`/`drawText`.
 * Multi-codepoint emoji especially (✈️, 🍽️ — base glyph + variation
 * selector) render as tofu boxes or the wrong glyph on devices/builds
 * without a full color-emoji font (GSI images, some OEM skins, budget
 * devices) — the same class of problem `WatermarkUtil` already works around
 * for its heart/eye icons by drawing them as vector paths instead of text.
 * Real [ImageVector] icons render identically everywhere, so both the
 * composer's picker ([com.yeex.dlof.ui.create.MomentComposer]) and the
 * published timeline ([com.yeex.dlof.ui.components.MomentTimeline]) use
 * this instead of the emoji character.
 */
data class MomentIconOption(val key: String, val icon: ImageVector)

val MOMENT_ICON_PALETTE = listOf(
    MomentIconOption("location", Icons.Filled.LocationOn),
    MomentIconOption("car", Icons.Filled.DirectionsCar),
    MomentIconOption("flight", Icons.Filled.Flight),
    MomentIconOption("coffee", Icons.Filled.LocalCafe),
    MomentIconOption("food", Icons.Filled.Restaurant),
    MomentIconOption("sunset", Icons.Filled.WbTwilight),
    MomentIconOption("home", Icons.Filled.Home),
    MomentIconOption("sports", Icons.Filled.SportsSoccer),
    MomentIconOption("flag_yellow", Icons.Filled.Flag),
    MomentIconOption("flag_red", Icons.Filled.OutlinedFlag),
    MomentIconOption("refresh", Icons.Filled.Autorenew),
    MomentIconOption("target", Icons.Filled.GpsFixed),
    MomentIconOption("celebration", Icons.Filled.Celebration),
    MomentIconOption("calendar", Icons.Filled.CalendarToday),
    MomentIconOption("time", Icons.Filled.AccessTime),
    MomentIconOption("check", Icons.Filled.CheckCircle),
    MomentIconOption("cancel", Icons.Filled.Cancel),
    MomentIconOption("chat", Icons.Filled.ChatBubble),
    MomentIconOption("camera", Icons.Filled.PhotoCamera),
    MomentIconOption("trophy", Icons.Filled.EmojiEvents)
)

/**
 * Moments published before this change have the old emoji characters saved
 * in [com.yeex.dlof.data.model.MomentStep.icon] rather than a key from
 * [MOMENT_ICON_PALETTE] — mapped here so they still render their original
 * category's icon instead of silently falling back to the default pin.
 */
private val LEGACY_EMOJI_TO_KEY = mapOf(
    "📍" to "location", "🚗" to "car", "✈️" to "flight", "☕" to "coffee",
    "🍽️" to "food", "🌅" to "sunset", "🏠" to "home", "⚽" to "sports",
    "🟨" to "flag_yellow", "🟥" to "flag_red", "🔄" to "refresh", "🎯" to "target",
    "🎉" to "celebration", "📅" to "calendar", "⏰" to "time", "✅" to "check",
    "❌" to "cancel", "💬" to "chat", "📸" to "camera", "🏆" to "trophy"
)

/** Resolves a [com.yeex.dlof.data.model.MomentStep.icon] value (a palette key, a legacy emoji, or blank) to the vector icon to draw for it. */
fun momentIconFor(rawIcon: String): ImageVector {
    val key = LEGACY_EMOJI_TO_KEY[rawIcon] ?: rawIcon
    return MOMENT_ICON_PALETTE.firstOrNull { it.key == key }?.icon ?: MOMENT_ICON_PALETTE.first().icon
}
