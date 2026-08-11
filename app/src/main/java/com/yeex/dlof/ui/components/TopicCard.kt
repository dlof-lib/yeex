package com.yeex.dlof.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notes
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicType
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCard
import com.yeex.dlof.ui.theme.YeexChip
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.ui.theme.yeexBrandGradient
import com.yeex.dlof.util.MediaBase64

/**
 * A single row in [com.yeex.dlof.ui.topics.TopicsScreen] / a profile's
 * topics tab — permanent, text-first, so unlike [ParagraphCard] this is a
 * normal scrolling list card, not a full-bleed swiped page.
 *
 * Styled as its own distinct "article" card rather than a generic
 * avatar+text+icon-row social post: a brand-gradient spine along the
 * leading edge marks it as a permanent, reading-first Topic (vs. the
 * ephemeral square Paragraph), the byline is reduced to a quiet kicker line
 * instead of a filled type chip, and the reaction counts sit inside one
 * unified "stat rail" pill instead of loose floating icons — the one
 * signature flourish spent per card, everything else kept quiet.
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
    val isLink = topic.type == TopicType.LINK.name

    YeexCard(
        onClick = onOpen,
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = YeexDimens.spaceMd, vertical = 6.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Signature spine: a permanent, reading-first Topic reads as a
            // "bound page" rather than a feed row — full brand gradient for
            // a link-type topic (richer, produced content), a quieter single
            // accent tint for a plain text topic.
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        if (isLink) yeexBrandGradient()
                        else Brush.verticalGradient(listOf(YeexAccent.copy(alpha = 0.6f), YeexAccent.copy(alpha = 0.18f)))
                    )
            )

            Column(Modifier.padding(YeexDimens.spaceLg - 2.dp)) {
                // Byline: identity first, a quiet kicker (not a filled chip)
                // marks the type so filled pills stay reserved for hashtags.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile(topic.authorId) }) {
                    UserAvatar(authorIconBase64, size = YeexDimens.avatarSizeSmall)
                    Spacer(Modifier.width(YeexDimens.spaceSm))
                    Text("@${topic.authorIdentifier}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (topic.authorVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (isLink) Icons.Filled.Link else Icons.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (isLink) stringResource(R.string.topic_type_link) else stringResource(R.string.topic_type_text),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.size(12.dp))

                if (topic.title.isNotBlank()) {
                    Text(
                        topic.title,
                        style = MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.size(6.dp))
                }

                if (topic.body.isNotBlank()) {
                    Text(
                        topic.body.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: topic.body,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val link = topic.link
                if (link != null) {
                    Spacer(Modifier.size(YeexDimens.spaceMd))
                    LinkPreviewCard(preview = link, onClick = onOpen)
                } else if (topic.imageBase64.isNotBlank()) {
                    Spacer(Modifier.size(YeexDimens.spaceMd))
                    val cover = remember(topic.imageBase64) {
                        runCatching { MediaBase64.decodeToBitmap(topic.imageBase64) }.getOrNull()
                    }
                    if (cover != null) {
                        Image(
                            bitmap = cover.asImageBitmap(),
                            contentDescription = topic.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(YeexDimens.radiusMedium))
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

                Spacer(Modifier.size(YeexDimens.spaceMd))
                TopicStatRail(topic = topic, hasLiked = hasLiked, onLike = onLike)
            }
        }
    }
}

/**
 * The reaction/engagement counts as one cohesive rounded rail instead of
 * bare floating icons — reads like a single considered control rather than
 * a row of leftover metadata. Update count (when present) sits as its own
 * small accent badge just before the rail, since it's an author action
 * ("جديد") rather than a reader reaction.
 */
@Composable
private fun TopicStatRail(topic: Topic, hasLiked: Boolean, onLike: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (topic.updateCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(YeexAccent.copy(alpha = 0.14f), RoundedCornerShape(YeexDimens.radiusPill))
                    .padding(horizontal = YeexDimens.spaceSm, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.topic_update_count, topic.updateCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = YeexAccent
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(YeexDimens.radiusPill))
                .padding(horizontal = YeexDimens.spaceMd - 2.dp, vertical = 6.dp)
        ) {
            StatItem(
                icon = if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (hasLiked) YeexCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                count = topic.likeCount,
                contentDescription = stringResource(R.string.action_like),
                modifier = Modifier.clickable { onLike() }
            )
            StatDivider()
            StatItem(
                icon = Icons.Filled.ChatBubbleOutline,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = topic.commentCount
            )
            StatDivider()
            StatItem(
                icon = Icons.Filled.Visibility,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = topic.viewCount
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    count: Long,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = YeexDimens.spaceSm)
            .width(1.dp)
            .height(12.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
    )
}
