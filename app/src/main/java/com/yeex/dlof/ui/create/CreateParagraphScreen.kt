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
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.ui.theme.yeexBrandGradient
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.MediaDuration
import com.yeex.dlof.util.VideoTrimUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TEXT_MAX_LEN = 220

private enum class ComposerType { TEXT, IMAGE, VIDEO }

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
    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { imageUri = uri; videoUri = null }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { videoUri = uri; imageUri = null }
    }

    val composerType = when {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeChip(
                icon = Icons.Filled.TextFields,
                label = stringResource(R.string.type_text),
                selected = composerType == ComposerType.TEXT,
                modifier = Modifier.weight(1f)
            ) { imageUri = null; videoUri = null }
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
        }

        if (composerType != ComposerType.TEXT) {
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

                    var type = ParagraphType.TEXT.name
                    var mediaBase64 = ""
                    var mime = ""
                    try {
                        when {
                            imageUri != null -> {
                                mediaBase64 = MediaBase64.encodeImage(context.contentResolver, imageUri!!)
                                type = ParagraphType.IMAGE.name
                                mime = "image/jpeg"
                            }
                            videoUri != null -> {
                                val durationMs = MediaDuration.getDurationMs(context, videoUri!!)
                                if (durationMs == null || durationMs < MediaDuration.MIN_VIDEO_MS) {
                                    error = context.getString(R.string.error_video_too_short)
                                    isPublishing = false
                                    return@launch
                                }
                                // Clips longer than MAX_VIDEO_MS are trimmed down to the
                                // limit (first N seconds) instead of being rejected —
                                // no re-encode, so this is fast and lossless.
                                val encoded = if (durationMs > MediaDuration.MAX_VIDEO_MS) {
                                    val trimmedFile = File(context.cacheDir, "yeex_trim_${System.currentTimeMillis()}.mp4")
                                    val trimmed = withContext(Dispatchers.IO) {
                                        VideoTrimUtil.trimToFile(context, videoUri!!, trimmedFile, MediaDuration.MAX_VIDEO_MS)
                                    }
                                    if (!trimmed) {
                                        error = context.getString(R.string.error_trim_failed)
                                        isPublishing = false
                                        return@launch
                                    }
                                    val result = MediaBase64.encodeVideoFileIfSmallEnough(trimmedFile)
                                    trimmedFile.delete()
                                    result
                                } else {
                                    MediaBase64.encodeVideoIfSmallEnough(context.contentResolver, videoUri!!)
                                }
                                if (encoded == null) {
                                    error = context.getString(R.string.error_video_too_large)
                                    isPublishing = false
                                    return@launch
                                }
                                mediaBase64 = encoded
                                type = ParagraphType.VIDEO.name
                                mime = "video/mp4"
                            }
                        }
                        // authorIdentifier/authorVerified are denormalized onto every paragraph
                        // so ParagraphCard and the feed never need a per-post user lookup.
                        val me = userRepo.getUser(uid)
                        repo.publish(
                            Paragraph(
                                authorId = uid,
                                authorIdentifier = me?.identifier ?: "",
                                authorVerified = me?.verified ?: false,
                                type = type,
                                text = text,
                                mediaBase64 = mediaBase64,
                                mediaMimeType = mime,
                                roomId = roomId ?: ""
                            )
                        )
                        onPublished()
                    } catch (e: Exception) {
                        error = e.message ?: context.getString(R.string.error_unknown)
                    } finally {
                        isPublishing = false
                    }
                }
            },
            enabled = !isPublishing && (text.isNotBlank() || imageUri != null || videoUri != null),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            val enabled = !isPublishing && (text.isNotBlank() || imageUri != null || videoUri != null)
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
