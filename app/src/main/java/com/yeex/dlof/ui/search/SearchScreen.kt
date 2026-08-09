package com.yeex.dlof.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Container
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.ContainerRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.ShimmerBox
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.util.FeedRanking
import com.yeex.dlof.util.RoomRanking
import com.yeex.dlof.util.RoomType
import com.yeex.dlof.util.SearchHistoryStore
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
 *     [UserRepository.searchByIdentifierPrefix], PLUS a display-name prefix
 *     search via [UserRepository.searchByDisplayNamePrefix] (merged and
 *     de-duplicated) so a typed name matches too — AND public room-name
 *     search via [RoomRepository.searchByName], shown as two filterable
 *     sections.
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
 *
 * Further polish on top of that base:
 *  - A local, on-device "عمليات بحث سابقة" (recent searches) row —
 *    [SearchHistoryStore] — shown in the idle state above the trending
 *    content, tap to re-run, per-item remove, and a clear-all action. Pure
 *    SharedPreferences, nothing sent anywhere.
 *  - The matched substring in each result's name/handle is bolded
 *    ([highlightedAnnotatedString]) so scanning a results list is faster
 *    than reading every row start-to-finish.
 *  - The loading state is a shimmer skeleton shaped like the real result
 *    rows ([SearchResultsSkeleton]) instead of a plain centered spinner,
 *    matching the shimmer treatment [com.yeex.dlof.ui.components.ParagraphSkeleton]
 *    already uses for the feed.
 *  - The error state now has a working "إعادة المحاولة" retry button
 *    instead of just an icon + message with no way to recover without
 *    retyping the query.
 *  - The keyboard's IME action is "Search" and submits/dismisses the
 *    keyboard directly instead of only offering a generic "done".
 */
@Composable
fun SearchScreen(
    containerRepo: ContainerRepository = ContainerRepository(),
    userRepo: UserRepository = UserRepository(),
    roomRepo: RoomRepository = RoomRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
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
    var trendingRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var trendingParagraphs by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    var searchError by remember { mutableStateOf(false) }
    // Bumped by the error state's retry button to force the search
    // LaunchedEffect below to re-run even when `query` itself hasn't
    // changed (a plain `query` key alone wouldn't fire again for an
    // identical string).
    var retryTick by remember { mutableStateOf(0) }
    // Local "عمليات بحث سابقة" list — see [SearchHistoryStore]. Loaded once
    // and kept in sync with what's persisted as searches happen / entries
    // get removed, so the idle-state row updates immediately either way.
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        searchHistory = SearchHistoryStore.recent(context)
    }

    // "قد تعرفهم" + "الرائج الآن" only need fetching once — both are shown
    // solely in the idle (query-blank) state below, as one-shot snapshots
    // rather than live subscriptions (no need to keep a listener open just
    // for a top-5 list that's cheap to refresh next time the tab opens).
    LaunchedEffect(Unit) {
        val uid = authRepo.currentUid()
        if (uid != null) {
            suggestedAccounts = runCatching { userRepo.suggestedAccounts(uid) }.getOrDefault(emptyList())
        }
        val now = System.currentTimeMillis()
        trendingRooms = runCatching { RoomRanking.rankTrending(roomRepo.listPublicRooms(), now).take(8) }
            .getOrDefault(emptyList())
        trendingParagraphs = runCatching { FeedRanking.topTrending(paragraphRepo.getActiveParagraphs(), now, limit = 6) }
            .getOrDefault(emptyList())
    }

    // Debounced, real-time search: waits for a short pause in typing instead
    // of firing a query on every keystroke, then runs once per settled
    // query. Also keyed on retryTick so the error state's retry button can
    // force a re-run of the exact same query.
    LaunchedEffect(query, retryTick) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            isLoading = false
            hasSearched = false
            searchError = false
            containerResults = emptyList()
            userResults = emptyList()
            roomResults = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        searchError = false
        delay(350)
        // Any Firebase call below can throw (offline, timeout, transient
        // permission/deserialization errors) — this runs inside a
        // LaunchedEffect coroutine, so an uncaught exception here would
        // propagate up and crash the whole app instead of just failing the
        // search. Catch it and show an inline error state instead.
        runCatching {
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
                //
                // Two separate prefix queries are merged here: identifier
                // (@handle) AND display name, so typing someone's actual
                // name finds them too, not only their exact handle prefix —
                // previously only the identifier query ran, so a name search
                // silently returned nothing.
                val byIdentifier = userRepo.searchByIdentifierPrefix(trimmed)
                val byDisplayName = userRepo.searchByDisplayNamePrefix(trimmed)
                val mergedUsers = (byIdentifier + byDisplayName).distinctBy { it.uid }
                userResults = SearchRanking.rankUsers(trimmed, mergedUsers)
                roomResults = SearchRanking.rankRooms(trimmed, roomRepo.searchByName(trimmed))
            }
        }.onFailure {
            searchError = true
            containerResults = emptyList()
            userResults = emptyList()
            roomResults = emptyList()
        }.onSuccess {
            // Only recorded once the search actually resolved (not on
            // failure) — a query that only ever errored out isn't a
            // meaningful "recent search" to resurface later.
            searchHistory = SearchHistoryStore.record(context, trimmed)
        }
        isLoading = false
        hasSearched = true
    }

    val showUsers = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.USERS)
    val showRooms = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.ROOMS)
    val noResults = hasSearched && !isLoading && !searchError && containerResults.isEmpty() &&
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
                                onSearchSubmit = { keyboardController?.hide() },
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
                    trendingRooms = trendingRooms,
                    trendingParagraphs = trendingParagraphs,
                    searchHistory = searchHistory,
                    onOpenUser = onOpenUser,
                    onOpenRoom = onOpenRoom,
                    onSelectHistory = { picked -> query = picked },
                    onRemoveHistory = { removed ->
                        searchHistory = SearchHistoryStore.remove(context, removed)
                    },
                    onClearHistory = {
                        SearchHistoryStore.clear(context)
                        searchHistory = emptyList()
                    }
                )
                isLoading -> SearchResultsSkeleton()
                searchError -> SearchErrorState(onRetry = { retryTick++ })
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
                            UserResultCard(u, query = query, onClick = { onOpenUser(u) })
                        }
                    }

                    if (showRooms && roomResults.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.search_rooms_section))
                        }
                        items(roomResults, key = { it.id }) { r ->
                            RoomResultCard(r, query = query, onClick = { onOpenRoom(r) })
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
private fun BasicSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = Brush.verticalGradient(listOf(YeexAccent, YeexAccent)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
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

/**
 * Shimmer placeholder shaped like the real result rows ([UserResultCard] /
 * [RoomResultCard]) — shown while a search is in flight instead of a plain
 * centered spinner, so the results area doesn't "jump" from a
 * centered-blank state into a left-aligned list once results land.
 */
@Composable
private fun SearchResultsSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        repeat(6) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(44.dp), shape = CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.28f).height(11.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchIdleState(
    suggestedAccounts: List<User>,
    trendingRooms: List<Room>,
    trendingParagraphs: List<Paragraph>,
    searchHistory: List<String>,
    onOpenUser: (User) -> Unit,
    onOpenRoom: (Room) -> Unit,
    onSelectHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (suggestedAccounts.isEmpty() && trendingRooms.isEmpty() && trendingParagraphs.isEmpty() && searchHistory.isEmpty()) {
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

    // "الرائج الآن" (trending rooms + trending paragraphs, both ranked by
    // FeedRanking/RoomRanking's hot-score math — see their doc comments) and
    // a friend-of-a-friend suggestion list (see suggestedAccounts) fill what
    // would otherwise be a blank idle state with something immediately
    // useful, the same way most production search tabs surface discovery
    // content before the person types anything.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_recent_searches),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.clear_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = YeexAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClearHistory
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(searchHistory, key = { it }) { pastQuery ->
                        RecentSearchChip(
                            query = pastQuery,
                            onClick = { onSelectHistory(pastQuery) },
                            onRemove = { onRemoveHistory(pastQuery) }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (trendingRooms.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.search_trending_rooms),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(trendingRooms, key = { it.id }) { r ->
                        TrendingRoomChip(r, onClick = { onOpenRoom(r) })
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (trendingParagraphs.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.search_trending_posts),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            items(trendingParagraphs, key = { it.id }) { p ->
                TrendingParagraphRow(
                    p,
                    onClick = { onOpenUser(User(uid = p.authorId, identifier = p.authorIdentifier, verified = p.authorVerified)) }
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }

        if (suggestedAccounts.isNotEmpty()) {
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
}

/** One chip in the "عمليات بحث سابقة" row: tapping the label re-runs that
 * search, tapping the trailing × removes just that entry — two separate
 * click targets inside the same [Surface] rather than one click doing
 * double duty, so removing a stale entry doesn't accidentally re-search it
 * first. */
@Composable
private fun RecentSearchChip(query: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp)
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                query,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove_recent_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** One card in the "الرائج الآن" horizontal room row — see [SearchIdleState]. */
@Composable
private fun TrendingRoomChip(room: Room, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(160.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                room.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${room.memberCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (room.roomType == RoomType.TV_CHANNEL) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tv_channel_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = YeexCrimson
                )
            }
        }
    }
}

/** One row in the "منشورات رائجة" trending-paragraphs list — see [SearchIdleState]. */
@Composable
private fun TrendingParagraphRow(p: Paragraph, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${p.authorIdentifier}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (p.authorVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexCrimson, modifier = Modifier.size(13.dp))
                    }
                }
                if (p.text.isNotBlank()) {
                    Text(
                        p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "❤ ${p.likeCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown when the search request itself failed (offline/timeout/etc.) — distinct from a plain "no results" so the user knows to retry rather than reword their query. Now has a working retry action instead of leaving the only recovery path as retyping the query. */
@Composable
private fun SearchErrorState(onRetry: () -> Unit) {
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
        Text(stringResource(R.string.search_failed), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
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
 *
 * When [query] is non-blank, the matched portion of the display name /
 * identifier is bolded ([highlightedAnnotatedString]) so scanning a list of
 * results is faster than reading each row start-to-finish.
 */
@Composable
private fun UserResultCard(user: User, query: String = "", onClick: () -> Unit) {
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
                        highlightedAnnotatedString(user.displayName.ifBlank { user.identifier }, query),
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
                    highlightedAnnotatedString("@${user.identifier}", query),
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

/** Same visual language as [UserResultCard] but for public/private rooms —
 * see [UserResultCard] for the [query] highlighting behavior. */
@Composable
private fun RoomResultCard(room: Room, query: String = "", onClick: () -> Unit) {
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
                    highlightedAnnotatedString(room.name, query),
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

/** Bolds+tints the first case-insensitive occurrence of [query] inside
 * [text] (brand accent color), so a matched result's name/handle reads
 * as "why this matched" at a glance instead of requiring the person to
 * re-read the whole row against what they typed. Falls back to plain text
 * when [query] is blank or doesn't actually occur in [text] (e.g. a
 * display-name match whose identifier doesn't contain the query). */
@Composable
private fun highlightedAnnotatedString(text: String, query: String): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return AnnotatedString(text)
    val start = text.indexOf(trimmedQuery, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    val end = start + trimmedQuery.length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = YeexAccent, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}
