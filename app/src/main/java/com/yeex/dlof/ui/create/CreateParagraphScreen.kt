package com.yeex.dlof.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.data.model.MomentStep
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.ui.theme.yeexBrandGradient
import com.yeex.dlof.util.BackgroundTaskType
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.MediaDuration
import com.yeex.dlof.util.TaskProgressManager
import com.yeex.dlof.util.VideoTrimUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TEXT_MAX_LEN = 220
private const val MOMENT_MIN_STEPS = 2

private enum class ComposerType { TEXT, IMAGE, VIDEO, MOMENT }

/**
 * Immutable snapshot of a [MomentStepState] taken right before publish, so the
 * background [TaskProgressManager] block (which can outlive this composable
 * and runs off the main thread) never touches Compose mutable state directly.
 */
private data class MomentStepDraft(
    val id: String,
    val title: String,
    val time: String,
    val icon: String,
    val text: String,
    val imageUri: Uri?,
    val colorHex: String
)

@Composable
fun CreateParagraphScreen(
    roomId: String? = null,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: ParagraphRepository = ParagraphRepository(),
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var momentMode by remember { mutableStateOf(false) }
    // Starts with the minimum 2 stages once MOMENT is picked (see the TypeChip
    // onClick below), so the composer never shows a lone, unaddable stage.
    val momentSteps = remember { mutableStateListOf<MomentStepState>() }
    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val publishingLabel = stringResource(R.string.publishing_label)
    val publishSuccessMessage = stringResource(R.string.publish_success)
    val momentMinStepsError = stringResource(R.string.moment_min_steps_error)
    val momentStepTitleError = stringResource(R.string.moment_step_needs_title_error)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { imageUri = uri; videoUri = null; momentMode = false }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { videoUri = uri; imageUri = null; momentMode = false }
    }
    // One shared picker for every Moment stage's optional photo — which stage
    // it targets is tracked in momentImageTarget rather than creating a new
    // launcher per (dynamically-added) stage.
    var momentImageTarget by remember { mutableStateOf<MomentStepState?>(null) }
    val pickMomentStepImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) momentImageTarget?.imageUri = uri
        momentImageTarget = null
    }

    val composerType = when {
        momentMode -> ComposerType.MOMENT
        videoUri != null -> ComposerType.VIDEO
        imageUri != null -> ComposerType.IMAGE
        else -> ComposerType.TEXT
    }

    // fillMaxWidth (not fillMaxSize) so this renders as a compact pop-up sheet
    // when hosted inside FeedScreen's ModalBottomSheet, rather than stretching
    // to the full screen height.
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.create_paragraph),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.paragraph_expires),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))

        // ---- Composer text box, purple border, char counter ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, YeexAccent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .background(YeexDarkCard, RoundedCornerShape(18.dp))
                .padding(4.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= TEXT_MAX_LEN) text = it },
                placeholder = { Text(stringResource(R.string.compose_placeholder)) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "${text.length}/$TEXT_MAX_LEN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.choose_paragraph_type),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        // ---- Type selector: تغيير النوع يفتح المنتقي المناسب أو يمسح الوسائط ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeChip(
                icon = Icons.Filled.TextFields,
                label = stringResource(R.string.type_text),
                selected = composerType == ComposerType.TEXT,
                modifier = Modifier.weight(1f)
            ) { imageUri = null; videoUri = null; momentMode = false }
            TypeChip(
                icon = Icons.Filled.Image,
                label = stringResource(R.string.type_image),
                selected = composerType == ComposerType.IMAGE,
                modifier = Modifier.weight(1f)
            ) { pickImage.launch("image/*") }
            TypeChip(
                icon = Icons.Filled.Videocam,
                label = stringResource(R.string.type_video),
                selected = composerType == ComposerType.VIDEO,
                modifier = Modifier.weight(1f)
            ) { pickVideo.launch("video/*") }
            TypeChip(
                icon = Icons.Filled.Timeline,
                label = stringResource(R.string.type_moment),
                selected = composerType == ComposerType.MOMENT,
                modifier = Modifier.weight(1f)
            ) {
                imageUri = null
                videoUri = null
                momentMode = true
                if (momentSteps.isEmpty()) {
                    repeat(MOMENT_MIN_STEPS) { momentSteps.add(MomentStepState()) }
                }
            }
        }

        if (composerType == ComposerType.MOMENT) {
            Spacer(Modifier.height(18.dp))
            MomentComposer(
                steps = momentSteps,
                onPickImage = { target ->
                    momentImageTarget = target
                    pickMomentStepImage.launch("image/*")
                }
            )
        }

        if (composerType == ComposerType.IMAGE || composerType == ComposerType.VIDEO) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.media_length_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // Real, appropriately-sized preview of the picked media — tall
            // enough (240dp) to actually judge framing/crop before publishing,
            // instead of a flat confirmation strip. Images are shown at their
            // real proportions (Fit, letterboxed) and videos autoplay muted
            // on loop, matching how both render full-size in the feed itself.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .border(1.5.dp, YeexAccent, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                when (composerType) {
                    ComposerType.IMAGE -> imageUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.image_selected),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ComposerType.VIDEO -> videoUri?.let { uri ->
                        VideoUriPreview(uri = uri, modifier = Modifier.fillMaxSize())
                    }
                    ComposerType.TEXT -> {}
                }

                IconButton(
                    onClick = { imageUri = null; videoUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_media),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (composerType == ComposerType.IMAGE)
                            stringResource(R.string.image_selected)
                        else
                            stringResource(R.string.video_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isPublishing = true
                    error = null
                    val uid = authRepo.currentUid()
                    if (uid == null) { error = context.getString(R.string.error_login_required); isPublishing = false; return@launch }

                    // Fast, local pre-flight checks stay here so a problem (too-short
                    // clip, not logged in) surfaces inline in the sheet right away.
                    // Everything past this point — encoding, trimming, and the actual
                    // network publish — hands off to TaskProgressManager, which keeps
                    // running (with a real progress bar) even after this sheet closes,
                    // so the person doesn't have to sit and wait for it to finish.
                    val currentVideoUri = videoUri
                    if (currentVideoUri != null) {
                        val durationMs = MediaDuration.getDurationMs(context, currentVideoUri)
                        if (durationMs == null || durationMs < MediaDuration.MIN_VIDEO_MS) {
                            error = context.getString(R.string.error_video_too_short)
                            isPublishing = false
                            return@launch
                        }
                    }

                    if (momentMode) {
                        if (momentSteps.size < MOMENT_MIN_STEPS) {
                            error = momentMinStepsError
                            isPublishing = false
                            return@launch
                        }
                        if (momentSteps.any { it.title.isBlank() }) {
                            error = momentStepTitleError
                            isPublishing = false
                            return@launch
                        }
                    }

                    val appContext = context.applicationContext
                    val capturedText = text
                    val capturedImageUri = imageUri
                    val capturedVideoUri = currentVideoUri
                    val capturedRoomId = roomId
                    // Snapshotted right away as plain data (not the mutable-state
                    // MomentStepState objects) since this list is read from a
                    // background dispatcher inside TaskProgressManager below.
                    val capturedMomentSteps: List<MomentStepDraft> = if (momentMode) {
                        momentSteps.map { s ->
                            MomentStepDraft(s.id, s.title.trim(), s.time.trim(), s.icon, s.text.trim(), s.imageUri, s.colorHex)
                        }
                    } else emptyList()

                    TaskProgressManager.launch(
                        id = "publish_${System.currentTimeMillis()}",
                        type = BackgroundTaskType.PUBLISH,
                        label = publishingLabel
                    ) { updateProgress ->
                        var type = ParagraphType.TEXT.name
                        var mediaBase64 = ""
                        var mime = ""
                        var momentStepsToSave: List<MomentStep> = emptyList()
                        updateProgress(0.1f)

                        when {
                            capturedMomentSteps.isNotEmpty() -> {
                                type = ParagraphType.MOMENT.name
                                val stepCount = capturedMomentSteps.size
                                momentStepsToSave = capturedMomentSteps.mapIndexed { index, draft ->
                                    val encodedImage = draft.imageUri?.let { uri ->
                                        runCatching {
                                            MediaBase64.encodeMomentStepImage(appContext.contentResolver, uri)
                                        }.getOrNull()
                                    } ?: ""
                                    updateProgress(0.1f + 0.6f * (index + 1) / stepCount)
                                    MomentStep(
                                        id = draft.id,
                                        order = index,
                                        title = draft.title,
                                        time = draft.time,
                                        icon = draft.icon,
                                        text = draft.text,
                                        imageBase64 = encodedImage,
                                        colorHex = draft.colorHex
                                    )
                                }
                            }
                            capturedImageUri != null -> {
                                mediaBase64 = MediaBase64.encodeImage(appContext.contentResolver, capturedImageUri)
                                type = ParagraphType.IMAGE.name
                                mime = "image/jpeg"
                                updateProgress(0.5f)
                            }
                            capturedVideoUri != null -> {
                                val durationMs = MediaDuration.getDurationMs(appContext, capturedVideoUri)
                                    ?: error(appContext.getString(R.string.error_video_too_short))
                                // Clips longer than MAX_VIDEO_MS are trimmed down to the
                                // limit (first N seconds) instead of being rejected —
                                // no re-encode, so this is fast and lossless.
                                val encoded = if (durationMs > MediaDuration.MAX_VIDEO_MS) {
                                    val trimmedFile = File(appContext.cacheDir, "yeex_trim_${System.currentTimeMillis()}.mp4")
                                    val trimmed = withContext(Dispatchers.IO) {
                                        VideoTrimUtil.trimToFile(appContext, capturedVideoUri, trimmedFile, MediaDuration.MAX_VIDEO_MS)
                                    }
                                    updateProgress(0.35f)
                                    if (!trimmed) error(appContext.getString(R.string.error_trim_failed))
                                    val result = MediaBase64.encodeVideoFileIfSmallEnough(trimmedFile)
                                    trimmedFile.delete()
                                    result
                                } else {
                                    MediaBase64.encodeVideoIfSmallEnough(appContext.contentResolver, capturedVideoUri)
                                }
                                    ?: error(appContext.getString(R.string.error_video_too_large))
                                mediaBase64 = encoded
                                type = ParagraphType.VIDEO.name
                                mime = "video/mp4"
                                updateProgress(0.6f)
                            }
                        }

                        // authorIdentifier/authorVerified are denormalized onto every paragraph
                        // so ParagraphCard and the feed never need a per-post user lookup.
                        val me = userRepo.getUser(uid)
                        updateProgress(0.75f)
                        repo.publish(
                            Paragraph(
                                authorId = uid,
                                authorIdentifier = me?.identifier ?: "",
                                authorVerified = me?.verified ?: false,
                                type = type,
                                text = capturedText,
                                mediaBase64 = mediaBase64,
                                mediaMimeType = mime,
                                roomId = capturedRoomId ?: "",
                                momentSteps = momentStepsToSave
                            )
                        )
                        updateProgress(0.95f)
                        publishSuccessMessage
                    }

                    isPublishing = false
                    onPublished()
                }
            },
            enabled = !isPublishing && (
                text.isNotBlank() || imageUri != null || videoUri != null ||
                    (momentMode && momentSteps.size >= MOMENT_MIN_STEPS)
            ),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            val enabled = !isPublishing && (
                text.isNotBlank() || imageUri != null || videoUri != null ||
                    (momentMode && momentSteps.size >= MOMENT_MIN_STEPS)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (enabled) yeexBrandGradient() else androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                        ),
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(R.string.publish_action),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Muted, looping ExoPlayer preview for a just-picked video [Uri] — plays
 * straight from the content Uri the picker returned, before publish encodes
 * it to Base64. Mirrors [com.yeex.dlof.ui.components.VideoPlayer]'s zoom-fill
 * behaviour so the composer preview matches how the video will actually
 * appear full-screen in the feed.
 */
@Composable
private fun VideoUriPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(uri) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    )
}

@Composable
private fun TypeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) YeexAccent.copy(alpha = 0.18f) else YeexDarkCard,
        border = if (selected) BorderStroke(1.5.dp, YeexAccent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) YeexAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) YeexAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
