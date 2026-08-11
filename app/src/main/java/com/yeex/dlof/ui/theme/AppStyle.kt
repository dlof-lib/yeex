package com.yeex.dlof.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * YEEX's shared design tokens — one place for the spacing scale, corner
 * radii, border width, and icon sizes every screen should draw from, instead
 * of each screen picking its own `12.dp` / `14.dp` / `16.dp` ad hoc. Paired
 * with the reusable primitives below ([YeexCard], [YeexChip],
 * [YeexPrimaryButton], [YeexIconBadge], [YeexSectionHeader]) this is the
 * app's single "unified style" layer, sitting on top of the brand
 * colors/typography already defined in Theme.kt.
 */
object YeexDimens {
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 24.dp

    val radiusSmall = 10.dp
    val radiusMedium = 14.dp
    val radiusLarge = 16.dp
    val radiusPill = 50.dp

    val borderWidth = 1.dp
    val borderWidthSelected = 2.dp

    val iconBadgeSize = 40.dp
    val avatarSizeSmall = 30.dp
    val avatarSizeMedium = 36.dp
    val buttonHeight = 50.dp
}

/**
 * The app's standard elevated surface — YeexDarkCard fill, a hairline
 * outline, and [YeexDimens.radiusLarge] corners. Every card-shaped block in
 * the app (paragraph/topic list rows, link previews, attach tiles) should be
 * built on this instead of a bespoke `Card`/`Surface` so corner radius,
 * border, and fill always match.
 */
@Composable
fun YeexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(YeexDimens.radiusLarge),
    containerColor: Color = YeexDarkCard,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = shape,
        color = containerColor,
        border = BorderStroke(YeexDimens.borderWidth, borderColor),
        content = content
    )
}

/**
 * A single tappable pill — used for type switches (نص/رابط), filter tabs,
 * and tag/hashtag tokens across the app. [selected] swaps between the solid
 * brand-accent fill (active) and a subdued surfaceVariant fill (inactive),
 * the same two states [TekButton] already uses for its own pill.
 */
@Composable
fun YeexChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(YeexDimens.radiusPill),
        color = if (selected) YeexAccent else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = YeexDimens.spaceLg, vertical = YeexDimens.spaceSm)
        )
    }
}

/**
 * The app's one primary call-to-action button style: the purple → pink
 * brand gradient on a full-width pill, matching [TekButton]'s "not
 * following" state and the publish button already used on paragraphs. Every
 * "نشر / تأكيد" action should use this instead of a bespoke gradient Box.
 */
@Composable
fun YeexPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(YeexDimens.radiusPill),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().height(YeexDimens.buttonHeight)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(yeexBrandGradient(), RoundedCornerShape(YeexDimens.radiusPill)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(text, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A small circular icon tile in a tinted accent background — the shape
 * already used for a link card's fallback icon and a composer toolbar
 * button. Centralized here so both read the same size/tint/corner values.
 */
@Composable
fun YeexIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = YeexDimens.iconBadgeSize,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(YeexDimens.radiusSmall),
    tint: Color = YeexAccent
) {
    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = shape,
        color = tint.copy(alpha = 0.15f),
        border = if (onClick != null) BorderStroke(YeexDimens.borderWidth, MaterialTheme.colorScheme.outline) else null
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** A circular icon-only badge — same tinted-background treatment as [YeexIconBadge] but round, e.g. a play button over a video thumbnail. */
@Composable
fun YeexCircleIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = Color.Black.copy(alpha = 0.55f),
    tint: Color = Color.White
) {
    Box(
        modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** The small "icon + bold label" row used above a content section (e.g. "التحديثات", "التعليقات"). */
@Composable
fun YeexSectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(YeexDimens.spaceSm))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
