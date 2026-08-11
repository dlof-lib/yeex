package com.yeex.dlof.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yeex.dlof.data.model.LinkCardType
import com.yeex.dlof.data.model.LinkPreview
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCard
import com.yeex.dlof.ui.theme.YeexCircleIconBadge
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.ui.theme.YeexIconBadge

/**
 * The "🔗 نشر الروابط" Link Card: turns a pasted URL into a tappable
 * YouTube/GitHub/website/media card instead of showing the raw link — see
 * [com.yeex.dlof.util.LinkPreviewUtil] for how [preview] is built. Built on
 * the shared [YeexCard]/[YeexIconBadge] primitives (AppStyle.kt).
 */
@Composable
fun LinkPreviewCard(preview: LinkPreview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cardType = runCatching { LinkCardType.valueOf(preview.cardType) }.getOrDefault(LinkCardType.WEBSITE)

    YeexCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(YeexDimens.radiusMedium)) {
        Column {
            if (preview.imageUrl.isNotBlank()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = preview.imageUrl,
                        contentDescription = preview.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                    if (cardType == LinkCardType.YOUTUBE || cardType == LinkCardType.VIDEO) {
                        YeexCircleIconBadge(
                            icon = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(YeexDimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preview.imageUrl.isBlank()) {
                    YeexIconBadge(icon = cardIcon(cardType), contentDescription = null)
                    Spacer(Modifier.size(YeexDimens.spaceSm))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        preview.title.ifBlank { preview.url },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (preview.description.isNotBlank()) {
                        Text(
                            preview.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        preview.siteName.ifBlank { preview.url },
                        style = MaterialTheme.typography.labelSmall,
                        color = YeexAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun cardIcon(type: LinkCardType) = when (type) {
    LinkCardType.YOUTUBE -> Icons.Filled.PlayArrow
    LinkCardType.GITHUB -> Icons.Filled.Code
    LinkCardType.IMAGE -> Icons.Filled.Image
    LinkCardType.VIDEO -> Icons.Filled.PlayArrow
    LinkCardType.ARTICLE, LinkCardType.WEBSITE -> Icons.Filled.Language
}
