package com.yeex.dlof.ui.create

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.data.model.LinkPreview
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.TopicRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.LinkPreviewCard
import com.yeex.dlof.ui.components.extractTopicTags
import com.yeex.dlof.ui.gallery.YeexGalleryPickerSheet
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCard
import com.yeex.dlof.ui.theme.YeexChip
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.ui.theme.YeexIconBadge
import com.yeex.dlof.ui.theme.YeexPrimaryButton
import com.yeex.dlof.ui.theme.yeexBrandGradient
import com.yeex.dlof.util.LinkPreviewUtil
import com.yeex.dlof.util.MediaBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TITLE_MAX_LEN = 150
private const val BODY_MAX_LEN = 20000

/**
 * YEEX TOPICS composer. Two publish modes:
 *  - TEXT: title + a markdown-lite body (toolbar inserts ## / > / - / ``` ```
 *    snippets), optional cover image, optional attached paragraphs.
 *  - LINK: paste a URL and YEEX fetches a "Link Card" preview (YouTube/GitHub
 *    /website) instead of showing the bare link — see [LinkPreviewUtil].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTopicScreen(
    roomId: String? = null,
    // Same external-prefill contract as CreateParagraphScreen: a screenshot
    // (ScreenshotWatcher) or shared content from another app
    // (ShareTargetSheet/PendingShareBridge) can seed the cover image / body
    // text before the person starts typing.
    initialImageUri: Uri? = null,
    initialText: String = "",
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: TopicRepository = TopicRepository(),
    onPublished: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()

    var mode by remember { mutableStateOf(TopicType.TEXT) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(initialText) }
    var coverUri by remember { mutableStateOf(initialImageUri) }
    var showGallery by remember { mutableStateOf(false) }

    var linkInput by remember { mutableStateOf("") }
    var linkPreview by remember { mutableStateOf<LinkPreview?>(null) }
    var isFetchingLink by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    var attachable by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    val selectedParagraphIds = remember { mutableStateOf(setOf<String>()) }

    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.topic_invalid_error)
    val linkFetchError = stringResource(R.string.topic_link_fetch_error)

    LaunchedEffect(myUid) {
        if (myUid != null) attachable = repo.getMyAttachableParagraphs(myUid)
    }

    YeexGalleryPickerSheet(
        visible = showGallery,
        onDismiss = { showGallery = false },
        onImagePicked = { uri -> coverUri = uri; showGallery = false }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.topic_create_title),
                style = MaterialTheme.typography.titleLarge.copy(brush = yeexBrandGradient()),
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
        }

        Spacer(Modifier.height(10.dp))

        // ---- TEXT / LINK mode switch ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YeexChip(stringResource(R.string.topic_type_text), mode == TopicType.TEXT) { mode = TopicType.TEXT }
            YeexChip(stringResource(R.string.topic_type_link), mode == TopicType.LINK) { mode = TopicType.LINK }
        }

        Spacer(Modifier.height(14.dp))

        if (mode == TopicType.TEXT) {
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= TITLE_MAX_LEN) title = it },
                label = { Text(stringResource(R.string.topic_title_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))

            // ---- Markdown-lite toolbar ----
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                YeexIconBadge(Icons.Filled.Title, stringResource(R.string.topic_toolbar_heading), shape = CircleShape, size = 36.dp, onClick = { body = body.appendLine("## ") })
                YeexIconBadge(Icons.Filled.FormatQuote, stringResource(R.string.topic_toolbar_quote), shape = CircleShape, size = 36.dp, onClick = { body = body.appendLine("> ") })
                YeexIconBadge(Icons.Filled.FormatListBulleted, stringResource(R.string.topic_toolbar_list), shape = CircleShape, size = 36.dp, onClick = { body = body.appendLine("- ") })
                YeexIconBadge(Icons.Filled.Code, stringResource(R.string.topic_toolbar_code), shape = CircleShape, size = 36.dp, onClick = { body = body.appendLine("```\n\n```") })
            }
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= BODY_MAX_LEN) body = it },
                label = { Text(stringResource(R.string.topic_body_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                supportingText = { Text("${body.length}/$BODY_MAX_LEN") }
            )

            Spacer(Modifier.height(10.dp))

            if (coverUri != null) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(YeexDimens.radiusMedium))
                    )
                    IconButton(onClick = { coverUri = null }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    }
                }
            } else {
                YeexCard(onClick = { showGallery = true }, shape = RoundedCornerShape(YeexDimens.radiusMedium)) {
                    Row(Modifier.padding(YeexDimens.spaceMd), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = YeexAccent)
                        Spacer(Modifier.width(YeexDimens.spaceSm))
                        Text(stringResource(R.string.topic_add_cover_image))
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = linkInput,
                onValueChange = { linkInput = it; linkPreview = null; linkError = null },
                label = { Text(stringResource(R.string.topic_link_hint)) },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    if (LinkPreviewUtil.looksLikeUrl(linkInput)) {
                        isFetchingLink = true
                        linkError = null
                        scope.launch {
                            val preview = withContext(Dispatchers.IO) { runCatching { LinkPreviewUtil.fetch(linkInput.trim()) }.getOrNull() }
                            isFetchingLink = false
                            if (preview == null) linkError = linkFetchError else linkPreview = preview
                        }
                    } else {
                        linkError = linkFetchError
                    }
                },
                shape = RoundedCornerShape(YeexDimens.radiusPill),
                color = YeexAccent
            ) {
                Row(Modifier.padding(horizontal = YeexDimens.spaceLg, vertical = YeexDimens.spaceMd - 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isFetchingLink) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(stringResource(R.string.topic_link_preview_button), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            linkError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            linkPreview?.let { preview ->
                Spacer(Modifier.height(10.dp))
                LinkPreviewCard(preview = preview, onClick = {})
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= TITLE_MAX_LEN) title = it },
                label = { Text(stringResource(R.string.topic_link_comment_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4
            )
        }

        // ---- موضوع ↔ فقرة: attach existing (still-active) paragraphs ----
        if (attachable.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.topic_attach_paragraphs_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachable, key = { it.id }) { p ->
                    val selected = p.id in selectedParagraphIds.value
                    Surface(
                        onClick = {
                            selectedParagraphIds.value = if (selected) selectedParagraphIds.value - p.id else selectedParagraphIds.value + p.id
                        },
                        shape = RoundedCornerShape(YeexDimens.radiusSmall),
                        border = BorderStroke(YeexDimens.borderWidthSelected, if (selected) YeexAccent else MaterialTheme.colorScheme.outline)
                    ) {
                        Box(Modifier.size(72.dp)) {
                            val bmp = remember(p.mediaBase64) {
                                if (p.mediaBase64.isNotBlank()) runCatching { MediaBase64.decodeToBitmap(p.mediaBase64) }.getOrNull() else null
                            }
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Text(p.text.take(10), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        YeexPrimaryButton(
            text = stringResource(R.string.topic_publish_button),
            isLoading = isPublishing,
            onClick = {
                if (myUid == null) {
                    // not signed in — nothing to publish
                } else {
                    val validText = mode == TopicType.TEXT && (title.isNotBlank() || body.isNotBlank())
                    val validLink = mode == TopicType.LINK && linkPreview != null
                    if (!validText && !validLink) {
                        error = invalidMessage
                    } else {
                        isPublishing = true
                        error = null
                        scope.launch {
                            val user = userRepo.getUser(myUid)
                            val (hashtags, mentions) = extractTopicTags("$title\n$body")
                            val coverBase64 = coverUri?.let {
                                withContext(Dispatchers.IO) { runCatching { MediaBase64.encodeImage(context.contentResolver, it) }.getOrNull() }
                            }.orEmpty()
                            val topic = Topic(
                                authorId = myUid,
                                authorIdentifier = user?.identifier.orEmpty(),
                                authorVerified = user?.verified ?: false,
                                type = mode.name,
                                title = title,
                                body = if (mode == TopicType.TEXT) body else "",
                                imageBase64 = coverBase64,
                                link = if (mode == TopicType.LINK) linkPreview else null,
                                hashtags = hashtags,
                                mentions = mentions,
                                roomId = roomId.orEmpty()
                            )
                            runCatching { repo.publish(topic) }
                                .onSuccess { id ->
                                    for (pid in selectedParagraphIds.value) {
                                        runCatching { repo.linkParagraph(id, pid) }
                                    }
                                    isPublishing = false
                                    onPublished(id)
                                }
                                .onFailure {
                                    isPublishing = false
                                    error = invalidMessage
                                }
                        }
                    }
                }
            }
        )
    }
}

/** Appends [prefix] as a new line at the end of the body — used by the markdown-lite toolbar buttons. */
private fun String.appendLine(prefix: String): String =
    if (this.isBlank()) prefix else this.trimEnd('\n') + "\n" + prefix
