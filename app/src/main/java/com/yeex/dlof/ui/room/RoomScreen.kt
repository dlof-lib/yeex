package com.yeex.dlof.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.ui.feed.FeedScreen
import kotlinx.coroutines.launch

@Composable
fun RoomScreen(
    roomId: String,
    repo: RoomRepository = RoomRepository(),
    onOpenProfile: (String) -> Unit = {},
    onRepost: (String) -> Unit
) {
    var room by remember { mutableStateOf<Room?>(null) }
    // Wrapped so a transient Firebase failure (offline, timeout) just leaves
    // the room unresolved instead of crashing the whole app — an uncaught
    // exception here would otherwise propagate out of this LaunchedEffect.
    LaunchedEffect(roomId) { room = runCatching { repo.getRoom(roomId) }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        room?.let { r ->
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = {
                        Text(if (r.isPublic) stringResource(R.string.room_public) else stringResource(R.string.room_private))
                    })
                }
                if (r.bio.isNotBlank()) Text(r.bio, style = MaterialTheme.typography.bodyMedium)
                if (r.socialLinks.isNotEmpty()) {
                    Text(r.socialLinks.values.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
                if (r.phone.isNotBlank()) Text(r.phone, style = MaterialTheme.typography.labelSmall)
                Text("${r.memberCount} members", style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
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
