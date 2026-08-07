package com.yeex.dlof.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
            RailAction(
                icon = if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (hasLiked) YeexCrimson else Color.White,
                count = paragraph.likeCount,
                contentDescription = stringResource(R.string.action_like),
                onClick = onLike
            )
            RailAction(
                icon = Icons.Filled.ThumbDown,
                tint = if (hasDisliked) YeexAccent else Color.White,
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
