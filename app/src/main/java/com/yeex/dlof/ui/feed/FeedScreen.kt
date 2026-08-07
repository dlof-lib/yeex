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

@Composable
fun FeedScreen(
    roomId: String? = null,
    viewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FeedViewModelFactory(roomId)
    ),
    onCreateParagraph: () -> Unit,
    onOpenComments: (String) -> Unit,
    onRepost: (String) -> Unit
) {
    val paragraphs by viewModel.paragraphs.collectAsState()
    val reactions by viewModel.reactions.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feed_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateParagraph) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_paragraph))
            }
        }
    ) { padding ->
        if (paragraphs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(stringResource(R.string.paragraph_expires))
            }
            return@Scaffold
        }

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

class FeedViewModelFactory(private val roomId: String?) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return FeedViewModel(roomId = roomId) as T
    }
}
