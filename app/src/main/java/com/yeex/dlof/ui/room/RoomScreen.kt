package com.yeex.dlof.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.ui.components.LiveStreamPlayer
import com.yeex.dlof.ui.feed.FeedScreen
import com.yeex.dlof.util.RoomType
import kotlinx.coroutines.launch

@Composable
fun RoomScreen(
    roomId: String,
    repo: RoomRepository = RoomRepository(),
    authRepo: AuthRepository = AuthRepository(),
    onOpenProfile: (String) -> Unit = {},
    onRepost: (String) -> Unit
) {
    var room by remember { mutableStateOf<Room?>(null) }
    var showStreamEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()

    // Wrapped so a transient Firebase failure (offline, timeout) just leaves
    // the room unresolved instead of crashing the whole app — an uncaught
    // exception here would otherwise propagate out of this LaunchedEffect.
    suspend fun reload() {
        room = runCatching { repo.getRoom(roomId) }.getOrNull()
    }
    LaunchedEffect(roomId) { reload() }

    Column(Modifier.fillMaxSize()) {
        room?.let { r ->
            val isOwner = myUid != null && myUid == r.ownerId

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
                Text("${r.memberCount} members", style = MaterialTheme.typography.labelSmall)
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
        }

        // Reuses the same swipeable square-card feed, scoped to this room.
        // Comments open in-place inside FeedScreen's own ModalBottomSheet
        // now, so only profile-browsing and repost still bubble up here.
        FeedScreen(
            roomId = roomId,
            onOpenProfile = onOpenProfile,
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
