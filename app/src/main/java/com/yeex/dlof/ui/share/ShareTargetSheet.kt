package com.yeex.dlof.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDimens
import com.yeex.dlof.util.PendingShareBridge

/**
 * The single entry point for both new inbound-content flows:
 *  - a screenshot [PendingShareBridge.Source.SCREENSHOT] the person can
 *    choose to publish (see ScreenshotWatcher, opt-in in Settings), and
 *  - content shared into YEEX from another app
 *    [PendingShareBridge.Source.EXTERNAL_SHARE] (MainActivity's ACTION_SEND
 *    handling).
 *
 * Both just need one decision from the person — "فقرة" (ephemeral, 24h feed)
 * or "موضوع" (permanent, discussion) — so one shared sheet handles both
 * instead of two near-identical dialogs. Mounted once near the root of
 * [com.yeex.dlof.navigation.YeexNavGraph] so it can pop up over whatever
 * screen the person happens to be on when either source fires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetSheet(
    content: PendingShareBridge.PendingContent,
    onChoosePublishAsParagraph: () -> Unit,
    onChoosePublishAsTopic: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = YeexDimens.spaceLg, vertical = YeexDimens.spaceMd)) {
            Text(
                stringResource(R.string.share_target_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.size(YeexDimens.spaceXs))
            Text(
                if (content.source == PendingShareBridge.Source.SCREENSHOT)
                    stringResource(R.string.share_target_screenshot_subtitle)
                else stringResource(R.string.share_target_share_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.size(YeexDimens.spaceLg))

            ShareTargetOptionRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.share_target_as_paragraph),
                subtitle = stringResource(R.string.share_target_as_paragraph_desc),
                onClick = onChoosePublishAsParagraph
            )
            Spacer(Modifier.size(YeexDimens.spaceSm))
            ShareTargetOptionRow(
                icon = Icons.Filled.MenuBook,
                title = stringResource(R.string.share_target_as_topic),
                subtitle = stringResource(R.string.share_target_as_topic_desc),
                onClick = onChoosePublishAsTopic
            )

            Spacer(Modifier.size(YeexDimens.spaceLg))
            Text(
                stringResource(R.string.share_target_dismiss),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = YeexDimens.spaceMd),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShareTargetOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YeexDimens.radiusMedium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(YeexDimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShareTargetIconBadge(icon)
        Spacer(Modifier.width(YeexDimens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShareTargetIconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(YeexDimens.iconBadgeSize)
            .clip(CircleShape)
            .background(YeexAccent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(20.dp))
    }
}
