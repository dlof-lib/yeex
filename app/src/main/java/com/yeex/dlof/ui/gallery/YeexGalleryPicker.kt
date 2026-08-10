package com.yeex.dlof.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexBlack
import com.yeex.dlof.ui.theme.YeexBrandGradient
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexDarkSurface
import com.yeex.dlof.ui.theme.YeexGray
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.ui.theme.YeexWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "YeexGallery"

/** Media kind of a gallery item, as read straight from MediaStore. */
enum class GalleryMediaType { IMAGE, VIDEO }

/** One row from the device's MediaStore image/video index — the "real gallery" built from scratch. */
data class GalleryItem(val uri: Uri, val dateAddedSec: Long, val type: GalleryMediaType, val durationMs: Long = 0L)

/**
 * Reads the device's real photo *and* video library straight from
 * MediaStore, built from scratch (no cached/fake state, no third-party
 * gallery libs) so the picker renders a live, on-brand grid instead of
 * handing off to the OS photo picker UI.
 *
 * Deliberately avoids the old "ORDER BY ... LIMIT n" trick embedded in the
 * sort-order string — while MediaProvider tolerates it on stock Android, it
 * isn't a documented/guaranteed contract and silently returns zero rows on
 * some OEM content-provider implementations, which is what produced the
 * "لا توجد صور" empty state even with photos present on the device. Instead
 * this queries with a normal DATE_ADDED DESC sort and caps the result with
 * Kotlin's `take()` after reading the cursor.
 */
private object MediaGalleryLoader {

    private fun queryCollection(
        context: android.content.Context,
        collection: Uri,
        type: GalleryMediaType,
        limit: Int
    ): List<GalleryItem> {
        val result = mutableListOf<GalleryItem>()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (type == GalleryMediaType.VIDEO) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val durationCol = if (type == GalleryMediaType.VIDEO) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else -1
                while (cursor.moveToNext() && result.size < limit) {
                    val id = cursor.getLong(idCol)
                    val date = cursor.getLong(dateCol)
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    result += GalleryItem(uri, date, type, duration)
                }
            }
        }.onFailure { e ->
            // Surfaced via Logcat instead of swallowed, so a genuine provider
            // failure (vs. a device with an empty library) is diagnosable.
            Log.e(TAG, "Failed to query ${type.name} MediaStore collection", e)
        }
        return result
    }

    /** Loads real photos + videos from MediaStore, newest first, merged and re-sorted by date. */
    suspend fun loadMedia(context: android.content.Context, limit: Int = 600): List<GalleryItem> =
        withContext(Dispatchers.IO) {
            val images = queryCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, GalleryMediaType.IMAGE, limit)
            val videos = queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, GalleryMediaType.VIDEO, limit)
            (images + videos).sortedByDescending { it.dateAddedSec }.take(limit)
        }
}

private fun readMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private enum class GalleryTab { ALL, PHOTOS, VIDEOS }

/**
 * Full-screen, on-brand media picker: dark violet/black background, the
 * signature purple → pink gradient header, a live grid of the device's real
 * photos *and* videos with All/Photos/Videos tabs, and a tap-to-select flow
 * with an explicit confirm step so a misplaced tap never immediately
 * commits a picture.
 *
 * @param onImagePicked called once with the confirmed image [Uri].
 * @param onVideoPicked called once with the confirmed video [Uri] — omit to keep this an image-only picker (e.g. avatar/banner slots).
 * @param onOpenSystemPicker optional fallback (e.g. launching ActivityResultContracts.GetContent)
 *   offered from the empty/denied states and from the header, for files outside the photo index.
 */
@Composable
fun YeexGalleryPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    onVideoPicked: ((Uri) -> Unit)? = null,
    /** When true, this instance is video-only (e.g. the video type chip in
     * the composer) — hides the All/Photos/Videos tabs and pins the grid to
     * videos, instead of defaulting to the mixed "all" view. */
    forceVideoOnly: Boolean = false,
    title: String = stringResource(R.string.gallery_picker_title),
    onOpenSystemPicker: (() -> Unit)? = null
) {
    if (!visible) return
    val context = LocalContext.current
    val permissions = remember { readMediaPermissions() }

    var hasPermission by remember {
        mutableStateOf(permissions.all { ContextCompatPermissionGranted(context, it) })
    }
    var allMedia by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<GalleryItem?>(null) }
    var tab by remember { mutableStateOf(if (forceVideoOnly) GalleryTab.VIDEOS else GalleryTab.ALL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasPermission = results.values.all { it } }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            allMedia = MediaGalleryLoader.loadMedia(context)
            isLoading = false
        }
    }

    val showVideos = onVideoPicked != null
    val visibleMedia = remember(allMedia, tab, showVideos) {
        when (tab) {
            GalleryTab.ALL -> if (showVideos) allMedia else allMedia.filter { it.type == GalleryMediaType.IMAGE }
            GalleryTab.PHOTOS -> allMedia.filter { it.type == GalleryMediaType.IMAGE }
            GalleryTab.VIDEOS -> allMedia.filter { it.type == GalleryMediaType.VIDEO }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(YeexBlack)
        ) {
            // ---- Branded header ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(YeexDarkSurface, YeexDarkCard)))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = YeexWhite)
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(title, color = YeexWhite, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.gallery_picker_subtitle),
                            color = YeexGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (onOpenSystemPicker != null) {
                        TextButton(onClick = onOpenSystemPicker) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.gallery_picker_browse_files), color = YeexAccent, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(YeexBrandGradient)
                )
                if (showVideos && !forceVideoOnly) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GalleryTabChip(stringResource(R.string.gallery_picker_title), tab == GalleryTab.ALL) { tab = GalleryTab.ALL; selected = null }
                        GalleryTabChip(stringResource(R.string.gallery_tab_photos), tab == GalleryTab.PHOTOS) { tab = GalleryTab.PHOTOS; selected = null }
                        GalleryTabChip(stringResource(R.string.gallery_tab_videos), tab == GalleryTab.VIDEOS) { tab = GalleryTab.VIDEOS; selected = null }
                    }
                }
            }

            // ---- Body ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasPermission -> GalleryEmptyState(
                        icon = Icons.Filled.Collections,
                        title = stringResource(R.string.gallery_picker_permission_title),
                        message = stringResource(R.string.gallery_picker_permission_message),
                        actionLabel = stringResource(R.string.gallery_picker_grant_access),
                        onAction = { permissionLauncher.launch(permissions) }
                    )
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YeexAccent)
                    }
                    visibleMedia.isEmpty() -> GalleryEmptyState(
                        icon = Icons.Filled.CameraAlt,
                        title = stringResource(R.string.gallery_picker_empty_title),
                        message = stringResource(R.string.gallery_picker_empty_message),
                        actionLabel = onOpenSystemPicker?.let { stringResource(R.string.gallery_picker_browse_files) },
                        onAction = onOpenSystemPicker
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(visibleMedia, key = { it.uri.toString() }) { item ->
                            GalleryThumb(
                                item = item,
                                isSelected = selected?.uri == item.uri,
                                onClick = { selected = item }
                            )
                        }
                    }
                }
            }

            // ---- Confirm bar ----
            AnimatedVisibility(visible = selected != null, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(YeexDarkSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selected?.let { sel ->
                        AsyncImage(
                            model = sel.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.gallery_picker_one_selected),
                        color = YeexWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            selected?.let { sel ->
                                if (sel.type == GalleryMediaType.VIDEO) onVideoPicked?.invoke(sel.uri)
                                else onImagePicked(sel.uri)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YeexAccent, contentColor = YeexWhite),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gallery_picker_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) YeexAccent else YeexDarkCard,
        onClick = onClick
    ) {
        Text(
            label,
            color = if (selected) YeexWhite else YeexGray,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GalleryThumb(item: GalleryItem, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 0.92f else 1f, label = "thumbScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(YeexDarkCard)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        )
        if (item.type == GalleryMediaType.VIDEO) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = YeexWhite,
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, Brush.linearGradient(listOf(YeexAccent, YeexPink)), RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(YeexAccent, YeexPink))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = YeexWhite, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(YeexDarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = YeexWhite, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, color = YeexGray, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = YeexAccent, contentColor = YeexWhite),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun ContextCompatPermissionGranted(context: android.content.Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
