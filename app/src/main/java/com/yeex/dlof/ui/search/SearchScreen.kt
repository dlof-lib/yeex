package com.yeex.dlof.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Container
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicType
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.ContainerRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.TopicRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.ShimmerBox
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexBrandGradient
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.util.FeedRanking
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.RoomRanking
import com.yeex.dlof.util.RoomType
import com.yeex.dlof.util.SearchHistoryStore
import com.yeex.dlof.util.SearchRanking
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchFilter { ALL, USERS, ROOMS, PARAGRAPHS, TOPICS }

/**
 * Professional, real-time search screen.
 *
 * Priority order, per spec ("للبحث عن حاوية تخصيص اولا اكتب @container.name[].me"):
 *  1. "@container.<name>[].me" → container lookup ([ContainerRepository]).
 *  2. Anything else → identifier search (accounts, "@handle" or bare) via
 *     [UserRepository.searchByIdentifierPrefix], PLUS a display-name prefix
 *     search via [UserRepository.searchByDisplayNamePrefix] (merged and
 *     de-duplicated) so a typed name matches too — AND public room-name
 *     search via [RoomRepository.searchByName] — AND real text search over
 *     paragraphs ([ParagraphRepository.searchActiveByText]) and topics
 *     ([TopicRepository.searchByText]), including "#hashtag" queries, shown
 *     as four filterable sections instead of only accounts/rooms. The old
 *     "منشورات رائجة" idle-state row showed trending posts regardless of
 *     what was typed; typing a query now actually searches paragraph/topic
 *     text and hashtags, not just their authors' identifiers.
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
    topicRepo: TopicRepository = TopicRepository(),
    authRepo: AuthRepository = AuthRepository(),
    onOpenContainer: (Container) -> Unit,
    onOpenUser: (User) -> Unit = {},
    onOpenRoom: (Room) -> Unit = {},
    onOpenRooms: () -> Unit = {},
    onOpenTopic: (Topic) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var containerResults by remember { mutableStateOf<List<Container>>(emptyList()) }
    var userResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var roomResults by remember { mutableStateOf<List<Room>>(emptyList()) }
    var paragraphResults by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    var topicResults by remember { mutableStateOf<List<Topic>>(emptyList()) }
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
    // Controls the "⋮" overflow menu in the new top bar (see the top-bar
    // Row below) — kept separate from the search-field state above since it
    // opens/closes independently of typing or query results.
    var topBarMenuExpanded by remember { mutableStateOf(false) }
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
            paragraphResults = emptyList()
            topicResults = emptyList()
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
                paragraphResults = emptyList()
                topicResults = emptyList()
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
                // Real content search: paragraph text and topic
                // title/body/hashtags, not just their authors' handles —
                // see [ParagraphRepository.searchActiveByText] /
                // [TopicRepository.searchByText]'s doc comments for why
                // this filters in memory rather than querying a text index.
                paragraphResults = SearchRanking.rankParagraphs(trimmed, paragraphRepo.searchActiveByText(trimmed))
                topicResults = SearchRanking.rankTopics(trimmed, topicRepo.searchByText(trimmed))
            }
        }.onFailure {
            searchError = true
            containerResults = emptyList()
            userResults = emptyList()
            roomResults = emptyList()
            paragraphResults = emptyList()
            topicResults = emptyList()
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
    val showParagraphs = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.PARAGRAPHS)
    val showTopics = !isContainerQuery && (filter == SearchFilter.ALL || filter == SearchFilter.TOPICS)
    val noResults = hasSearched && !isLoading && !searchError && containerResults.isEmpty() &&
        (if (isContainerQuery) {
            true
        } else {
            userResults.isEmpty() && roomResults.isEmpty() && paragraphResults.isEmpty() && topicResults.isEmpty()
        })

    Column(Modifier.fillMaxSize()) {
        // ---- Header (top bar + search bar) ----
        // A soft frosted surface instead of a flat divider bar, with a
        // real brand-gradient hairline drawn underneath it (see the
        // gradient Box right after this Surface) so the header reads as
        // part of Yeex's purple → pink identity rather than a generic
        // Material app bar.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(YeexAccent.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ---- Top bar: brand mark + quick actions ----
                // Promoted out of the search-field row into its own row so
                // the screen opens with the same "yeex" identity as the
                // home feed's [YeexTopBar], with the rooms shortcut and a
                // new "⋮" overflow menu (search-history clear, room
                // browsing) sitting where a professional app bar keeps its
                // secondary actions instead of crowding the search pill.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = YeexBrandGradient,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.nav_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    SearchTopBarAction(
                        icon = Icons.Filled.Groups,
                        contentDescription = stringResource(R.string.browse_rooms),
                        onClick = onOpenRooms
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        SearchTopBarAction(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            onClick = { topBarMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = topBarMenuExpanded,
                            onDismissRequest = { topBarMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_search_history)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.History, contentDescription = null)
                                },
                                enabled = searchHistory.isNotEmpty(),
                                onClick = {
                                    SearchHistoryStore.clear(context)
                                    searchHistory = emptyList()
                                    topBarMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_rooms)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Groups, contentDescription = null)
                                },
                                onClick = {
                                    topBarMenuExpanded = false
                                    onOpenRooms()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ---- Search field ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The pill glows with a faint brand-gradient outline while
                    // there's an active query, giving quiet feedback that a
                    // search is "live" without needing extra chrome.
                    val fieldGlow by animateFloatAsState(
                        targetValue = if (query.isNotEmpty()) 1f else 0f,
                        label = "searchFieldGlow"
                    )
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    YeexAccent.copy(alpha = 0.15f + 0.45f * fieldGlow),
                                    YeexPink.copy(alpha = 0.10f + 0.35f * fieldGlow)
                                )
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = if (query.isNotEmpty()) YeexAccent else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isContainerQuery) {
                    Spacer(Modifier.height(10.dp))
                    // Scrollable, not a fixed Row: five pills (all/accounts/
                    // rooms/posts/topics) no longer reliably fit one phone
                    // width the way the original three did.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
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
                        FilterPill(
                            label = stringResource(R.string.search_tab_paragraphs),
                            selected = filter == SearchFilter.PARAGRAPHS,
                            onClick = { filter = SearchFilter.PARAGRAPHS }
                        )
                        FilterPill(
                            label = stringResource(R.string.search_tab_topics),
                            selected = filter == SearchFilter.TOPICS,
                            onClick = { filter = SearchFilter.TOPICS }
                        )
                    }
                }
            }
        }
        // Thin brand-gradient hairline under the header, in place of a flat
        // Divider, echoing the purple → pink identity used across the app.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(YeexBrandGradient)
        )

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

                    if (showParagraphs && paragraphResults.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.search_paragraphs_section))
                        }
                        items(paragraphResults, key = { it.id }) { p ->
                            ParagraphResultCard(
                                paragraph = p,
                                query = query,
                                onClick = {
                                    onOpenUser(User(uid = p.authorId, identifier = p.authorIdentifier, verified = p.authorVerified))
                                }
                            )
                        }
                    }

                    if (showTopics && topicResults.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.search_topics_section))
                        }
                        items(topicResults, key = { it.id }) { t ->
                            TopicResultCard(topic = t, query = query, onClick = { onOpenTopic(t) })
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

/** A pill-shaped filter tab. The selected state fills with the brand's
 * purple → pink gradient (matching [YeexBrandGradient] used for avatar
 * rings and primary actions elsewhere) instead of a flat accent color, so
 * the active filter reads as unmistakably "on brand" rather than generic
 * Material selection. */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) {
                    Modifier.background(YeexBrandGradient)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        )
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // rememberRipple() is now a hard compile error (not just
                // deprecated) as of the newer compose-bom -- androidx.compose
                // .material3.ripple.ripple() is its non-composable, drop-in
                // replacement (no remember{} needed, it's already stable).
                indication = androidx.compose.material3.ripple.ripple(),
                onClick = onClick
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(YeexBrandGradient)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
            StateIcon(Icons.Outlined.TravelExplore)
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        modifier = Modifier.width(160.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(YeexAccent.copy(alpha = 0.18f), YeexPink.copy(alpha = 0.18f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(com.yeex.dlof.util.RoomCategory.icon(room.category), contentDescription = null, modifier = Modifier.size(14.dp), tint = YeexAccent)
            }
            Spacer(Modifier.height(8.dp))
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
        StateIcon(Icons.Outlined.TravelExplore)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.search_failed), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = YeexAccent, contentColor = Color.White)
        ) {
            Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold)
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
        StateIcon(Icons.Outlined.SearchOff)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.titleMedium)
    }
}

/** Shared full-screen-state icon treatment (idle/error/empty): a large soft
 * brand-gradient disc behind the glyph instead of a bare gray icon floating
 * in white space, so these "nothing here yet" moments still feel branded. */
@Composable
private fun StateIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(YeexAccent.copy(alpha = 0.14f), YeexPink.copy(alpha = 0.06f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ContainerResultCard(container: Container, onClick: () -> Unit) {
    ResultCardShell(onClick = onClick) {
        GradientIconBadge(icon = Icons.Filled.Groups)
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
        ResultChevron()
    }
}

/** Shared "row card" chrome for every search result type (container/user/
 * room): a soft elevated surface with a faint outline instead of the old
 * flat `surfaceVariant` fill, plus a gentle press-down scale so tapping a
 * result feels responsive rather than static. */
@Composable
private fun ResultCardShell(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "resultCardPress")
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** A circular badge with a faint brand-gradient wash behind the icon —
 * used across all result-card leading icons so containers/rooms share one
 * consistent "on-brand" treatment instead of separate flat tint colors. */
@Composable
private fun GradientIconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(YeexAccent.copy(alpha = 0.16f), YeexPink.copy(alpha = 0.16f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(size * 0.5f))
    }
}

/** A small tonal circular icon button for the search screen's top bar
 * (rooms shortcut, "⋮" overflow) — a lighter-weight sibling of
 * [GradientIconBadge] sized for an app-bar action rather than a result
 * card's leading badge. */
@Composable
private fun SearchTopBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = YeexAccent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ResultChevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

/**
 * A square media/type thumbnail leading a paragraph or topic search
 * result — an actual decoded cover image for IMAGE paragraphs and topics
 * with a cover photo, the remote link-preview image for LINK topics, a
 * branded video/Moment placeholder (with a small play-badge for video) for
 * those types, and [fallbackIcon] only when there's genuinely nothing
 * visual to show (a plain TEXT paragraph/topic). Lets a paragraph/topic
 * result read as "what it actually looks like" — image, video, text, etc.
 * — instead of only "which section it's under".
 */
@Composable
private fun SearchResultThumbnail(
    bitmap: android.graphics.Bitmap?,
    remoteImageUrl: String? = null,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isVideo: Boolean = false,
    size: Dp = 52.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(YeexAccent.copy(alpha = 0.18f), YeexPink.copy(alpha = 0.18f)))
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            !remoteImageUrl.isNullOrBlank() -> AsyncImage(
                model = remoteImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Icon(fallbackIcon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(size * 0.42f))
        }
        // Small play-badge over a video cover (or the video placeholder
        // itself), so a video result reads as "video" at a glance even
        // when it has no decodable still frame to show — matching the
        // "▶" treatment [com.yeex.dlof.ui.profile.ProfileScreen]'s own
        // paragraph thumbnails use for the same case.
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
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
    ResultCardShell(onClick = onClick) {
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
        ResultChevron()
    }
}

/** Same visual language as [UserResultCard] but for public/private rooms —
 * see [UserResultCard] for the [query] highlighting behavior. */
@Composable
private fun RoomResultCard(room: Room, query: String = "", onClick: () -> Unit) {
    ResultCardShell(onClick = onClick) {
        GradientIconBadge(icon = if (room.isPublic) Icons.Filled.Public else Icons.Filled.Lock)
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
        ResultChevron()
    }
}

/**
 * A search result row for a matched paragraph (فقرة) — the matched snippet
 * of [Paragraph.text] (which is also where hashtags live), the author's
 * @identifier, and the like count, so a text/hashtag search result reads
 * as "why this matched" the same way [UserResultCard]/[RoomResultCard] do.
 * Tapping it opens the author's profile — paragraphs have no standalone
 * detail screen of their own (they're ephemeral, swiped through in the
 * feed), so this mirrors what [TrendingParagraphRow] already does.
 */
@Composable
private fun ParagraphResultCard(paragraph: Paragraph, query: String = "", onClick: () -> Unit) {
    // Same "only IMAGE decodes to a still frame" rule ParagraphThumbCard
    // uses in ProfileScreen — VIDEO paragraphs store raw MP4 bytes, which
    // BitmapFactory can't decode, so those fall through to the branded
    // video placeholder below instead of a broken image.
    val bitmap = remember(paragraph.id) {
        if (paragraph.mediaBase64.isNotEmpty() && paragraph.type != ParagraphType.VIDEO.name) {
            MediaBase64.decodeToBitmap(paragraph.mediaBase64)
        } else null
    }
    ResultCardShell(onClick = onClick) {
        SearchResultThumbnail(
            bitmap = bitmap,
            fallbackIcon = when (paragraph.type) {
                ParagraphType.VIDEO.name -> Icons.Filled.Videocam
                ParagraphType.MOMENT.name -> Icons.Filled.Timeline
                else -> Icons.Filled.Notes
            },
            isVideo = paragraph.type == ParagraphType.VIDEO.name
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${paragraph.authorIdentifier}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (paragraph.authorVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.verified_badge),
                        tint = YeexCrimson,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (paragraph.text.isNotBlank()) {
                Text(
                    highlightedAnnotatedString(paragraph.text, query),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "❤ ${paragraph.likeCount} · 💬 ${paragraph.commentCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ResultChevron()
    }
}

/** A search result row for a matched topic (موضوع) — see [ParagraphResultCard]
 * for the general shape. Unlike paragraphs, topics have their own permanent
 * detail screen, so this opens [onClick] straight into it. */
@Composable
private fun TopicResultCard(topic: Topic, query: String = "", onClick: () -> Unit) {
    val cover = remember(topic.id) {
        if (topic.imageBase64.isNotBlank()) MediaBase64.decodeToBitmap(topic.imageBase64) else null
    }
    val isLink = topic.type == TopicType.LINK.name
    ResultCardShell(onClick = onClick) {
        SearchResultThumbnail(
            bitmap = cover,
            remoteImageUrl = topic.link?.imageUrl,
            fallbackIcon = if (isLink) Icons.Filled.Link else Icons.Filled.Article
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    highlightedAnnotatedString(topic.title.ifBlank { topic.body }, query),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (topic.authorVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.verified_badge),
                        tint = YeexCrimson,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                "@${topic.authorIdentifier}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "❤ ${topic.likeCount} · 💬 ${topic.commentCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ResultChevron()
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
