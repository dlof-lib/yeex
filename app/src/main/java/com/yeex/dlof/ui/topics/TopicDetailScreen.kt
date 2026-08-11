package com.yeex.dlof.ui.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicUpdate
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.TopicRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.LinkPreviewCard
import com.yeex.dlof.ui.components.MarkdownText
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.ui.theme.YeexSectionHeader
import com.yeex.dlof.util.MediaBase64
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full read/discuss view of a single YEEX Topic: title + rendered body (or
 * Link Card), attached paragraphs (موضوع ↔ فقرة), the "Topic Updates" log
 * (only the author can add a new entry — see [Topic] doc), and a comment
 * thread. Unlike [com.yeex.dlof.ui.components.ParagraphCard] this is a
 * normal scrolling page: the content is meant to be read, not swiped past.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: TopicRepository = TopicRepository(),
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenParagraph: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()
    val uriHandler = LocalUriHandler.current

    var topic by remember { mutableStateOf<Topic?>(null) }
    var authorIcon by remember { mutableStateOf("") }
    var hasLiked by remember { mutableStateOf(false) }
    var updates by remember { mutableStateOf<List<TopicUpdate>>(emptyList()) }
    var linkedParagraphs by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentInput by remember { mutableStateOf("") }
    var updateInput by remember { mutableStateOf("") }
    var isSendingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(topicId) {
        repo.observeTopic(topicId).collect { t ->
            topic = t
            if (t != null) {
                if (authorIcon.isBlank()) authorIcon = userRepo.getUser(t.authorId)?.profileIconUrl.orEmpty()
                linkedParagraphs = repo.getLinkedParagraphs(t)
                if (myUid != null) {
                    hasLiked = repo.getLikedByMe(topicId, myUid)
                    repo.incrementView(topicId, myUid)
                }
            }
        }
    }
    LaunchedEffect(topicId) { repo.observeUpdates(topicId).collect { updates = it.sortedByDescending { u -> u.createdAt } } }
    LaunchedEffect(topicId) { repo.observeComments(topicId).collect { comments = it } }

    val current = topic
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YeexAccent) }
        return
    }

    val isOwner = myUid != null && myUid == current.authorId
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy — HH:mm", Locale("ar")) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBackIosNew, contentDescription = stringResource(R.string.cancel))
                }
                Spacer(Modifier.weight(1f))
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile(current.authorId) }) {
                    UserAvatar(authorIcon, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${current.authorIdentifier}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (current.authorVerified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            dateFormat.format(Date(current.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (current.title.isNotBlank()) {
                    Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                }

                val link = current.link
                if (link != null) {
                    LinkPreviewCard(preview = link, onClick = { runCatching { uriHandler.openUri(link.url) } })
                    Spacer(Modifier.height(10.dp))
                }

                if (current.imageBase64.isNotBlank()) {
                    val cover = remember(current.imageBase64) {
                        runCatching { MediaBase64.decodeToBitmap(current.imageBase64) }.getOrNull()
                    }
                    if (cover != null) {
                        androidx.compose.foundation.Image(
                            bitmap = cover.asImageBitmap(),
                            contentDescription = current.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (current.body.isNotBlank()) {
                    MarkdownText(
                        text = current.body,
                        onLinkClick = { url -> runCatching { uriHandler.openUri(url) } }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---- stats + like ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (myUid != null) {
                                scope.launch { hasLiked = repo.toggleLike(topicId, myUid) }
                            }
                        }
                    ) {
                        Icon(
                            if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.action_like),
                            tint = if (hasLiked) YeexCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(current.likeCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(20.dp))
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(current.commentCount.toString(), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(20.dp))
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(current.viewCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ---- موضوع ↔ فقرة: attached paragraphs ----
        if (linkedParagraphs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.topic_linked_paragraphs_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(linkedParagraphs, key = { it.id }) { p ->
                        Surface(
                            onClick = { onOpenParagraph(p.id) },
                            shape = RoundedCornerShape(YeexDimens.radiusSmall),
                            border = BorderStroke(YeexDimens.borderWidth, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(Modifier.size(90.dp)) {
                                val bmp = remember(p.mediaBase64) {
                                    if (p.mediaBase64.isNotBlank()) runCatching { MediaBase64.decodeToBitmap(p.mediaBase64) }.getOrNull() else null
                                }
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        Text(p.text.take(12), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Topic Updates timeline ----
        item {
            Spacer(Modifier.height(18.dp))
            YeexSectionHeader(
                icon = Icons.Filled.History,
                title = stringResource(R.string.topic_updates_title),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (isOwner) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = updateInput,
                        onValueChange = { updateInput = it },
                        placeholder = { Text(stringResource(R.string.topic_add_update_hint)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (updateInput.isNotBlank() && !isSendingUpdate) {
                                isSendingUpdate = true
                                val text = updateInput
                                updateInput = ""
                                scope.launch {
                                    runCatching { repo.addUpdate(topicId, text) }
                                    isSendingUpdate = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.topic_publish_button), tint = YeexAccent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (updates.isEmpty()) {
                Text(
                    stringResource(R.string.topic_no_updates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        items(updates, key = { it.id }) { update ->
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Box(
                    Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(YeexAccent)
                )
                Column {
                    Text(
                        stringResource(R.string.topic_update_label, updates.indexOf(update).let { updates.size - it }),
                        style = MaterialTheme.typography.labelSmall,
                        color = YeexAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(update.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        dateFormat.format(Date(update.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Comments ----
        item {
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.comments_title, comments.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text(stringResource(R.string.comment_hint)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (commentInput.isNotBlank() && myUid != null) {
                            val text = commentInput
                            commentInput = ""
                            scope.launch {
                                val me = userRepo.getUser(myUid)
                                runCatching {
                                    repo.addComment(
                                        Comment(
                                            paragraphId = topicId,
                                            authorId = myUid,
                                            authorIdentifier = me?.identifier.orEmpty(),
                                            text = text
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.comment_hint), tint = YeexAccent)
                }
            }
        }
        items(comments, key = { it.id }) { comment ->
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpenProfile(comment.authorId) }) {
                Column {
                    Text("@${comment.authorIdentifier}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
