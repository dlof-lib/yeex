package com.yeex.dlof.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexNavy

/**
 * Follow button used on other users' profiles. Not following -> shows "Tek"
 * on a solid brand-gradient pill with a "+person" icon; tapping calls
 * [onToggle] and the caller flips [isFollowing], which switches the label to
 * "Teker" (i.e. now following) on a subdued outlined pill with a checkmark —
 * mirrors familiar follow/following patterns while keeping the app's own
 * tek/teker/teking terminology.
 */
@Composable
fun TekButton(isFollowing: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(50)
    AnimatedContent(
        targetState = isFollowing,
        label = "tek-button",
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { following ->
        if (following) {
            Surface(
                onClick = onToggle,
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                modifier = modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_teker), fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Surface(
                onClick = onToggle,
                shape = shape,
                modifier = modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(YeexNavy, YeexAccent))),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_tek), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
