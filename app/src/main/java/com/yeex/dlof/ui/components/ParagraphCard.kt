package com.yeex.dlof.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDislike
import com.yeex.dlof.ui.theme.YeexLike
import com.yeex.dlof.ui.theme.YeexLikeGlow
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.util.BackgroundTaskType
import com.yeex.dlof.util.DownloadUtil
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.PdfExportUtil
import com.yeex.dlof.util.TaskProgressManager
import com.yeex.dlof.util.TranslationUtil
import com.yeex.dlof.util.WatermarkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Renders a single paragraph as an immersive, edge-to-edge FULL-SCREEN page —
 * TikTok-style — the unit [com.yeex.dlof.ui.feed.FeedScreen]'s HorizontalPager
 * swipes between left/right. Video fills the entire device screen (zoomed to
 * fill, like a short-video app); images are shown at their real proportions
 * (letterboxed on black rather than cropped) so nothing is cut off. Actions live in a right-side vertical rail and author/caption sit above a
 * bottom scrim, both floating over the media.
 *
 * Tapping the author row (avatar + "@handle") calls [onOpenProfile] so the
 * feed can navigate to that account's profile and browse their paragraphs.
 */

/**
 * Top padding (applied after [androidx.compose.foundation.layout.statusBarsPadding])
 * for this card's own top-corner overlays (speed chip, overflow menu). Must
 * clear [com.yeex.dlof.ui.components.YeexTopBar]'s 52dp content height plus a
 * small gap, since that shared bar floats over the same corners — otherwise
 * these overlays collide with the wordmark/search/rooms icons living there.
 */
private val TopOverlayClearance = 64.dp

@Composable
fun ParagraphCard(
    paragraph: Paragraph,
    hasLiked: Boolean,
    hasDisliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    userRepo: UserRepository = UserRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
    // Whether this card is the page the pager has actually settled on — see
    // VideoPlayer's isActive doc. Only forwarded to VideoPlayer; irrelevant
    // for image/text paragraphs.
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watermarkLabel = stringResource(R.string.watermark_text)
    val savedMessage = stringResource(R.string.download_saved)
    val videoSavedMessage = stringResource(R.string.download_saved_video)
    val failedMessage = stringResource(R.string.download_failed)
    val translateFailedMessage = stringResource(R.string.translate_failed)
    val hasMedia = paragraph.mediaBase64.isNotEmpty()
    val isVideo = paragraph.type == ParagraphType.VIDEO.name
    // Only IMAGE paragraphs decode to a Bitmap. VIDEO paragraphs store raw
    // MP4 bytes, which BitmapFactory can't decode (see MediaBase64.decodeToBitmap) —
    // those are rendered with VideoPlayer below instead.
    val bitmap = remember(paragraph.id) {
        if (hasMedia && !isVideo) MediaBase64.decodeToBitmap(paragraph.mediaBase64) else null
    }
    val (captionText, hashtags) = remember(paragraph.text) { extractHashtags(paragraph.text) }

    // ---- Tap-to-pause (video only) + double-tap-to-like state ----
    var isPaused by remember(paragraph.id) { mutableStateOf(false) }
    var playbackSpeed by remember(paragraph.id) { mutableStateOf(1f) }
    var heartBurstVisible by remember { mutableStateOf(false) }
    var heartBurstTrigger by remember { mutableStateOf(0) }
    var heartBurstOffset by remember { mutableStateOf(Offset.Zero) }

    // ---- TikTok-style "end of video" loop flash: VideoPlayer calls onLoop
    // every time playback wraps back to frame 0, and this briefly shows a
    // centered replay glyph so looping reads as an intentional beat instead
    // of a silent jump-cut — then fades itself back out. ----
    var loopFlashTrigger by remember(paragraph.id) { mutableStateOf(0) }

    // ---- On-device caption translation state (see TranslationUtil) ----
    var translatedText by remember(paragraph.id) { mutableStateOf<String?>(null) }
    var isTranslating by remember(paragraph.id) { mutableStateOf(false) }

    // One view bump per paragraph, only once it's the page the user has
    // actually settled on (not every card mid-swipe-through).
    LaunchedEffect(paragraph.id, isActive) {
        if (isActive && paragraph.id.isNotBlank()) {
            runCatching { paragraphRepo.incrementView(paragraph.id) }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    val comingSoonMessage = stringResource(R.string.coming_soon)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---- Background layer: media fills the whole screen ----
        // A single tap pauses/resumes video (no-op for other types); a
        // double tap always shows the heart burst and likes the paragraph
        // (never unlikes — a second double tap is a no-op on the like
        // state itself, matching Instagram/TikTok). Attached here, on the
        // background layer specifically (not the outer root Box), so the
        // rail/top-row/caption children declared further down still get
        // first claim on any tap that lands on them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(paragraph.id, isVideo, hasLiked) {
                    detectTapGestures(
                        onTap = { if (isVideo) isPaused = !isPaused },
                        onDoubleTap = { offset ->
                            heartBurstOffset = offset
                            heartBurstTrigger++
                            heartBurstVisible = true
                            if (!hasLiked) onLike()
                        }
                    )
                }
        ) {
            when (paragraph.type) {
                ParagraphType.VIDEO.name -> {
                    if (hasMedia) {
                        VideoPlayer(
                            paragraphId = paragraph.id,
                            mediaBase64 = paragraph.mediaBase64,
                            modifier = Modifier.fillMaxSize(),
                            isActive = isActive,
                            isPaused = isPaused,
                            playbackSpeed = playbackSpeed,
                            onLoop = { loopFlashTrigger++ }
                        )
                    }
                }
                ParagraphType.IMAGE.name -> {
                    if (bitmap != null) {
                        // Fit (not Crop) so the image is shown at its real
                        // proportions instead of being cropped to fill the
                        // screen — any letterboxing just shows the black
                        // background behind it.
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ParagraphType.MOMENT.name -> {
                    // Leaves room for the top author row / expiry timer, the
                    // right-side action rail, and the bottom caption+view-count
                    // overlay drawn further down, instead of the timeline
                    // rendering underneath them.
                    MomentTimeline(
                        steps = paragraph.momentSteps,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 56.dp, bottom = 150.dp, start = 12.dp, end = 84.dp)
                    )
                }
                else -> {
                    // Text-only paragraphs get a subtle brand gradient instead of a blank void.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(YeexAccent.copy(alpha = 0.55f), Color.Black)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            paragraph.text,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 28.dp)
                        )
                    }
                }
            }
        }

        // ---- Pause indicator: shown centered while a video is manually paused ----
        if (isVideo && isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_resume), tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        // ---- End-of-video loop flash (video only) ----
        if (isVideo && loopFlashTrigger > 0) {
            key(loopFlashTrigger) {
                VideoLoopFlash(modifier = Modifier.align(Alignment.Center))
            }
        }

        // ---- Double-tap heart burst ----
        if (heartBurstVisible) {
            key(heartBurstTrigger) {
                DoubleTapHeartBurst(offsetPx = heartBurstOffset, onFinished = { heartBurstVisible = false })
            }
        }

        // ---- Bottom scrim for legibility over the media ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // ---- Right-side vertical action rail (TikTok-style) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ReactionButton(
                isActive = hasLiked,
                activeIcon = Icons.Filled.Favorite,
                inactiveIcon = Icons.Filled.FavoriteBorder,
                activeColor = YeexLike,
                glowColor = YeexLikeGlow,
                count = paragraph.likeCount,
                contentDescription = stringResource(R.string.action_like),
                onClick = onLike
            )
            ReactionButton(
                isActive = hasDisliked,
                activeIcon = Icons.Filled.ThumbDown,
                inactiveIcon = Icons.Outlined.ThumbDown,
                activeColor = YeexDislike,
                glowColor = YeexDislike,
                count = paragraph.dislikeCount,
                contentDescription = stringResource(R.string.action_dislike),
                onClick = onDislike
            )
            RailAction(
                icon = Icons.Filled.ChatBubble,
                tint = Color.White,
                count = paragraph.commentCount,
                contentDescription = stringResource(R.string.action_comment),
                onClick = onComment
            )
            if (captionText.isNotBlank()) {
                RailAction(
                    icon = Icons.Filled.Translate,
                    tint = if (translatedText != null) YeexAccent else Color.White,
                    count = null,
                    contentDescription = stringResource(R.string.action_translate),
                    loading = isTranslating,
                    onClick = {
                        if (translatedText != null) {
                            // Toggle back to the original caption instead of
                            // re-translating — a second tap always means
                            // "show me what I had before", not "translate again".
                            translatedText = null
                        } else if (!isTranslating) {
                            scope.launch {
                                isTranslating = true
                                val targetTag = TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
                                    ?: TranslateLanguage.ENGLISH
                                val result = TranslationUtil.translate(captionText, targetTag)
                                isTranslating = false
                                result.onSuccess { translated -> translatedText = translated }
                                    .onFailure { Toast.makeText(context, translateFailedMessage, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                )
            }
            RailAction(
                icon = Icons.Filled.Repeat,
                tint = Color.White,
                count = paragraph.repostCount,
                contentDescription = stringResource(R.string.action_repost),
                onClick = onRepost
            )
            if (bitmap != null) {
                RailAction(
                    icon = Icons.Filled.Download,
                    tint = Color.White,
                    count = null,
                    contentDescription = stringResource(R.string.action_download),
                    onClick = {
                        val downloadingLabel = context.getString(R.string.downloading_image_label)
                        TaskProgressManager.launch(
                            id = "download_${paragraph.id}_${System.currentTimeMillis()}",
                            type = BackgroundTaskType.DOWNLOAD,
                            label = downloadingLabel
                        ) { updateProgress ->
                            updateProgress(0.1f)
                            // Author name/avatar aren't denormalized onto the paragraph
                            // (see the comment on authorIdentifier above) — fetched
                            // here, once, only when a download is actually requested.
                            val author = runCatching { userRepo.getUser(paragraph.authorId) }.getOrNull()
                            updateProgress(0.35f)
                            val authorAvatar = author?.profileIconUrl
                                ?.takeIf { it.isNotBlank() }
                                ?.let { MediaBase64.decodeToBitmap(it) }
                            updateProgress(0.55f)
                            val watermarked = withContext(Dispatchers.Default) {
                                WatermarkUtil.applyWatermark(
                                    source = bitmap,
                                    appLabel = watermarkLabel,
                                    authorIdentifier = paragraph.authorIdentifier,
                                    authorDisplayName = author?.displayName ?: paragraph.authorIdentifier,
                                    authorAvatar = authorAvatar
                                )
                            }
                            updateProgress(0.8f)
                            val ok = withContext(Dispatchers.Default) {
                                DownloadUtil.saveToGallery(context, watermarked, "yeex_${paragraph.id}")
                            }
                            updateProgress(0.95f)
                            if (!ok) error(failedMessage)
                            savedMessage
                        }
                    }
                )
            }
            // Video download: separate branch from the image one above since
            // VIDEO paragraphs never decode to a Bitmap (see the `bitmap`
            // comment near the top of this function) — previously that meant
            // no download action showed up for videos at all. Saves the raw
            // MP4 bytes as-is: WatermarkUtil's frame-by-frame stamping isn't
            // wired up yet (see its doc + README roadmap), so this is
            // intentionally unwatermarked rather than silently failing.
            if (isVideo && hasMedia) {
                RailAction(
                    icon = Icons.Filled.Download,
                    tint = Color.White,
                    count = null,
                    contentDescription = stringResource(R.string.action_download_video),
                    onClick = {
                        val downloadingLabel = context.getString(R.string.downloading_video_label)
                        TaskProgressManager.launch(
                            id = "download_video_${paragraph.id}_${System.currentTimeMillis()}",
                            type = BackgroundTaskType.DOWNLOAD,
                            label = downloadingLabel
                        ) { updateProgress ->
                            updateProgress(0.1f)
                            val videoBytes = withContext(Dispatchers.Default) {
                                android.util.Base64.decode(paragraph.mediaBase64, android.util.Base64.NO_WRAP)
                            }
                            updateProgress(0.5f)
                            val ok = withContext(Dispatchers.Default) {
                                DownloadUtil.saveVideoToGallery(context, videoBytes, "yeex_${paragraph.id}")
                            }
                            updateProgress(0.95f)
                            if (!ok) error(failedMessage)
                            videoSavedMessage
                        }
                    }
                )
            }
            // PDF export: works for any paragraph. IMAGE reuses the same
            // decoded bitmap as the gallery download above; TEXT (which has
            // no bitmap at all) is rendered onto a card first via
            // PdfExportUtil.renderTextCard. Both paths get the same
            // WatermarkUtil stamp before being wrapped into a one-page PDF.
            RailAction(
                icon = Icons.Filled.PictureAsPdf,
                tint = Color.White,
                count = null,
                contentDescription = stringResource(R.string.action_download_pdf),
                onClick = {
                    val downloadingLabel = context.getString(R.string.downloading_pdf_label)
                    TaskProgressManager.launch(
                        id = "download_pdf_${paragraph.id}_${System.currentTimeMillis()}",
                        type = BackgroundTaskType.DOWNLOAD,
                        label = downloadingLabel
                    ) { updateProgress ->
                        updateProgress(0.1f)
                        val author = runCatching { userRepo.getUser(paragraph.authorId) }.getOrNull()
                        updateProgress(0.3f)
                        val authorAvatar = author?.profileIconUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.let { MediaBase64.decodeToBitmap(it) }
                        val authorName = author?.displayName ?: paragraph.authorIdentifier
                        updateProgress(0.5f)
                        val ok = withContext(Dispatchers.Default) {
                            val sourceBitmap = when {
                                bitmap != null -> bitmap
                                paragraph.type == ParagraphType.MOMENT.name -> PdfExportUtil.renderMomentCard(
                                    title = paragraph.text,
                                    authorLine = "@${paragraph.authorIdentifier}",
                                    steps = paragraph.momentSteps
                                )
                                else -> PdfExportUtil.renderTextCard(
                                    text = paragraph.text,
                                    authorLine = "@${paragraph.authorIdentifier}"
                                )
                            }
                            val watermarked = WatermarkUtil.applyWatermark(
                                source = sourceBitmap,
                                appLabel = watermarkLabel,
                                authorIdentifier = paragraph.authorIdentifier,
                                authorDisplayName = authorName,
                                authorAvatar = authorAvatar
                            )
                            PdfExportUtil.exportBitmap(context, watermarked, "yeex_${paragraph.id}")
                        }
                        updateProgress(0.95f)
                        if (!ok) error(failedMessage)
                        savedMessage
                    }
                }
            )
        }

        // ---- Top-start (visually the RIGHT edge in RTL, since Start follows
        // reading direction): playback speed chip (video only). Sits below
        // YeexTopBar's 52dp band instead of inside it, so it doesn't land on
        // top of the "yeex" wordmark that also lives on the Start side. ----
        if (isVideo) {
            Surface(
                onClick = {
                    val speeds = listOf(0.5f, 1f, 1.5f, 2f)
                    val idx = speeds.indexOf(playbackSpeed).let { if (it == -1) 1 else it }
                    playbackSpeed = speeds[(idx + 1) % speeds.size]
                },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 14.dp, top = TopOverlayClearance)
            ) {
                Text(
                    speedLabel(playbackSpeed),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ---- Top-end (visually the LEFT edge in RTL): overflow menu only —
        // the author avatar and expiry timer now live at the bottom, smaller,
        // alongside the caption instead of crowding the top of the screen.
        // Same below-topbar clearance as the speed chip, so it doesn't land
        // on top of YeexTopBar's search/rooms icons (which also sit on the
        // End side in RTL). ----
        Box(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 14.dp, top = TopOverlayClearance)
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options), tint = Color.White, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_report)) },
                    leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        Toast.makeText(context, comingSoonMessage, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ---- Bottom-start overlay: compact author row + expiry timer, caption, hashtags, view count ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 84.dp, bottom = 24.dp)
        ) {
            // Author avatar + handle + expiry countdown, all smaller and down
            // here instead of a larger block pinned to the top of the screen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (paragraph.authorId.isNotBlank()) onOpenProfile(paragraph.authorId) }
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "@${paragraph.authorIdentifier}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                )
                if (paragraph.authorVerified) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.verified_badge),
                        tint = YeexCrimson,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (paragraph.createdAt > 0L) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${relativeAgeLabel(paragraph.createdAt)}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (paragraph.expiresAt > 0L) {
                    Spacer(Modifier.width(8.dp))
                    CompactExpiryCountdown(expiresAt = paragraph.expiresAt)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (captionText.isNotBlank()) {
                Text(
                    translatedText ?: captionText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
                if (translatedText != null) {
                    Text(
                        stringResource(R.string.translated_label),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (hashtags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    hashtags.take(4).forEach { tag ->
                        Text(
                            "#$tag",
                            color = YeexAccent.copy(alpha = 0.95f),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.view_count, formatCount(paragraph.viewCount)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun speedLabel(speed: Float): String {
    val trimmed = if (speed == speed.toLong().toFloat()) speed.toLong().toString() else speed.toString()
    return "${trimmed}x"
}

/** Fading, scaling heart that bursts outward from a double-tap point — the
 * classic Instagram/TikTok "double tap to like" confirmation. Purely
 * decorative (no pointer input), so it never blocks taps on whatever's
 * underneath it once it starts fading. */
@Composable
private fun DoubleTapHeartBurst(offsetPx: Offset, onFinished: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1.25f, animationSpec = tween(180, easing = FastOutSlowInEasing))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        kotlinx.coroutines.delay(250)
        alpha.animateTo(0f, animationSpec = tween(280))
        onFinished()
    }
    Icon(
        Icons.Filled.Favorite,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(90.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationX = offsetPx.x - size.width / 2f
                translationY = offsetPx.y - size.height / 2f
            }
    )
}

/** Brief, centered "looped" glyph — TikTok's cue that a video just finished
 * and seamlessly restarted, so the jump back to frame 0 reads as a beat
 * instead of a glitch. Purely decorative, self-dismissing, no pointer input
 * (matches [DoubleTapHeartBurst]'s shape): scales/fades in, holds briefly,
 * fades out, then removes itself from composition via [key] in the caller. */
@Composable
private fun VideoLoopFlash(modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(150))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        kotlinx.coroutines.delay(200)
        alpha.animateTo(0f, animationSpec = tween(220))
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** Extracts "#لحظة"-style hashtags (Arabic/Latin word chars + digits/underscore)
 * out of [text], returning the caption with the hashtags stripped plus the
 * de-duplicated tag list, so the feed can show them as a separate chip row
 * (as in the reference design) instead of inline in the caption. */
private fun extractHashtags(text: String): Pair<String, List<String>> {
    val regex = Regex("#([\\p{L}0-9_]+)")
    val tags = regex.findAll(text).map { it.groupValues[1] }.distinct().toList()
    val cleaned = regex.replace(text, "").replace(Regex(" {2,}"), " ").trim()
    return cleaned to tags
}

/** Short, localized relative time ("الآن" / "منذ 5د" / "منذ 3س" / "منذ 2ي"). */
private fun relativeAgeLabel(createdAt: Long): String {
    val diffMin = (System.currentTimeMillis() - createdAt).coerceAtLeast(0) / 60000L
    return when {
        diffMin < 1 -> "الآن"
        diffMin < 60 -> "منذ ${diffMin}د"
        diffMin < 60 * 24 -> "منذ ${diffMin / 60}س"
        else -> "منذ ${diffMin / (60 * 24)}ي"
    }
}

/** Compact, single-line "HH:MM:SS left" chip, ticking once a second — now
 * shown small next to the author handle at the bottom instead of a larger
 * two-line block pinned to the top of the screen. Hidden once the paragraph
 * has expired (it'll be swept by [ParagraphRepository.purgeExpired] shortly after). */
@Composable
private fun CompactExpiryCountdown(expiresAt: Long, modifier: Modifier = Modifier) {
    if (expiresAt <= 0L) return
    var remainingMs by remember(expiresAt) { mutableStateOf(expiresAt - System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            remainingMs = expiresAt - System.currentTimeMillis()
            if (remainingMs <= 0L) break
            kotlinx.coroutines.delay(1000)
        }
    }
    if (remainingMs <= 0L) return
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val label = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(Icons.Filled.Timer, contentDescription = stringResource(R.string.expires_in_label), tint = YeexCrimson, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            color = YeexCrimson,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** One icon+count entry in the right-side action rail, TikTok-style. */
@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Long?,
    contentDescription: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = !loading,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        if (count != null) {
            Text(
                formatCount(count),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/**
 * A premium, punchy like/dislike control for the reaction rail.
 *
 * - The icon crossfades + scales between its outline and filled states.
 * - A radial "glow" ring bursts outward and fades whenever the reaction
 *   becomes active, giving tactile confirmation (like Instagram/YouTube).
 * - The whole button springs (overshoot + settle) on every tap.
 * - When active, the circular backdrop turns into a soft brand-colored
 *   gradient instead of the flat translucent-black used by neutral rail
 *   actions, so "liked"/"disliked" state reads instantly at a glance.
 */
@Composable
private fun ReactionButton(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    activeColor: Color,
    glowColor: Color,
    count: Long,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Tap "punch": overshoots past 1f then settles — feels alive, not linear.
    val pressScale = remember { Animatable(1f) }
    // Burst ring: 0f (hidden) -> 1f (fully expanded + faded) on activation.
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val backdropColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
        animationSpec = tween(220),
        label = "reactionBackdrop"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f),
        animationSpec = tween(220),
        label = "reactionBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.White,
        animationSpec = tween(220),
        label = "reactionIconTint"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .scale(pressScale.value)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        scope.launch {
                            pressScale.animateTo(
                                targetValue = 1.28f,
                                animationSpec = tween(90, easing = FastOutSlowInEasing)
                            )
                            pressScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        if (!isActive) {
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
                            }
                        }
                        onClick()
                    }
                )
        ) {
            // Expanding, fading glow burst behind the icon on activation.
            if (burst.value > 0f) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .scale(0.7f + burst.value * 0.9f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.55f * (1f - burst.value)),
                                    glowColor.copy(alpha = 0f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Circular backdrop that shifts from neutral to brand-tinted.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(backdropColor)
                    .border(width = 1.2.dp, color = borderColor, shape = CircleShape)
            )

            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    (scaleIn(initialScale = 0.4f, animationSpec = tween(200)))
                        .togetherWith(scaleOut(targetScale = 0.4f, animationSpec = tween(150)))
                },
                label = "reactionIconSwap"
            ) { active ->
                Icon(
                    imageVector = if (active) activeIcon else inactiveIcon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            formatCount(count),
            color = if (isActive) activeColor else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
