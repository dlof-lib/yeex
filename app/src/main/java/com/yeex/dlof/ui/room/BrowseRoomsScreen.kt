package com.yeex.dlof.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.ui.components.ShimmerBox
import com.yeex.dlof.util.RoomCategory
import com.yeex.dlof.util.RoomRanking
import kotlinx.coroutines.launch

private enum class RoomsTab { EXPLORE, MINE }

/**
 * "تصفح الغرف" — discover public rooms (or the ones the current user
 * already belongs to), filter by name/interest, join with one tap, and jump
 * into [CreateRoomScreen] via the FAB. Complements [RoomScreen], which shows
 * a single room's feed once the user has picked one here.
 */
@Composable
fun BrowseRoomsScreen(
    authRepo: AuthRepository = AuthRepository(),
    repo: RoomRepository = RoomRepository(),
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit
) {
    val uid = authRepo.currentUid()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(RoomsTab.EXPLORE) }
    var query by remember { mutableStateOf("") }
    // null = "الكل" (all categories) — see the filter-chip row below.
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var allPublicRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var myRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var myRoomIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var joiningRoomId by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        isLoading = true
        // Wrapped so a transient Firebase failure (offline, timeout) leaves
        // the previous results in place instead of crashing the whole app —
        // an uncaught exception here would otherwise propagate out of the
        // LaunchedEffect coroutine below.
        runCatching {
            // Explore is ranked by RoomRanking's trending score (size + a
            // freshness boost for brand-new rooms) instead of raw Firebase
            // return order — see RoomRanking's doc comment.
            allPublicRooms = RoomRanking.rankTrending(repo.listPublicRooms())
            if (uid != null) {
                myRooms = repo.listMyRooms(uid)
                myRoomIds = myRooms.map { it.id }.toSet()
            }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    val source = if (tab == RoomsTab.MINE) myRooms else allPublicRooms
    val filtered = remember(source, query, selectedCategory) {
        source
            .filter { r -> selectedCategory == null || r.category == selectedCategory }
            .filter { r ->
                query.isBlank() ||
                    r.name.contains(query, ignoreCase = true) ||
                    r.interests.any { it.contains(query, ignoreCase = true) }
            }
    }
    // Top 3 of the (already trend-ranked) Explore list get a "trending" badge.
    val trendingIds = remember(filtered, tab) {
        if (tab == RoomsTab.EXPLORE) filtered.take(3).map { it.id }.toSet() else emptySet()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoom) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_room))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.browse_rooms), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_rooms_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // "فئات الغرف" — topical filter chips; "الكل" (null) clears it.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.room_category_all)) }
                    )
                }
                items(RoomCategory.ALL) { c ->
                    FilterChip(
                        selected = selectedCategory == c,
                        onClick = { selectedCategory = if (selectedCategory == c) null else c },
                        leadingIcon = { Icon(RoomCategory.icon(c), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(RoomCategory.label(c)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (uid != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tab == RoomsTab.EXPLORE,
                        onClick = { tab = RoomsTab.EXPLORE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.explore_rooms)) }
                    SegmentedButton(
                        selected = tab == RoomsTab.MINE,
                        onClick = { tab = RoomsTab.MINE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.my_rooms)) }
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                // "طور التحميل الهيكلي" — skeleton rows shaped like the real
                // RoomListItem instead of a plain centered spinner, same
                // reasoning as SearchScreen's SearchResultsSkeleton: the list
                // area doesn't jump from a centered-blank state into a
                // left-aligned list once results land.
                isLoading -> RoomListSkeleton()
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_rooms_found), style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { room ->
                        RoomListItem(
                            room = room,
                            isMember = room.id in myRoomIds,
                            isJoining = joiningRoomId == room.id,
                            isTrending = room.id in trendingIds,
                            canJoin = uid != null,
                            onOpen = { onOpenRoom(room.id) },
                            onJoin = {
                                if (uid == null) return@RoomListItem
                                scope.launch {
                                    joiningRoomId = room.id
                                    runCatching { repo.joinRoom(room.id, uid) }
                                    myRoomIds = myRoomIds + room.id
                                    joiningRoomId = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomListItem(
    room: Room,
    isMember: Boolean,
    isJoining: Boolean,
    isTrending: Boolean,
    canJoin: Boolean,
    onOpen: () -> Unit,
    onJoin: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (room.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = room.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(RoomCategory.icon(room.category), contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        room.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (room.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                        contentDescription = if (room.isPublic) stringResource(R.string.room_public) else stringResource(R.string.room_private),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isTrending) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = stringResource(R.string.room_trending_badge),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        RoomCategory.icon(room.category),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        RoomCategory.label(room.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (room.bio.isNotBlank()) {
                    Text(
                        room.bio,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.room_members_count, formatMemberCount(room.memberCount)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            if (canJoin) {
                if (isMember) {
                    OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.open_room)) }
                } else {
                    Button(onClick = onJoin, enabled = !isJoining) {
                        if (isJoining) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.join_room))
                        }
                    }
                }
            }
        }
    }
}

private fun formatMemberCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/** A column of [RoomListItemSkeleton] rows — see the `isLoading` branch above. */
@Composable
private fun RoomListSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { RoomListItemSkeleton() }
    }
}

/**
 * Shimmer placeholder shaped like [RoomListItem]: round avatar, name bar,
 * category bar, bio bar, member-count bar, and a button-shaped block on the
 * trailing edge — so the skeleton reads as "a room row is coming" rather
 * than a generic loading block.
 */
@Composable
private fun RoomListItemSkeleton() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(modifier = Modifier.size(46.dp), shape = CircleShape)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f).height(11.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(11.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.25f).height(10.dp))
            }

            Spacer(Modifier.width(8.dp))
            ShimmerBox(modifier = Modifier.width(72.dp).height(36.dp), shape = RoundedCornerShape(50))
        }
    }
}
