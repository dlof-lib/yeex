package com.yeex.dlof.ui.room

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.util.RoomCategory
import com.yeex.dlof.util.RoomType
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
    var roomType by remember { mutableStateOf(RoomType.GENERAL) }
    var category by remember { mutableStateOf(RoomCategory.GENERAL) }
    var rules by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
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

        Spacer(Modifier.height(12.dp))
        // "فئة الغرفة" — topical category, used for discovery filters in
        // BrowseRoomsScreen. A dropdown rather than a chip row like room
        // type since RoomCategory has ~20 options, too many to lay out flat.
        Text(stringResource(R.string.room_category_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = categoryMenuExpanded,
            onExpandedChange = { categoryMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = RoomCategory.label(category),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                leadingIcon = { Icon(RoomCategory.icon(category), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { categoryMenuExpanded = false }
            ) {
                RoomCategory.ALL.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(RoomCategory.label(c)) },
                        leadingIcon = { Icon(RoomCategory.icon(c), contentDescription = null) },
                        onClick = {
                            category = c
                            categoryMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            rules,
            { rules = it },
            label = { Text(stringResource(R.string.room_rules_label)) },
            placeholder = { Text(stringResource(R.string.room_rules_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        // Room type — TV_CHANNEL is the room-level counterpart of the
        // TV-channel business-account category (see util/BusinessCategory.kt);
        // a TV channel room additionally gets the live-stream link editor in
        // RoomScreen once created.
        Text(stringResource(R.string.room_type_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row {
            FilterChip(
                selected = roomType == RoomType.GENERAL,
                onClick = { roomType = RoomType.GENERAL },
                label = { Text(stringResource(R.string.room_type_general)) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = roomType == RoomType.TV_CHANNEL,
                onClick = { roomType = RoomType.TV_CHANNEL },
                label = { Text(stringResource(R.string.room_type_tv_channel)) }
            )
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
                            phone = phone,
                            roomType = roomType,
                            category = category,
                            rules = rules
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
