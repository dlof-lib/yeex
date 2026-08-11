package com.yeex.dlof.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.ui.components.LiveStreamPlayer
import com.yeex.dlof.ui.components.ShimmerBox
import com.yeex.dlof.ui.feed.FeedScreen
import com.yeex.dlof.util.RoomCategory
import com.yeex.dlof.util.RoomType
import kotlinx.coroutines.launch

@Composable
fun RoomScreen(
    roomId: String,
    repo: RoomRepository = RoomRepository(),
    authRepo: AuthRepository = AuthRepository(),
    onOpenProfile: (String) -> Unit = {},
    onOpenTopic: (String) -> Unit = {},
    onRepost: (String) -> Unit
) {
    var room by remember { mutableStateOf<Room?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showStreamEditor by remember { mutableStateOf(false) }
    var showRulesEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()

    // Wrapped so a transient Firebase failure (offline, timeout) just leaves
    // the room unresolved instead of crashing the whole app — an uncaught
    // exception here would otherwise propagate out of this LaunchedEffect.
    suspend fun reload() {
        isLoading = true
        room = runCatching { repo.getRoom(roomId) }.getOrNull()
        isLoading = false
    }
    LaunchedEffect(roomId) { reload() }
    LaunchedEffect(roomId, myUid) {
        // Real, unique-per-viewer room view — see RoomRepository.incrementView.
        if (myUid != null) runCatching { repo.incrementView(roomId, myUid) }
    }

    Column(Modifier.fillMaxSize()) {
        // "طور التحميل الهيكلي" — shimmer placeholder shaped like the real
        // header while the initial Firebase read is in flight, instead of a
        // blank gap above the feed.
        if (isLoading) {
            RoomHeaderSkeleton()
        }
        room?.let { r ->
            val isOwner = myUid != null && myUid == r.ownerId

            // Cover banner — purely visual, shown above everything else
            // (including the live stream) when the owner has set one.
            if (r.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = r.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }

            // Live stream — owner-only to set (see updateLiveStream's doc),
            // but visible to everyone once set, right above the room info.
            if (r.liveStreamUrl.isNotBlank()) {
                LiveStreamPlayer(
                    url = r.liveStreamUrl,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }

            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = {
                        Text(if (r.isPublic) stringResource(R.string.room_public) else stringResource(R.string.room_private))
                    })
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(RoomCategory.icon(r.category), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(RoomCategory.label(r.category)) }
                    )
                    if (r.roomType == RoomType.TV_CHANNEL) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.tv_channel_badge)) }
                        )
                    }
                    if (isOwner) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showStreamEditor = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_live_stream))
                        }
                    }
                }
                if (r.bio.isNotBlank()) Text(r.bio, style = MaterialTheme.typography.bodyMedium)
                if (r.socialLinks.isNotEmpty()) {
                    Text(r.socialLinks.values.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
                if (r.phone.isNotBlank()) Text(r.phone, style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.room_members_count, r.memberCount.toString()), style = MaterialTheme.typography.labelSmall)
                Text(
                    stringResource(R.string.profile_views_label) + ": " + com.yeex.dlof.util.ViewMilestones.formatCount(r.viewCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // "قوانين الغرفة" — owner-authored community guidelines.
                // Shown to members when set; owners also get an edit affordance
                // even when empty, to invite setting them.
                if (r.rules.isNotBlank() || isOwner) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.room_rules_label), style = MaterialTheme.typography.labelLarge)
                        if (isOwner) {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showRulesEditor = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.room_rules_edit))
                            }
                        }
                    }
                    if (r.rules.isNotBlank()) {
                        Text(r.rules, style = MaterialTheme.typography.bodySmall)
                    } else if (isOwner) {
                        Text(
                            stringResource(R.string.room_rules_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()

            if (showStreamEditor) {
                LiveStreamEditorDialog(
                    currentUrl = r.liveStreamUrl,
                    onDismiss = { showStreamEditor = false },
                    onSave = { newUrl ->
                        scope.launch {
                            runCatching { repo.updateLiveStream(roomId, newUrl) }
                            showStreamEditor = false
                            reload()
                        }
                    }
                )
            }

            if (showRulesEditor) {
                RulesEditorDialog(
                    currentRules = r.rules,
                    onDismiss = { showRulesEditor = false },
                    onSave = { newRules ->
                        scope.launch {
                            runCatching { repo.updateRules(roomId, newRules) }
                            showRulesEditor = false
                            reload()
                        }
                    }
                )
            }
        }

        // Reuses the same swipeable square-card feed, scoped to this room.
        // Comments open in-place inside FeedScreen's own ModalBottomSheet
        // now, so only profile-browsing and repost still bubble up here.
        FeedScreen(
            roomId = roomId,
            onOpenProfile = onOpenProfile,
            onOpenTopic = onOpenTopic,
            onRepost = onRepost
        )
    }
}

/** Owner-only dialog to set/clear [Room.liveStreamUrl] — see [RoomScreen]. */
@Composable
private fun LiveStreamEditorDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_live_stream)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.live_stream_url_label)) },
                placeholder = { Text(stringResource(R.string.live_stream_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(url.trim()) }) {
                Text(stringResource(R.string.live_stream_save))
            }
        },
        dismissButton = {
            Row {
                if (currentUrl.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(R.string.live_stream_remove))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** Owner-only dialog to set/clear [Room.rules] — see [RoomScreen]. */
@Composable
private fun RulesEditorDialog(
    currentRules: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var rules by remember { mutableStateOf(currentRules) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_rules_label)) },
        text = {
            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                placeholder = { Text(stringResource(R.string.room_rules_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(rules.trim()) }) {
                Text(stringResource(R.string.live_stream_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Shimmer placeholder shaped like [RoomScreen]'s header (cover banner, title
 * + badge chips, bio line, member-count line) — shown while [RoomScreen]'s
 * initial Firebase read is in flight. FeedScreen underneath has its own
 * skeleton ([com.yeex.dlof.ui.components.ParagraphSkeleton]), so this only
 * covers the room-info block above it.
 */
@Composable
private fun RoomHeaderSkeleton() {
    Column {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ShimmerBox(modifier = Modifier.width(140.dp).height(22.dp))
                Spacer(Modifier.width(8.dp))
                ShimmerBox(modifier = Modifier.width(60.dp).height(24.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                Spacer(Modifier.width(6.dp))
                ShimmerBox(modifier = Modifier.width(90.dp).height(24.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
            }
            Spacer(Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f).height(11.dp))
        }
        HorizontalDivider()
    }
}
