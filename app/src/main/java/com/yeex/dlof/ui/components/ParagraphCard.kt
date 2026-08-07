package com.yeex.dlof.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDislike
import com.yeex.dlof.ui.theme.YeexLike
import com.yeex.dlof.ui.theme.YeexLikeGlow
import com.yeex.dlof.util.DownloadUtil
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.WatermarkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders a single paragraph as an immersive, edge-to-edge FULL-SCREEN page —
 * TikTok-style — the unit [com.yeex.dlof.ui.feed.FeedScreen]'s HorizontalPager
 * swipes between left/right. Media fills the entire device screen (cropped,
 * like a short-video app) instead of sitting inside a padded square card;
 * actions live in a right-side vertical rail and author/caption sit above a
 * bottom scrim, both floating over the media.
 */
@Composable
fun ParagraphCard(
    paragraph: Paragraph,
    hasLiked: Boolean,
    hasDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watermarkLabel = stringResource(R.string.watermark_text)
    val savedMessage = stringResource(R.string.download_saved)
    val failedMessage = stringResource(R.string.download_failed)
    val hasMedia = paragraph.mediaBase64.isNotEmpty()
    val bitmap = remember(paragraph.id) {
        if (hasMedia) MediaBase64.decodeToBitmap(paragraph.mediaBase64) else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---- Background layer: media fills the whole screen, cropped like TikTok ----
        when (paragraph.type) {
            ParagraphType.IMAGE.name, ParagraphType.VIDEO.name -> {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                // Text-only paragraphs get a subtle brand gradient instead of a blank void.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(YeexAccent.copy(alpha = 0.55f), Color.Black)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        paragraph.text,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }
        }

        // ---- Bottom scrim for legibility over the media ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // ---- Right-side vertical action rail (TikTok-style) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ReactionButton(
                isActive = hasLiked,
                activeIcon = Icons.Filled.Favorite,
                inactiveIcon = Icons.Filled.FavoriteBorder,
                activeColor = YeexLike,
                glowColor = YeexLikeGlow,
                count = paragraph.likeCount,
                contentDescription = stringResource(R.string.action_like),
                onClick = onLike
            )
            ReactionButton(
                isActive = hasDisliked,
                activeIcon = Icons.Filled.ThumbDown,
                inactiveIcon = Icons.Outlined.ThumbDown,
                activeColor = YeexDislike,
                glowColor = YeexDislike,
                count = paragraph.dislikeCount,
                contentDescription = stringResource(R.string.action_dislike),
                onClick = onDislike
            )
            RailAction(
                icon = Icons.Filled.ChatBubble,
                tint = Color.White,
                count = paragraph.commentCount,
                contentDescription = stringResource(R.string.action_comment),
                onClick = onComment
            )
            RailAction(
                icon = Icons.Filled.Repeat,
                tint = Color.White,
                count = paragraph.repostCount,
                contentDescription = stringResource(R.string.action_repost),
                onClick = onRepost
            )
            if (bitmap != null) {
                RailAction(
                    icon = Icons.Filled.Download,
                    tint = Color.White,
                    count = null,
                    contentDescription = stringResource(R.string.action_download),
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                val watermarked = WatermarkUtil.applyWatermark(bitmap, watermarkLabel)
                                DownloadUtil.saveToGallery(context, watermarked, "yeex_${paragraph.id}")
                            }
                            Toast.makeText(
                                context,
                                if (ok) savedMessage else failedMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        // ---- Bottom-left author + caption overlay ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 84.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "@${paragraph.authorIdentifier}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (paragraph.authorVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.verified_badge),
                        tint = YeexCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (paragraph.text.isNotBlank() && paragraph.type != ParagraphType.TEXT.name) {
                Spacer(Modifier.height(6.dp))
                Text(
                    paragraph.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }
        }
    }
}

/** One icon+count entry in the right-side action rail, TikTok-style. */
@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Long?,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
        }
        if (count != null) {
            Text(
                formatCount(count),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/**
 * A premium, punchy like/dislike control for the reaction rail.
 *
 * - The icon crossfades + scales between its outline and filled states.
 * - A radial "glow" ring bursts outward and fades whenever the reaction
 *   becomes active, giving tactile confirmation (like Instagram/YouTube).
 * - The whole button springs (overshoot + settle) on every tap.
 * - When active, the circular backdrop turns into a soft brand-colored
 *   gradient instead of the flat translucent-black used by neutral rail
 *   actions, so "liked"/"disliked" state reads instantly at a glance.
 */
@Composable
private fun ReactionButton(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    activeColor: Color,
    glowColor: Color,
    count: Long,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Tap "punch": overshoots past 1f then settles — feels alive, not linear.
    val pressScale = remember { Animatable(1f) }
    // Burst ring: 0f (hidden) -> 1f (fully expanded + faded) on activation.
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val backdropColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
        animationSpec = tween(220),
        label = "reactionBackdrop"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f),
        animationSpec = tween(220),
        label = "reactionBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.White,
        animationSpec = tween(220),
        label = "reactionIconTint"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .scale(pressScale.value)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        scope.launch {
                            pressScale.animateTo(
                                targetValue = 1.28f,
                                animationSpec = tween(90, easing = FastOutSlowInEasing)
                            )
                            pressScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        if (!isActive) {
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
                            }
                        }
                        onClick()
                    }
                )
        ) {
            // Expanding, fading glow burst behind the icon on activation.
            if (burst.value > 0f) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .scale(0.7f + burst.value * 0.9f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.55f * (1f - burst.value)),
                                    glowColor.copy(alpha = 0f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Circular backdrop that shifts from neutral to brand-tinted.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(backdropColor)
                    .border(width = 1.2.dp, color = borderColor, shape = CircleShape)
            )

            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    (scaleIn(initialScale = 0.4f, animationSpec = tween(200)))
                        .togetherWith(scaleOut(targetScale = 0.4f, animationSpec = tween(150)))
                },
                label = "reactionIconSwap"
            ) { active ->
                Icon(
                    imageVector = if (active) activeIcon else inactiveIcon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            formatCount(count),
            color = if (isActive) activeColor else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
