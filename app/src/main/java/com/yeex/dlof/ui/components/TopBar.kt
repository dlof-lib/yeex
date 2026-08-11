package com.yeex.dlof.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .height(52.dp)
    ) {
        if (showWordmark) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = yeexBrandGradient(),
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier.align(Alignment.CenterStart)
            )
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
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
