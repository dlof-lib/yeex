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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.BlockRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexLike
import com.yeex.dlof.util.MutedWordsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

/**
 * The comment thread for a paragraph, designed to be hosted inside a
 * [ModalBottomSheet] (see [FeedScreen][com.yeex.dlof.ui.feed.FeedScreen] /
 * [RoomScreen][com.yeex.dlof.ui.room.RoomScreen]) instead of navigating to a
 * separate full screen — same "منبثقة" (pop-up) treatment as publishing and
 * editing the profile, so the feed stays mounted underneath and dismissing
 * is a single swipe-down.
 *
 * Threaded one level deep (top-level comments + replies, matching the
 * reference design): tapping "رد" under any comment — top-level or a reply —
 * targets the same top-level parent, so replies never nest past one level.
 * Each comment/reply also has its own heart + like count.
 */
@Composable
fun CommentsSheet(
    paragraphId: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: ParagraphRepository = ParagraphRepository(),
    blockRepo: BlockRepository = BlockRepository(),
    onOpenProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var avatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var likedByMe by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replyTarget by remember { mutableStateOf<Comment?>(null) }
    val myUid = authRepo.currentUid()

    // One-shot fetch, same trade-off as FeedViewModel's blockedUids — see
    // that field's doc comment. Re-fetched every time this sheet is opened
    // (cheap: it's a single small read), so a block made in Settings shows
    // up here on the very next comments sheet even without an app restart.
    var blockedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(myUid) {
        if (myUid != null) blockedUids = runCatching { blockRepo.blockedUidSet(myUid) }.getOrDefault(emptySet())
    }

    LaunchedEffect(paragraphId) {
        // .catch prevents a Firebase listener error (offline/cancelled) from
        // propagating as an uncaught exception out of this coroutine, which
        // would otherwise crash the app instead of just stalling the list.
        repo.observeComments(paragraphId).catch { isLoading = false }.collect { fetched ->
            comments = fetched
            isLoading = false
            // Best-effort avatar fetch for authors we haven't resolved yet —
            // keeps the list feeling like a real conversation instead of bare
            // "@handle" rows.
            val missingAvatars = fetched.map { c -> c.authorId }.distinct().filterNot { avatars.containsKey(it) }
            if (missingAvatars.isNotEmpty()) {
                val fetchedAvatars = missingAvatars.mapNotNull { uid ->
                    runCatching { userRepo.getUser(uid) }.getOrNull()?.let { uid to it.profileIconUrl }
                }
                if (fetchedAvatars.isNotEmpty()) avatars = avatars + fetchedAvatars
            }
            // Resolve which of these comments the viewer has already liked.
            if (myUid != null) {
                val missingLikes = fetched.map { it.id }.filterNot { likedByMe.contains(it) }
                val liked = missingLikes.filter { cid ->
                    runCatching { repo.getCommentLikedByMe(paragraphId, cid, myUid) }.getOrDefault(false)
                }
                if (liked.isNotEmpty()) likedByMe = likedByMe + liked
            }
        }
    }

    // "الكلمات المكتومة" + blocked authors — both filtered client-side, for
    // this viewer only, same as MutedWordsStore/BlockRepository's doc
    // comments describe. A comment hidden this way still exists and still
    // counts toward [comments.size] in the header — this only hides it from
    // the list, it doesn't moderate it for anyone else.
    fun isVisible(c: Comment): Boolean =
        c.authorId !in blockedUids && !MutedWordsStore.matches(context, c.text)

    val topLevel = comments.filter { it.parentId.isBlank() && isVisible(it) }.sortedByDescending { it.createdAt }
    val repliesByParent = comments.filter { it.parentId.isNotBlank() && isVisible(it) }.groupBy { it.parentId }

    fun toggleLike(comment: Comment) {
        val uid = myUid ?: return
        val wasLiked = likedByMe.contains(comment.id)
        // Optimistic UI, reconciled by the Firebase listener right after.
        likedByMe = if (wasLiked) likedByMe - comment.id else likedByMe + comment.id
        comments = comments.map { if (it.id == comment.id) it.copy(likeCount = (it.likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)) else it }
        scope.launch { runCatching { repo.toggleCommentLike(paragraphId, comment.id, uid) } }
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
                    items(topLevel, key = { it.id }) { c ->
                        CommentRow(
                            comment = c,
                            avatarBase64 = avatars[c.authorId].orEmpty(),
                            hasLiked = likedByMe.contains(c.id),
                            onClickAuthor = { if (c.authorId.isNotBlank()) onOpenProfile(c.authorId) },
                            onLike = { toggleLike(c) },
                            onReply = { replyTarget = c }
                        )
                        val replies = repliesByParent[c.id].orEmpty().sortedBy { it.createdAt }
                        replies.forEach { r ->
                            CommentRow(
                                comment = r,
                                avatarBase64 = avatars[r.authorId].orEmpty(),
                                hasLiked = likedByMe.contains(r.id),
                                onClickAuthor = { if (r.authorId.isNotBlank()) onOpenProfile(r.authorId) },
                                onLike = { toggleLike(r) },
                                onReply = { replyTarget = c }, // replies to a reply still target the thread's top-level comment
                                isReply = true
                            )
                        }
                    }
                }
            }
        }

        // ---- Optional "replying to @handle" banner ----
        replyTarget?.let { target ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.replying_to, target.authorIdentifier),
                    style = MaterialTheme.typography.labelMedium,
                    color = YeexAccent,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier
                        .size(18.dp)
                        .clickableNoRipple { replyTarget = null },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    val uid = myUid ?: return@IconButton
                    val text = input.trim()
                    val parentId = replyTarget?.id ?: ""
                    scope.launch {
                        isSending = true
                        val me = runCatching { userRepo.getUser(uid) }.getOrNull()
                        runCatching {
                            repo.addComment(
                                Comment(
                                    paragraphId = paragraphId,
                                    authorId = uid,
                                    authorIdentifier = me?.identifier ?: "",
                                    text = text,
                                    parentId = parentId
                                )
                            )
                        }
                        input = ""
                        replyTarget = null
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
private fun CommentRow(
    comment: Comment,
    avatarBase64: String,
    hasLiked: Boolean,
    onClickAuthor: () -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit,
    isReply: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 40.dp else 0.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            iconBase64 = avatarBase64,
            size = if (isReply) 30.dp else 36.dp,
            modifier = Modifier.clickableNoRipple(onClickAuthor)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorIdentifier,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.action_reply),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickableNoRipple(onReply)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
            Icon(
                if (hasLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.action_like),
                tint = if (hasLiked) YeexLike else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickableNoRipple(onLike)
            )
            if (comment.likeCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text("${comment.likeCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
