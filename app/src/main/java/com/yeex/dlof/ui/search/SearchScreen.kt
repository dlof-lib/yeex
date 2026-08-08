package com.yeex.dlof.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Container
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.ContainerRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.util.SearchRanking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchFilter { ALL, USERS, ROOMS }

/**
 * Professional, real-time search screen.
 *
 * Priority order, per spec ("للبحث عن حاوية تخصيص اولا اكتب @container.name[].me"):
 *  1. "@container.<name>[].me" → container lookup ([ContainerRepository]).
 *  2. Anything else → identifier search (accounts, "@handle" or bare) via
 *     [UserRepository.searchByIdentifierPrefix] AND public room-name search
 *     via [RoomRepository.searchByName], shown as two filterable sections.
 *
 * Search now runs as-you-type (debounced) instead of requiring a separate
 * "بحث" button tap, with a filter row (الكل / الحسابات / الغرف), a loading
 * state, a clear button, and distinct empty/idle states — closer to a
 * production search experience than the previous plain text-field + list.
 *
 * Results are re-ranked client-side by [SearchRanking] (exact/prefix match,
 * verified status, popularity) instead of shown in Firebase's raw
 * lexicographic query order. The idle state (before typing anything) also
 * surfaces a "قد تعرفهم" (people you may know) row from
 * [UserRepository.suggestedAccounts] — a friend-of-a-friend suggestion over
 * the tek graph — so the search tab isn't a blank page until you type.
 */
@Composable
fun SearchScreen(
    containerRepo: ContainerRepository = ContainerRepository(),
    userRepo: UserRepository = UserRepository(),
    roomRepo: RoomRepository = RoomRepository(),
    authRepo: AuthRepository = AuthRepository(),
    onOpenContainer: (Container) -> Unit,
    onOpenUser: (User) -> Unit = {},
    onOpenRoom: (Room) -> Unit = {},
    onOpenRooms: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var containerResults by remember { mutableStateOf<List<Container>>(emptyList()) }
    var userResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var roomResults by remember { mutableStateOf<List<Room>>(emptyList()) }
    var isContainerQuery by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    var suggestedAccounts by remember { mutableStateOf<List<User>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // "قد تعرفهم" only makes sense signed-in and only needs fetching once —
    // it's shown solely in the idle (query-blank) state below.
    LaunchedEffect(Unit) {
        val uid = authRepo.currentUid() ?: return@LaunchedEffect
        suggestedAccounts = runCatching { userRepo.suggestedAccounts(uid) }.getOrDefault(emptyList())
    }

    // Debounced, real-time search: waits for a short pause in typing instead
    // of firing a query on every keystroke, then runs once per settled query.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            isLoading = false
            hasSearched = false
            containerResults = emptyList()
            userResults = emptyList()
            roomResults = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        delay(350)
        val containerName = containerRepo.parseContainerQuery(trimmed)
        isContainerQuery = containerName != null
        if (containerName != null) {
            containerResults = containerRepo.findByName(containerName)
            userResults = emptyList()
            roomResults = emptyList()
        } else {
            containerResults = emptyList()
            // Firebase's prefix query returns plain lexicographic order —
            // SearchRanking re-sorts the (already-narrow) results by actual
            // relevance: exact/prefix match strength, verified status, and
            // popularity. See SearchRanking's doc comment.
            userResults = SearchRanking.rankUsers(trimmed, userRepo.searchByIdentifierPrefix(trimmed))
            roomResults = SearchRanking.rankRooms(trimmed, roomRepo.searchByName(trimmed))
        }
        isLoading = false
        hasSearched = true
    }

    val showUsers = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.USERS)
    val showRooms = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.ROOMS)
    val noResults = hasSearched && !isLoading && containerResults.isEmpty() &&
        (if (isContainerQuery) true else userResults.isEmpty() && roomResults.isEmpty())

    Column(Modifier.fillMaxSize()) {
        // ---- Search bar ----
        Surface(shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                modifier = Modifier.weight(1f)
                            )
                            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = onOpenRooms, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.browse_rooms))
                    }
                }

                if (!isContainerQuery) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill(
                            label = stringResource(R.string.search_tab_all),
                            selected = filter == SearchFilter.ALL,
                            onClick = { filter = SearchFilter.ALL }
                        )
                        FilterPill(
                            label = stringResource(R.string.search_tab_users),
                            selected = filter == SearchFilter.USERS,
                            onClick = { filter = SearchFilter.USERS }
                        )
                        FilterPill(
                            label = stringResource(R.string.search_tab_rooms),
                            selected = filter == SearchFilter.ROOMS,
                            onClick = { filter = SearchFilter.ROOMS }
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> SearchIdleState(
                    suggestedAccounts = suggestedAccounts,
                    onOpenUser = onOpenUser
                )
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                noResults -> SearchEmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(containerResults) { c ->
                        ContainerResultCard(container = c, onClick = { onOpenContainer(c) })
                    }

                    if (showUsers && userResults.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.search_users_section))
                        }
                        items(userResults, key = { it.uid }) { u ->
                            UserResultCard(u, onClick = { onOpenUser(u) })
                        }
                    }

                    if (showRooms && roomResults.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.search_rooms_section))
                        }
                        items(roomResults, key = { it.id }) { r ->
                            RoomResultCard(r, onClick = { onOpenRoom(r) })
                        }
                    }

                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

/** A borderless, single-line text field styled to sit inline in the search pill. */
@Composable
private fun BasicSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = Brush.verticalGradient(listOf(YeexAccent, YeexAccent)),
        modifier = modifier.padding(vertical = 10.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.container_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            inner()
        }
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) YeexAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun SearchIdleState(
    suggestedAccounts: List<User>,
    onOpenUser: (User) -> Unit
) {
    if (suggestedAccounts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.TravelExplore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.search_idle_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.search_idle_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    // A friend-of-a-friend suggestion list (see RecommendationRanking) fills
    // what would otherwise be a blank idle state with something immediately
    // useful, the same way most production search tabs surface suggestions
    // before the person types anything.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.search_suggested_accounts),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(suggestedAccounts, key = { it.uid }) { u ->
            UserResultCard(u, onClick = { onOpenUser(u) })
        }
    }
}

@Composable
private fun SearchEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ContainerResultCard(container: Container, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(YeexNavy.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = YeexNavy, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${stringResource(R.string.container_label)}: ${container.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${container.roomIds.size} rooms · ${container.memberIds.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A single "browse other accounts" row: avatar, display name + verified
 * badge, @identifier, and Teking/Teker counts, all inside a tappable card —
 * reads like a real account preview instead of a bare-text lookup.
 */
@Composable
private fun UserResultCard(user: User, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(iconBase64 = user.profileIconUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName.ifBlank { user.identifier },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = stringResource(R.string.verified_badge),
                            tint = YeexCrimson,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    "@${user.identifier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.search_user_stats, user.tekingCount, user.tekerCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Same visual language as [UserResultCard] but for public/private rooms. */
@Composable
private fun RoomResultCard(room: Room, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(YeexAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (room.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = YeexAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.room_members_count, room.memberCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
