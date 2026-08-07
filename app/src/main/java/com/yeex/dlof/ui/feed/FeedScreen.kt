package com.yeex.dlof.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.components.ParagraphCard
import com.yeex.dlof.ui.components.ParagraphSkeleton
import com.yeex.dlof.ui.create.CreateParagraphScreen

/**
 * Publishing a new paragraph now happens in-place as a [ModalBottomSheet]
 * pop-up (see [CreateParagraphScreen]'s host below) instead of navigating to
 * a separate full screen, per the "قسم النشر شاشة منبثقة" requirement — the
 * feed stays mounted underneath and simply refreshes via its live listener
 * once the sheet closes after a successful publish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    roomId: String? = null,
    viewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FeedViewModelFactory(roomId)
    ),
    onOpenComments: (String) -> Unit,
    onRepost: (String) -> Unit
) {
    val paragraphs by viewModel.paragraphs.collectAsState()
    val reactions by viewModel.reactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feed_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_paragraph))
            }
        }
    ) { padding ->
        when {
            // First Firebase snapshot hasn't arrived yet — skeleton, not the empty state.
            isLoading -> {
                ParagraphSkeleton(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                )
            }
            paragraphs.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(stringResource(R.string.paragraph_expires))
                }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { paragraphs.size })
                // Swiping left/right (horizontal pager) moves between paragraphs, one square card at a time.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                ) { page ->
                    val item = paragraphs[page]
                    val myReaction = reactions[item.id]
                    ParagraphCard(
                        paragraph = item,
                        hasLiked = myReaction == com.yeex.dlof.data.repository.Reaction.LIKE,
                        hasDisliked = myReaction == com.yeex.dlof.data.repository.Reaction.DISLIKE,
                        onLike = { viewModel.like(item.id) },
                        onDislike = { viewModel.dislike(item.id) },
                        onComment = { onOpenComments(item.id) },
                        onRepost = { onRepost(item.id) }
                    )
                }
            }
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
}

class FeedViewModelFactory(private val roomId: String?) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return FeedViewModel(roomId = roomId) as T
    }
}
