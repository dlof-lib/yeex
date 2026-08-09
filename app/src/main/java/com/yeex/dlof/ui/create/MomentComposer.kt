package com.yeex.dlof.ui.create

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.yeexBrandGradient
import java.util.UUID

/** "" is treated as "use the default brand gradient" rather than a fixed color. */
val MOMENT_COLOR_PALETTE = listOf("", "#9B5CF6", "#EE2A8B", "#FF3B30", "#FF9500", "#FFD60A", "#34C759", "#0A84FF")

/**
 * Mutable, per-field-observable draft for one Moment stage while it's being
 * edited in the composer. Kept separate from [com.yeex.dlof.data.model.MomentStep]
 * (which is the immutable, Firebase-serializable form) because the photo is
 * still a local [Uri] here — it's only encoded to Base64 at publish time.
 */
class MomentStepState(
    val id: String = UUID.randomUUID().toString(),
    title: String = "",
    time: String = "",
    icon: String = MOMENT_ICON_PALETTE.first().key,
    text: String = "",
    imageUri: Uri? = null,
    colorHex: String = ""
) {
    var title by mutableStateOf(title)
    var time by mutableStateOf(time)
    var icon by mutableStateOf(icon)
    var text by mutableStateOf(text)
    var imageUri by mutableStateOf(imageUri)
    var colorHex by mutableStateOf(colorHex)
}

private fun <T> SnapshotStateList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}

/**
 * The "مراحل اللحظة" editor: an ordered list of stage cards, each with a
 * drag handle (long-press + drag to reorder — swaps live once the dragged
 * card's offset crosses half a neighbor's height, mirroring the classic
 * reorderable-list pattern), inline fields, an emoji palette, a status-color
 * palette, and an optional per-stage photo.
 */
@Composable
fun MomentComposer(
    steps: SnapshotStateList<MomentStepState>,
    onPickImage: (MomentStepState) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    Column(modifier = modifier) {
        Text(
            stringResource(R.string.moment_steps_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.moment_steps_hint_prefix),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 3.dp).size(14.dp)
            )
            Text(
                stringResource(R.string.moment_steps_hint_suffix),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        steps.forEachIndexed { index, step ->
            val isDragging = index == draggedIndex
            key(step.id) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                        .onGloballyPositioned { itemHeights[index] = it.size.height }
                        .padding(bottom = 10.dp)
                ) {
                    MomentStepCard(
                        index = index,
                        step = step,
                        canRemove = steps.size > 2,
                        onRemove = { steps.removeAt(index) },
                        onPickImage = { onPickImage(step) },
                        onClearImage = { step.imageUri = null },
                        dragModifier = Modifier.pointerInput(step.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedIndex = index; dragOffsetY = 0f },
                                onDragEnd = { draggedIndex = -1; dragOffsetY = 0f },
                                onDragCancel = { draggedIndex = -1; dragOffsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val liveIndex = draggedIndex
                                    val stepHeight = (itemHeights[liveIndex] ?: return@detectDragGesturesAfterLongPress) + 10
                                    if (dragOffsetY > stepHeight / 2 && liveIndex < steps.lastIndex) {
                                        steps.move(liveIndex, liveIndex + 1)
                                        draggedIndex = liveIndex + 1
                                        dragOffsetY -= stepHeight
                                    } else if (dragOffsetY < -stepHeight / 2 && liveIndex > 0) {
                                        steps.move(liveIndex, liveIndex - 1)
                                        draggedIndex = liveIndex - 1
                                        dragOffsetY += stepHeight
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { steps.add(MomentStepState()) },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, YeexAccent.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.moment_add_step), color = YeexAccent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MomentStepCard(
    index: Int,
    step: MomentStepState,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    dragModifier: Modifier
) {
    val accent = step.colorHex.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: YeexAccent

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = YeexDarkCard,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.moment_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragModifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.moment_step_label, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.moment_step_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = step.time,
                    onValueChange = { if (it.length <= 20) step.time = it },
                    label = { Text(stringResource(R.string.moment_step_time_label)) },
                    placeholder = { Text(stringResource(R.string.moment_step_time_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
                OutlinedTextField(
                    value = step.title,
                    onValueChange = { if (it.length <= 60) step.title = it },
                    label = { Text(stringResource(R.string.moment_step_title_label)) },
                    placeholder = { Text(stringResource(R.string.moment_step_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = step.text,
                onValueChange = { if (it.length <= 160) step.text = it },
                label = { Text(stringResource(R.string.moment_step_text_label)) },
                placeholder = { Text(stringResource(R.string.moment_step_text_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ---- Icon category palette (real vector icons — see MomentIcons.kt) ----
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MOMENT_ICON_PALETTE.forEach { option ->
                    val selected = step.icon == option.key
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { step.icon = option.key }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            option.icon,
                            contentDescription = null,
                            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Status-color palette — tints the dot/ring in the rendered timeline ----
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MOMENT_COLOR_PALETTE.forEach { hex ->
                    val selected = step.colorHex == hex
                    val swatchColor = hex.takeIf { it.isNotBlank() }
                        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (swatchColor != null) Modifier.background(swatchColor)
                                else Modifier.background(yeexBrandGradient())
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { step.colorHex = hex }
                            )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Optional per-stage photo ----
            val uri = step.imageUri
            if (uri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove_media),
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onPickImage,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.moment_step_add_photo), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
