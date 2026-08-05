package com.yeex.dlof.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Container
import com.yeex.dlof.data.repository.ContainerRepository
import kotlinx.coroutines.launch

/**
 * Search box. If the query matches "@container.<name>[].me" it's routed to
 * container lookup first (per spec: "اولا اكتب @container.name[].me");
 * anything else falls through to identifier/room search (left as a
 * follow-up — see README).
 */
@Composable
fun SearchScreen(
    containerRepo: ContainerRepository = ContainerRepository(),
    onOpenContainer: (Container) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var containerResults by remember { mutableStateOf<List<Container>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint)) },
            placeholder = { Text(stringResource(R.string.container_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                searched = true
                val containerName = containerRepo.parseContainerQuery(query)
                containerResults = if (containerName != null) {
                    containerRepo.findByName(containerName)
                } else {
                    emptyList()
                }
            }
        }) { Text(stringResource(R.string.search_hint)) }

        Spacer(Modifier.height(16.dp))
        if (searched && containerRepo.parseContainerQuery(query) == null) {
            Text("للبحث عن حاوية تخصيص استخدم الصيغة: @container.name[].me", style = MaterialTheme.typography.labelMedium)
        }
        containerResults.forEach { c ->
            ListItem(
                headlineContent = { Text("${stringResource(R.string.container_label)}: ${c.name}") },
                supportingContent = { Text("${c.roomIds.size} rooms · ${c.memberIds.size} members") },
                modifier = Modifier.clickable { onOpenContainer(c) }
            )
        }
    }
}
