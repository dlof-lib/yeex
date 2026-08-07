package com.yeex.dlof.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Container
import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.ContainerRepository
import com.yeex.dlof.data.repository.RoomRepository
import com.yeex.dlof.data.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * Search box.
 *
 * Priority order, per spec ("للبحث عن حاوية تخصيص اولا اكتب @container.name[].me"):
 *  1. "@container.<name>[].me" → container lookup ([ContainerRepository]).
 *  2. Anything else → identifier search (accounts, "@handle" or bare) via
 *     [UserRepository.searchByIdentifierPrefix] AND public room-name search
 *     via [RoomRepository.searchByName], shown as two sections. Both are
 *     prefix (startAt/endAt) queries — Realtime Database has no full-text
 *     search — so results are strongest when the query starts with the
 *     target identifier/room name.
 */
@Composable
fun SearchScreen(
    containerRepo: ContainerRepository = ContainerRepository(),
    userRepo: UserRepository = UserRepository(),
    roomRepo: RoomRepository = RoomRepository(),
    onOpenContainer: (Container) -> Unit,
    onOpenUser: (User) -> Unit = {},
    onOpenRoom: (Room) -> Unit = {},
    onOpenRooms: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var containerResults by remember { mutableStateOf<List<Container>>(emptyList()) }
    var userResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var roomResults by remember { mutableStateOf<List<Room>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var isContainerQuery by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        scope.launch {
            searched = true
            val containerName = containerRepo.parseContainerQuery(query)
            isContainerQuery = containerName != null
            if (containerName != null) {
                containerResults = containerRepo.findByName(containerName)
                userResults = emptyList()
                roomResults = emptyList()
            } else {
                containerResults = emptyList()
                userResults = userRepo.searchByIdentifierPrefix(query)
                roomResults = roomRepo.searchByName(query)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                placeholder = { Text(stringResource(R.string.container_search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onOpenRooms) {
                Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.browse_rooms))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { runSearch() }) { Text(stringResource(R.string.search_hint)) }

        Spacer(Modifier.height(16.dp))

        if (searched && isContainerQuery && containerResults.isEmpty()) {
            Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.labelMedium)
        }
        if (searched && !isContainerQuery && userResults.isEmpty() && roomResults.isEmpty()) {
            Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.labelMedium)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(containerResults) { c ->
                ListItem(
                    headlineContent = { Text("${stringResource(R.string.container_label)}: ${c.name}") },
                    supportingContent = { Text("${c.roomIds.size} rooms · ${c.memberIds.size} members") },
                    modifier = Modifier.clickable { onOpenContainer(c) }
                )
            }

            if (userResults.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_users_section),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(userResults) { u ->
                    ListItem(
                        headlineContent = { Text(u.displayName.ifBlank { u.identifier }) },
                        supportingContent = { Text("@${u.identifier}") },
                        modifier = Modifier.clickable { onOpenUser(u) }
                    )
                }
            }

            if (roomResults.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_rooms_section),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(roomResults) { r ->
                    ListItem(
                        headlineContent = { Text(r.name) },
                        supportingContent = { Text(stringResource(R.string.room_members_count, r.memberCount.toString())) },
                        modifier = Modifier.clickable { onOpenRoom(r) }
                    )
                }
            }
        }
    }
}
