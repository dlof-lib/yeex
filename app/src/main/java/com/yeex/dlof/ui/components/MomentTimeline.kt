package com.yeex.dlof.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeex.dlof.data.model.MomentStep
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.util.MediaBase64

/**
 * Renders a "لحظة" (Moment) paragraph as a connected, scrollable vertical
 * timeline — the reference design's
 * `08:00 🚗 الانطلاق ↓ 10:30 ☕ الوصول ↓ ...` sequence — instead of a single
 * flat text/image/video. Used by [ParagraphCard] as the full-bleed background
 * layer for `ParagraphType.MOMENT`, in the same slot TEXT/IMAGE/VIDEO occupy.
 */
@Composable
fun MomentTimeline(steps: List<MomentStep>, modifier: Modifier = Modifier) {
    val ordered = remember(steps) { steps.sortedBy { it.order } }
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(colors = listOf(YeexAccent.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.9f)))
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(ordered, key = { _, step -> step.id.ifBlank { step.order.toString() } }) { index, step ->
                MomentStepRow(step = step, isLast = index == ordered.lastIndex)
            }
        }
    }
}

@Composable
private fun MomentStepRow(step: MomentStep, isLast: Boolean) {
    val dotColor = step.colorHex.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: YeexAccent
    val bitmap = remember(step.id, step.imageBase64) {
        step.imageBase64.takeIf { it.isNotBlank() }?.let { MediaBase64.decodeToBitmap(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 4.dp)
    ) {
        // ---- Connector: vertical line running through a ringed emoji dot ----
        Box(modifier = Modifier.width(34.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .then(if (isLast) Modifier.height(28.dp) else Modifier.fillMaxHeight())
                    .background(dotColor.copy(alpha = 0.4f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.5.dp, dotColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(step.icon.ifBlank { "•" }, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = 20.dp, top = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.time.isNotBlank()) {
                    Text(
                        step.time,
                        color = dotColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    step.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
            if (step.text.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    step.text,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )
            }
            if (bitmap != null) {
                Spacer(Modifier.height(6.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}
