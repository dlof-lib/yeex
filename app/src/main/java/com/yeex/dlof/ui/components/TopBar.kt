package com.yeex.dlof.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.yeexBrandGradient

/**
 * The app's single top-bar treatment, used across the immersive feed (and
 * fit to reuse anywhere else a floating header is needed). It always keeps
 * the same three-slot anatomy — brand mark, a swappable center slot, and
 * action icons — so every screen that uses it reads as the same app rather
 * than a one-off overlay.
 *
 * It floats over full-bleed media (video, images) instead of pushing content
 * down like a Scaffold TopAppBar, so it always draws a soft top-to-transparent
 * scrim behind itself first to keep icons and text legible over bright media.
 * The brand mark itself is the app's official launcher icon (a small rounded
 * badge, matching what the person already recognizes from their home screen
 * and the OS share sheet) next to the wordmark, rather than text alone —
 * reads as a "real app" top bar instead of a generic label.
 *
 * @param showWordmark Off for screens that are already one level below the
 *   home feed (e.g. a room's own feed, which shows the room's name instead)
 *   so the brand mark isn't shown twice.
 * @param onOpenRooms/onOpenSearch/onOpenTopics When null, that action icon is
 *   omitted entirely rather than shown disabled, so callers that don't need
 *   an action don't have to pass a no-op lambda.
 * @param center The swappable middle slot — the "لك / متابعين / حاويات"
 *   segmented control on the home feed, a screen title elsewhere, or
 *   nothing at all.
 */
@Composable
fun YeexTopBar(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
    onOpenRooms: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenTopics: (() -> Unit)? = null,
    center: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.28f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .height(56.dp)
    ) {
        if (showWordmark) {
            val context = LocalContext.current
            // R.mipmap.ic_launcher_round resolves to an <adaptive-icon> XML
            // on API 26+ (mipmap-anydpi-v26) rather than a plain PNG/vector.
            // painterResource() only understands VectorDrawables and
            // rasterized assets and throws immediately on anything else —
            // that crash was happening the instant the home feed (the first
            // screen after login) composed this bar. Going through
            // Drawable.toBitmap() instead works for every drawable type
            // (adaptive icon, plain PNG, vector) on every API level, so this
            // can never throw.
            val appIconBitmap = remember {
                runCatching {
                    ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                        ?.toBitmap(width = 96, height = 96)
                        ?.asImageBitmap()
                }.getOrNull()
            }
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = yeexBrandGradient(),
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }

        Box(Modifier.align(Alignment.Center)) { center() }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenRooms != null) {
                TopBarIcon(
                    icon = Icons.Filled.Groups,
                    contentDescription = stringResource(R.string.browse_rooms),
                    onClick = onOpenRooms
                )
            }
            if (onOpenTopics != null) {
                TopBarIcon(
                    icon = Icons.Filled.MenuBook,
                    contentDescription = stringResource(R.string.topics_title),
                    onClick = onOpenTopics
                )
            }
            if (onOpenSearch != null) {
                TopBarIcon(
                    icon = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.nav_search),
                    onClick = onOpenSearch
                )
            }
        }
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Spacer(modifier: Modifier) {
    androidx.compose.foundation.layout.Spacer(modifier)
}
