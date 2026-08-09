package com.yeex.dlof.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * A pulsing shimmer placeholder shaped like [ParagraphCard] (square, rounded
 * corners) plus two text-line bars, shown by FeedScreen while the initial
 * Firebase snapshot is still loading — distinct from the real "no paragraphs
 * yet" empty state.
 */
@Composable
fun ParagraphSkeleton(modifier: Modifier = Modifier) {
    val shimmerColor = shimmerBrush()
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(shimmerColor, RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(0.6f)
                .height(14.dp)
                .background(shimmerColor, RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.4f)
                .height(14.dp)
                .background(shimmerColor, RoundedCornerShape(4.dp))
        )
    }
}

/**
 * The same pulsing shimmer treatment as [ParagraphSkeleton], exposed as a
 * plain placeholder block so other loading states around the app (e.g.
 * search results) can reuse it instead of re-implementing the shimmer
 * animation themselves.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(6.dp)) {
    Box(modifier = modifier.background(shimmerBrush(), shape))
}

@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = base.copy(alpha = 0.4f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(translate - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(translate, 300f)
    )
}
