package com.yeex.dlof.ui.repost

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * "إعادة نشر فقرة داخل غرفهم مع تعليق" — lets the current user pick one of
 * their own rooms (owned or joined, via RoomRepository.listMyRooms) and
 * repost [paragraphId] into it with an optional added comment.
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
    var myRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val uid = authRepo.currentUid()

    LaunchedEffect(uid) {
        if (uid != null) myRooms = roomRepo.listMyRooms(uid)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.action_repost)) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (myRooms.isEmpty()) {
                Text(stringResource(R.string.repost_no_rooms))
            } else {
                Text(stringResource(R.string.repost_pick_room), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(myRooms, key = { it.id }) { room ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedRoomId == room.id,
                                    onClick = { selectedRoomId = room.id }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedRoomId == room.id, onClick = { selectedRoomId = room.id })
                            Spacer(Modifier.width(8.dp))
                            Text(room.name)
                        }
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.action_comment)) },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !isPosting && selectedRoomId != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val roomId = selectedRoomId ?: return@Button
                        val myUid = uid ?: return@Button
                        scope.launch {
                            isPosting = true
                            error = null
                            try {
                                val original = paragraphRepo.getParagraph(paragraphId)
                                if (original == null) {
                                    error = "الفقرة الأصلية لم تعد متاحة"
                                } else {
                                    val me = userRepo.getUser(myUid)
                                    paragraphRepo.repostIntoRoom(
                                        original = original,
                                        roomId = roomId,
                                        comment = comment,
                                        reposterUid = myUid,
                                        reposterIdentifier = me?.identifier ?: "",
                                        reposterVerified = me?.verified ?: false
                                    )
                                    onDone()
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "حدث خطأ غير متوقع"
                            } finally {
                                isPosting = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.action_repost)) }
            }
        }
    }
}
