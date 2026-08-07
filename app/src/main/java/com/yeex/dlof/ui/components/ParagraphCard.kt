package com.yeex.dlof.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.util.DownloadUtil
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.WatermarkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders a single paragraph as a SQUARE card (1:1 aspect ratio) — the unit
 * that's swiped left/right in [com.yeex.dlof.ui.feed.FeedScreen]'s
 * HorizontalPager. Works for text-only, image, and video-cover paragraphs.
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f), // square, per spec
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (paragraph.type) {
                ParagraphType.IMAGE.name, ParagraphType.VIDEO.name -> {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (paragraph.text.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(8.dp)
                        ) {
                            Text(paragraph.text, color = Color.White, maxLines = 2)
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(paragraph.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // author + verified badge
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("@${paragraph.authorIdentifier}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                if (paragraph.authorVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = stringResource(R.string.verified_badge), tint = Color(0xFFC81D3D))
                }
            }

            // action row
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_like),
                        tint = if (hasLiked) Color(0xFFC81D3D) else Color.White
                    )
                }
                Text("${paragraph.likeCount}", color = Color.White)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDislike) {
                    Icon(
                        Icons.Filled.ThumbDown,
                        contentDescription = stringResource(R.string.action_dislike),
                        tint = if (hasDisliked) Color(0xFFC81D3D) else Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onComment) { Text(stringResource(R.string.action_comment), color = Color.White) }
                TextButton(onClick = onRepost) { Text(stringResource(R.string.action_repost), color = Color.White) }
                if (bitmap != null) {
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.action_download), tint = Color.White)
                    }
                }
            }
        }
    }
}
