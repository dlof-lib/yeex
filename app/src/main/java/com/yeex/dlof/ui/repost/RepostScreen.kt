package com.yeex.dlof.ui.repost

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.yeexBrandGradient
import kotlinx.coroutines.launch

private const val REPOST_COMMENT_MAX_LEN = 120

private enum class RepostVisibility { PUBLIC, CONTAINER, ROOM_ONLY }

/**
 * "إعادة فقرة" — reposts [paragraphId] with an optional comment.
 *
 * Presented as a bottom-sheet-styled screen (dim scrim + rounded card
 * anchored to the bottom, tap-outside or "إلغاء" to dismiss) so it reads as
 * a pop-up like [com.yeex.dlof.ui.comments.CommentsSheet] and
 * [com.yeex.dlof.ui.create.CreateParagraphScreen], while still being a real
 * navigation destination (see Routes.REPOST) so no navigation-graph changes
 * are needed elsewhere.
 *
 * Visibility choices map onto the existing Room model:
 * - عام (public): reposts with roomId = "" — the general/public feed.
 * - حاوية (container): repost into any of the user's rooms (owned or joined).
 * - غرفتي فقط (my room only): repost into one of the user's own *private*
 *   rooms (Room.isPublic == false) — the lock icon in the reference design.
 */
@Composable
fun RepostScreen(
    paragraphId: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    roomRepo: RoomRepository = RoomRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = authRepo.currentUid()

    var original by remember { mutableStateOf<Paragraph?>(null) }
    var originalAuthor by remember { mutableStateOf<User?>(null) }
    var myRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var visibility by remember { mutableStateOf(RepostVisibility.PUBLIC) }
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRoomPicker by remember { mutableStateOf(false) }

    LaunchedEffect(paragraphId, uid) {
        original = runCatching { paragraphRepo.getParagraph(paragraphId) }.getOrNull()
        original?.let { p -> originalAuthor = runCatching { userRepo.getUser(p.authorId) }.getOrNull() }
        if (uid != null) myRooms = runCatching { roomRepo.listMyRooms(uid) }.getOrDefault(emptyList())
    }

    val privateRooms = myRooms.filter { !it.isPublic }
    val selectedRoomName = myRooms.firstOrNull { it.id == selectedRoomId }?.name

    fun submit() {
        val myUid = uid ?: return
        val orig = original ?: return
        val targetRoomId = when (visibility) {
            RepostVisibility.PUBLIC -> ""
            RepostVisibility.CONTAINER, RepostVisibility.ROOM_ONLY -> selectedRoomId
        }
        if (visibility != RepostVisibility.PUBLIC && targetRoomId.isNullOrBlank()) {
            error = "اختر حاوية أولًا"
            return
        }
        scope.launch {
            isPosting = true
            error = null
            try {
                val me = userRepo.getUser(myUid)
                paragraphRepo.repostIntoRoom(
                    original = orig,
                    roomId = targetRoomId.orEmpty(),
                    comment = comment,
                    reposterUid = myUid,
                    reposterIdentifier = me?.identifier ?: "",
                    reposterVerified = me?.verified ?: false
                )
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "حدث خطأ غير متوقع"
            } finally {
                isPosting = false
            }
        }
    }

    // ---- Full-screen dim scrim, tap outside the card to dismiss ----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDone() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { /* absorb clicks */ }
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.repost_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(18.dp))

                original?.let { orig ->
                    OriginalParagraphPreview(orig, originalAuthor)
                    Spacer(Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= REPOST_COMMENT_MAX_LEN) comment = it },
                    placeholder = { Text(stringResource(R.string.repost_comment_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "${comment.length}/$REPOST_COMMENT_MAX_LEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.repost_where), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))

                VisibilityOption(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.repost_room_only),
                    subtitle = stringResource(R.string.repost_room_only_hint),
                    selected = visibility == RepostVisibility.ROOM_ONLY,
                    enabled = privateRooms.isNotEmpty()
                ) {
                    visibility = RepostVisibility.ROOM_ONLY
                    selectedRoomId = privateRooms.firstOrNull()?.id
                    showRoomPicker = privateRooms.size > 1
                }
                VisibilityOption(
                    icon = Icons.Filled.Widgets,
                    title = stringResource(R.string.repost_container),
                    subtitle = selectedRoomName?.takeIf { visibility == RepostVisibility.CONTAINER }
                        ?: stringResource(R.string.repost_container_hint),
                    selected = visibility == RepostVisibility.CONTAINER,
                    enabled = myRooms.isNotEmpty()
                ) {
                    visibility = RepostVisibility.CONTAINER
                    showRoomPicker = true
                }
                VisibilityOption(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.repost_public),
                    subtitle = stringResource(R.string.repost_public_hint),
                    selected = visibility == RepostVisibility.PUBLIC,
                    enabled = true
                ) {
                    visibility = RepostVisibility.PUBLIC
                    showRoomPicker = false
                }

                if (showRoomPicker && myRooms.isNotEmpty()) {
                    val candidates = if (visibility == RepostVisibility.ROOM_ONLY) privateRooms else myRooms
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(YeexDarkCard, RoundedCornerShape(14.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        candidates.forEach { room ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selectedRoomId == room.id) { selectedRoomId = room.id; showRoomPicker = false }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRoomId == room.id,
                                    onClick = { selectedRoomId = room.id; showRoomPicker = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = YeexAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(room.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                val canSubmit = !isPosting && original != null &&
                    (visibility == RepostVisibility.PUBLIC || !selectedRoomId.isNullOrBlank())
                Button(
                    onClick = { submit() },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (canSubmit) yeexBrandGradient() else Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                ),
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Repeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.repost_publish), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDone() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun OriginalParagraphPreview(paragraph: Paragraph, author: User?) {
    Surface(shape = RoundedCornerShape(16.dp), color = YeexDarkCard, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            UserAvatar(iconBase64 = author?.profileIconUrl.orEmpty(), size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${paragraph.authorIdentifier}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (paragraph.authorVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexCrimson, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    paragraph.text.ifBlank { "[${paragraph.type}]" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VisibilityOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = if (enabled) onClick else null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = YeexAccent))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
