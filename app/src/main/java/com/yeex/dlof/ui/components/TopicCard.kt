package com.yeex.dlof.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicType
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCard
import com.yeex.dlof.ui.theme.YeexChip
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.util.MediaBase64

/**
 * A single row in [com.yeex.dlof.ui.topics.TopicsScreen] / a profile's
 * topics tab — permanent, text-first, so unlike [ParagraphCard] this is a
 * normal scrolling list card, not a full-bleed swiped page. Built on the
 * shared [YeexCard]/[YeexChip] primitives (see AppStyle.kt) so it matches
 * the rest of the app's card/chip styling instead of its own one-off look.
 */
@Composable
fun TopicCard(
    topic: Topic,
    authorIconBase64: String = "",
    hasLiked: Boolean = false,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    YeexCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = YeexDimens.spaceMd, vertical = 6.dp)
    ) {
        Column(Modifier.padding(YeexDimens.spaceLg - 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile(topic.authorId) }) {
                UserAvatar(authorIconBase64, size = YeexDimens.avatarSizeSmall)
                Spacer(Modifier.width(YeexDimens.spaceSm))
                Text("@${topic.authorIdentifier}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (topic.authorVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                val typeLabel = if (topic.type == TopicType.LINK.name) stringResource(R.string.topic_type_link) else stringResource(R.string.topic_type_text)
                YeexChip(label = typeLabel, selected = false)
            }

            Spacer(Modifier.size(10.dp))

            if (topic.title.isNotBlank()) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(4.dp))
            }

            if (topic.body.isNotBlank()) {
                Text(
                    topic.body.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: topic.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val link = topic.link
            if (link != null) {
                Spacer(Modifier.size(YeexDimens.spaceSm))
                LinkPreviewCard(preview = link, onClick = onOpen)
            } else if (topic.imageBase64.isNotBlank()) {
                Spacer(Modifier.size(YeexDimens.spaceSm))
                val cover = remember(topic.imageBase64) {
                    runCatching { MediaBase64.decodeToBitmap(topic.imageBase64) }.getOrNull()
                }
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = topic.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(YeexDimens.radiusSmall))
                    )
                }
            }

            if (topic.hashtags.isNotEmpty()) {
                Spacer(Modifier.size(YeexDimens.spaceSm))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topic.hashtags) { tag ->
                        YeexChip(label = "#$tag", selected = true)
                    }
                }
            }

            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (topic.updateCount > 0) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.topic_update_count, topic.updateCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = YeexAccent
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLike() }
                ) {
                    Icon(
                        if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_like),
                        tint = if (hasLiked) YeexCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(topic.likeCount.toString(), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(topic.commentCount.toString(), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(topic.viewCount.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

