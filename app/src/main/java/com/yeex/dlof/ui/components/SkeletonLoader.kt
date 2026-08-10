package com.yeex.dlof.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Full-bleed shimmer placeholder shaped like the real [ParagraphCard] —
 * edge-to-edge dark "media" area, a bottom-start author row + two caption
 * bars, and a bottom-end vertical action rail with icon-sized circles — so
 * the skeleton reads as "a video/photo card is loading" instead of a
 * generic floating rectangle. Shown by FeedScreen while the initial
 * Firebase snapshot is still loading, in place of the "no paragraphs yet"
 * empty state. Uses [darkBase] shimmer tones throughout since the real feed
 * is always on a black background (see FeedScreen).
 */
@Composable
fun ParagraphSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // ---- Media area ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush(darkBase = true))
        )

        // ---- Right-side action rail (mirrors ParagraphCard's rail) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(modifier = Modifier.size(42.dp), shape = CircleShape, darkBase = true)
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(modifier = Modifier.width(18.dp).height(9.dp), darkBase = true)
                }
            }
        }

        // ---- Bottom-start author + caption placeholders (mirrors ParagraphCard) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 84.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(modifier = Modifier.size(24.dp), shape = CircleShape, darkBase = true)
                Spacer(Modifier.width(8.dp))
                ShimmerBox(modifier = Modifier.width(96.dp).height(12.dp), darkBase = true)
            }
            Spacer(Modifier.height(12.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.78f).height(13.dp), darkBase = true)
            Spacer(Modifier.height(7.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(13.dp), darkBase = true)
        }
    }
}

/**
 * A pulsing shimmer block, exposed as a plain placeholder so loading states
 * around the app (room lists, room header, search results, the feed above)
 * can reuse the same animated brush instead of re-implementing it. [darkBase]
 * switches the tone from the light Material surface variant (default, for
 * skeletons drawn over a normal screen background) to a translucent white
 * wash suited to full-bleed dark screens like the feed.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    darkBase: Boolean = false
) {
    Box(modifier = modifier.background(shimmerBrush(darkBase), shape))
}

/**
 * Animated diagonal sweep brush shared by every skeleton in the app. A wide
 * five-stop gradient (rather than a single highlight sandwiched between two
 * flat stops) gives the sweep a soft leading/trailing fade instead of a hard
 * edge, and the longer travel distance + duration below reads as a gentle
 * glide rather than a flicker.
 */
@Composable
private fun shimmerBrush(darkBase: Boolean = false): Brush {
    val base = if (darkBase) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
    val mid = if (darkBase) Color.White.copy(alpha = 0.16f) else base.copy(alpha = 0.65f)
    val highlight = if (darkBase) Color.White.copy(alpha = 0.24f) else base.copy(alpha = 0.85f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(base, mid, highlight, mid, base),
        start = Offset(translate - 400f, 0f),
        end = Offset(translate + 400f, 400f)
    )
}
