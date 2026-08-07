package com.yeex.dlof.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.util.MediaBase64

/**
 * Shared round avatar used anywhere a user needs to be shown compactly
 * (profile header, search results, follower lists, etc.) so every "browse
 * other accounts" surface renders the same brand-gradient ring and
 * placeholder person icon instead of bare text rows.
 */
@Composable
fun UserAvatar(iconBase64: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(iconBase64) {
        if (iconBase64.isNotBlank()) runCatching { MediaBase64.decodeToBitmap(iconBase64) }.getOrNull() else null
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Brush.horizontalGradient(listOf(YeexNavy, YeexAccent)), CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(size * 0.6f))
        }
    }
}
