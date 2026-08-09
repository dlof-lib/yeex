package com.yeex.dlof.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

/** One row from the device's MediaStore image index. */
data class GalleryImage(val uri: Uri, val dateAddedSec: Long)

/**
 * Reads the device photo library straight from MediaStore so the picker can
 * render its own branded grid instead of handing off to the OS photo picker
 * UI (which mixes in screenshots, stickers, app-generated images, etc. with
 * no yeex styling).
 */
private object MediaGalleryLoader {
    suspend fun loadImages(context: android.content.Context, limit: Int = 400): List<GalleryImage> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<GalleryImage>()
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
            runCatching {
                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val date = cursor.getLong(dateCol)
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        result += GalleryImage(uri, date)
                    }
                }
            }
            result
        }
}

private fun readMediaPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Full-screen, on-brand image picker: dark violet/black background, the
 * signature purple → pink gradient header, a live grid of the device's
 * photos, and a tap-to-select flow with an explicit confirm step so a
 * misplaced tap never immediately commits a picture.
 *
 * @param onImagePicked called once with the confirmed [Uri]; the sheet closes itself after.
 * @param onOpenSystemPicker optional fallback (e.g. launching ActivityResultContracts.GetContent)
 *   offered from the empty/denied states and from the header, for files outside the photo index.
 */
@Composable
fun YeexGalleryPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    title: String = stringResource(R.string.gallery_picker_title),
    onOpenSystemPicker: (() -> Unit)? = null
) {
    if (!visible) return
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompatPermissionGranted(context, readMediaPermission())
        )
    }
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<GalleryImage?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            images = MediaGalleryLoader.loadImages(context)
            isLoading = false
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
            }

            // ---- Body ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasPermission -> GalleryEmptyState(
                        icon = Icons.Filled.Collections,
                        title = stringResource(R.string.gallery_picker_permission_title),
                        message = stringResource(R.string.gallery_picker_permission_message),
                        actionLabel = stringResource(R.string.gallery_picker_grant_access),
                        onAction = { permissionLauncher.launch(readMediaPermission()) }
                    )
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YeexAccent)
                    }
                    images.isEmpty() -> GalleryEmptyState(
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
                        items(images, key = { it.uri.toString() }) { img ->
                            GalleryThumb(
                                image = img,
                                isSelected = selected?.uri == img.uri,
                                onClick = { selected = img }
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
                        onClick = { selected?.let { onImagePicked(it.uri) } },
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
private fun GalleryThumb(image: GalleryImage, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 0.92f else 1f, label = "thumbScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(YeexDarkCard)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        )
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
