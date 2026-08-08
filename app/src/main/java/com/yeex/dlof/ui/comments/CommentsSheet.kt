package com.yeex.dlof.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexNavy
import kotlinx.coroutines.launch

/**
 * The comment thread for a paragraph, designed to be hosted inside a
 * [ModalBottomSheet] (see [FeedScreen][com.yeex.dlof.ui.feed.FeedScreen] /
 * [RoomScreen][com.yeex.dlof.ui.room.RoomScreen]) instead of navigating to a
 * separate full screen — same "منبثقة" (pop-up) treatment as publishing and
 * editing the profile, so the feed stays mounted underneath and dismissing
 * is a single swipe-down.
 */
@Composable
fun CommentsSheet(
    paragraphId: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: ParagraphRepository = ParagraphRepository(),
    onOpenProfile: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var avatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(paragraphId) {
        repo.observeComments(paragraphId).collect {
            comments = it.sortedByDescending { c -> c.createdAt }
            isLoading = false
            // Best-effort avatar fetch for authors we haven't resolved yet —
            // keeps the list feeling like a real conversation instead of bare
            // "@handle" rows.
            val missing = comments.map { c -> c.authorId }.distinct().filterNot { avatars.containsKey(it) }
            if (missing.isNotEmpty()) {
                val fetched = missing.mapNotNull { uid ->
                    runCatching { userRepo.getUser(uid) }.getOrNull()?.let { uid to it.profileIconUrl }
                }
                if (fetched.isNotEmpty()) avatars = avatars + fetched
            }
        }
    }

    Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 620.dp)) {
        // ---- Header: drag handle is provided by ModalBottomSheet itself; this is the title row ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.comments_title, comments.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))

        Box(Modifier.weight(1f, fill = false)) {
            when {
                isLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                comments.isEmpty() -> EmptyCommentsState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(comments, key = { it.id }) { c ->
                        CommentRow(
                            comment = c,
                            avatarBase64 = avatars[c.authorId].orEmpty(),
                            onClickAuthor = { if (c.authorId.isNotBlank()) onOpenProfile(c.authorId) }
                        )
                    }
                }
            }
        }

        // ---- Composer bar, pinned to the bottom of the sheet ----
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 500) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.comment_hint)) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            val canSend = !isSending && input.isNotBlank()
            IconButton(
                enabled = canSend,
                onClick = {
                    val uid = authRepo.currentUid() ?: return@IconButton
                    val text = input.trim()
                    scope.launch {
                        isSending = true
                        val me = runCatching { userRepo.getUser(uid) }.getOrNull()
                        runCatching {
                            repo.addComment(
                                Comment(
                                    paragraphId = paragraphId,
                                    authorId = uid,
                                    authorIdentifier = me?.identifier ?: "",
                                    text = text
                                )
                            )
                        }
                        input = ""
                        isSending = false
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) YeexAccent else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = stringResource(R.string.action_comment),
                    tint = if (canSend) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyCommentsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.comments_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CommentRow(comment: Comment, avatarBase64: String, onClickAuthor: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            iconBase64 = avatarBase64,
            size = 36.dp,
            modifier = Modifier.clickableNoRipple(onClickAuthor)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${comment.authorIdentifier}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = YeexNavy,
                    modifier = Modifier.clickableNoRipple(onClickAuthor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    RelativeTime.label(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)

/** Short, localized relative time ("now" / "5m" / "3h" / "2d") for each comment. */
private object RelativeTime {
    @Composable
    fun label(createdAt: Long): String {
        if (createdAt <= 0L) return ""
        val diffMs = (System.currentTimeMillis() - createdAt).coerceAtLeast(0)
        val minutes = diffMs / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> stringResource(R.string.time_just_now)
            minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
            hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
            else -> stringResource(R.string.time_days_ago, days.toInt())
        }
    }
}
