package com.yeex.dlof.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.RoomRepository
import kotlinx.coroutines.launch

@Composable
fun CreateRoomScreen(
    authRepo: AuthRepository = AuthRepository(),
    repo: RoomRepository = RoomRepository(),
    onCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var interests by remember { mutableStateOf("") }
    var socialLink by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.create_room), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.room_label)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(bio, { bio = it }, label = { Text(stringResource(R.string.room_bio)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            interests,
            { interests = it },
            label = { Text(stringResource(R.string.room_interests_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(socialLink, { socialLink = it }, label = { Text(stringResource(R.string.room_social_link_hint)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.room_phone_hint)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (isPublic) stringResource(R.string.room_public) else stringResource(R.string.room_private))
            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    val uid = authRepo.currentUid() ?: return@launch
                    val id = repo.createRoom(
                        Room(
                            name = name,
                            bio = bio,
                            isPublic = isPublic,
                            interests = interests.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                            socialLinks = if (socialLink.isNotBlank()) mapOf("link" to socialLink) else emptyMap(),
                            phone = phone
                        ),
                        uid
                    )
                    onCreated(id)
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.create_room)) }
    }
}
