package com.yeex.dlof.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
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
        // Belt-and-suspenders: the whole subscription is wrapped in
        // runCatching on top of the Flow's own .catch{} operator. .catch{}
        // only protects exceptions raised while the flow is *emitting*; it
        // can't protect against an exception thrown synchronously while the
        // flow/query itself is being *built* (e.g. repo.observeTopics(...)
        // constructing a malformed Firebase Query before any collection
        // starts). Without this outer guard that class of failure would
        // propagate straight out of the LaunchedEffect coroutine, uncaught,
        // and crash the app the instant someone opens "المواضيع" — which is
        // exactly the bug this screen used to have. Now every failure path,
        // however early it happens, ends in isLoading = false + an empty
        // list instead of a crash.
        runCatching {
            repo.observeTopics(authorId = authorUid, roomId = roomId)
                .catch { isLoading = false }
                .collect { list ->
                    topics = list
                    isLoading = false
                    // NOTE: everything below runs inside collect{}, so the
                    // upstream .catch{} operator can NOT protect it — any
                    // exception here (network, Firebase permission-denied,
                    // a corrupt avatar/like lookup, etc.) would otherwise
                    // propagate uncaught and crash the app the moment the
                    // Topics list loads. Wrapping each side-effect in
                    // runCatching keeps one failed lookup from taking down
                    // the whole screen.
                    runCatching {
                        val missing = list.map { it.authorId }.distinct().filter { it !in avatars }
                        if (missing.isNotEmpty()) {
                            val fetched = missing.associateWith { uid -> runCatching { userRepo.getUser(uid)?.profileIconUrl }.getOrNull().orEmpty() }
                            avatars = avatars + fetched
                        }
                    }
                    if (myUid != null) {
                        runCatching {
                            val liked = mutableSetOf<String>()
                            for (t in list) {
                                if (runCatching { repo.getLikedByMe(t.id, myUid) }.getOrDefault(false)) liked.add(t.id)
                            }
                            likedByMe = liked
                        }
                    }
                }
        }.onFailure {
            // Query construction itself blew up (bad args, Firebase not
            // ready yet, etc.) — fail into the same safe empty state rather
            // than letting the exception escape the coroutine.
            isLoading = false
            topics = emptyList()
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
                // A shimmer-skeleton list (shaped like real TopicCards) instead
                // of a bare spinner — reads as "content is arriving" rather
                // than "the screen is frozen".
                isLoading -> Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    val shimmerInstance = rememberShimmer(ShimmerBounds.View)
                    repeat(4) { TopicSkeletonCard(shimmerInstance) }
                }
                topics.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.topics_empty))
                    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
                    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(120.dp))
                    Spacer(Modifier.height(8.dp))
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
                                        runCatching { repo.toggleLike(topic.id, myUid) }
                                            .onSuccess { nowLiked ->
                                                likedByMe = if (nowLiked) likedByMe + topic.id else likedByMe - topic.id
                                            }
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

/**
 * A skeleton row shaped like [TopicCard] (avatar + byline, title, body
 * lines) so the shimmering loading state doesn't jump/reflow once real
 * topics arrive — see compose-shimmer's `shimmer()` modifier.
 */
@Composable
private fun TopicSkeletonCard(shimmerInstance: com.valentinilk.shimmer.Shimmer) {
    val block = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .shimmer(shimmerInstance)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(block))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(96.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(block))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(0.65f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
    }
}
