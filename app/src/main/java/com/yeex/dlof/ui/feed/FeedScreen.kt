package com.yeex.dlof.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.comments.CommentsSheet
import com.yeex.dlof.ui.components.ParagraphCard
import com.yeex.dlof.ui.components.ParagraphSkeleton
import com.yeex.dlof.ui.components.YeexTopBar
import com.yeex.dlof.ui.create.CreateParagraphScreen
import com.yeex.dlof.ui.theme.YeexAccent

/**
 * Full-screen, edge-to-edge, TikTok-style feed: each [ParagraphCard] fills
 * the entire available screen (no Scaffold padding, no card margins), and
 * swiping left/right (HorizontalPager) moves between paragraphs — same
 * gesture as the product spec, just an immersive full-bleed presentation
 * instead of a padded square card.
 *
 * Publishing and comments both happen in-place as a [ModalBottomSheet]
 * pop-up (see [CreateParagraphScreen] / [CommentsSheet] hosts below) so the
 * feed stays mounted underneath instead of navigating to a separate screen.
 * Tapping an author (see [ParagraphCard.onOpenProfile]) still navigates —
 * via [onOpenProfile] — since browsing someone else's account is a real
 * screen, not a transient pop-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    roomId: String? = null,
    viewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FeedViewModelFactory(roomId)
    ),
    onOpenProfile: (String) -> Unit = {},
    onRepost: (String) -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onOpenRooms: (() -> Unit)? = null
) {
    val paragraphs by viewModel.paragraphs.collectAsState()
    val reactions by viewModel.reactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    // Which paragraph's comments are open, if any — replaces navigating to a
    // separate CommentsScreen with an in-place ModalBottomSheet, same
    // treatment as publishing above.
    var commentsParagraphId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            // First Firebase snapshot hasn't arrived yet — skeleton, not the empty state.
            isLoading -> {
                ParagraphSkeleton(modifier = Modifier.fillMaxSize().padding(16.dp))
            }
            paragraphs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.paragraph_expires), color = Color.White)
                }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { paragraphs.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = paragraphs[page]
                    val myReaction = reactions[item.id]
                    ParagraphCard(
                        paragraph = item,
                        hasLiked = myReaction == com.yeex.dlof.data.repository.Reaction.LIKE,
                        hasDisliked = myReaction == com.yeex.dlof.data.repository.Reaction.DISLIKE,
                        onLike = { viewModel.like(item.id) },
                        onDislike = { viewModel.dislike(item.id) },
                        onComment = { commentsParagraphId = item.id },
                        onRepost = { onRepost(item.id) },
                        onOpenProfile = onOpenProfile,
                        // Only the page the pager has actually settled on
                        // should autoplay its video — otherwise the page
                        // mid-swipe-in plays too, and two videos' audio
                        // overlaps for the length of the swipe gesture.
                        isActive = page == pagerState.currentPage
                    )
                }
            }
        }

        // Floating top bar instead of a Scaffold TopAppBar, so it sits over
        // the media rather than pushing it down. The three-way segmented
        // control (لك / متابعين / حاويات), the "yeex" wordmark, and the
        // rooms/search shortcuts only make sense on the global feed — a
        // specific room's own feed (roomId != null) is already scoped, and
        // shown beneath that room's own header, so it keeps just the plain
        // title.
        YeexTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            showWordmark = roomId == null,
            onOpenRooms = if (roomId == null) onOpenRooms else null,
            onOpenSearch = if (roomId == null) onOpenSearch else null,
            center = {
                if (roomId == null) {
                    FeedTabRow(selected = selectedTab, onSelect = { viewModel.selectTab(it) })
                } else {
                    Text(
                        stringResource(R.string.feed_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )

        // Floating create button, positioned like a compact TikTok-style
        // action, above the bottom nav bar.
        FloatingActionButton(
            onClick = { showCreateSheet = true },
            containerColor = Color.White,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .clip(CircleShape)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_paragraph))
        }
    }

    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }, sheetState = sheetState) {
            CreateParagraphScreen(
                roomId = roomId,
                onPublished = { showCreateSheet = false }
            )
        }
    }

    val openCommentsId = commentsParagraphId
    if (openCommentsId != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { commentsParagraphId = null }, sheetState = sheetState) {
            CommentsSheet(
                paragraphId = openCommentsId,
                onOpenProfile = { uid ->
                    commentsParagraphId = null
                    onOpenProfile(uid)
                }
            )
        }
    }
}

/** Segmented "لك / متابعين / حاويات" control, composed right-to-left so
 * "لك" (the default, active-by-default tab) sits at the reading start —
 * i.e. the right edge in RTL — matching the reference design. */
@Composable
private fun FeedTabRow(selected: FeedTab, onSelect: (FeedTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        FeedTabLabel(stringResource(R.string.feed_tab_for_you), selected == FeedTab.FOR_YOU) { onSelect(FeedTab.FOR_YOU) }
        FeedTabLabel(stringResource(R.string.feed_tab_following), selected == FeedTab.FOLLOWING) { onSelect(FeedTab.FOLLOWING) }
        FeedTabLabel(stringResource(R.string.feed_tab_containers), selected == FeedTab.CONTAINERS) { onSelect(FeedTab.CONTAINERS) }
    }
}

@Composable
private fun FeedTabLabel(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (isSelected) 20.dp else 0.dp)
                .height(2.5.dp)
                .background(YeexAccent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}

class FeedViewModelFactory(private val roomId: String?) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return FeedViewModel(roomId = roomId) as T
    }
}
