package com.yeex.dlof.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.TopicRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.TopicCard
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.yeexBrandGradient
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * YEEX TOPICS | المواضيع — a permanent, reading/discussion-first list,
 * independent of the swiped 24h paragraph feed. [authorUid] scopes the list
 * to one profile's topics (used from ProfileScreen); [roomId] scopes it to a
 * room's topics; both null shows the global topics feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    authorUid: String? = null,
    roomId: String? = null,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: TopicRepository = TopicRepository(),
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onCreateTopic: () -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScopeCompat()
    val myUid = authRepo.currentUid()
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var avatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var likedByMe by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(authorUid, roomId) {
        repo.observeTopics(authorId = authorUid, roomId = roomId)
            .catch { isLoading = false }
            .collect { list ->
            topics = list
            isLoading = false
            val missing = list.map { it.authorId }.distinct().filter { it !in avatars }
            if (missing.isNotEmpty()) {
                val fetched = missing.associateWith { uid -> userRepo.getUser(uid)?.profileIconUrl.orEmpty() }
                avatars = avatars + fetched
            }
            if (myUid != null) {
                val liked = mutableSetOf<String>()
                for (t in list) {
                    if (repo.getLikedByMe(t.id, myUid)) liked.add(t.id)
                }
                likedByMe = liked
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTopic, containerColor = YeexAccent) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.topic_create_title))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBackIosNew, contentDescription = stringResource(R.string.cancel))
                }
                Text(
                    stringResource(R.string.topics_title),
                    style = MaterialTheme.typography.titleLarge.copy(brush = yeexBrandGradient()),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = YeexAccent)
                }
                topics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.topics_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(topics, key = { it.id }) { topic ->
                        TopicCard(
                            topic = topic,
                            authorIconBase64 = avatars[topic.authorId].orEmpty(),
                            hasLiked = topic.id in likedByMe,
                            onOpen = { onOpenTopic(topic.id) },
                            onLike = {
                                if (myUid != null) {
                                    scope.launch {
                                        val nowLiked = repo.toggleLike(topic.id, myUid)
                                        likedByMe = if (nowLiked) likedByMe + topic.id else likedByMe - topic.id
                                    }
                                }
                            },
                            onOpenProfile = onOpenProfile
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
