package com.yeex.dlof.ui.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import kotlinx.coroutines.launch

/** Full-screen comment thread for a single paragraph (opened from the comment button). */
@Composable
fun CommentsScreen(
    paragraphId: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: ParagraphRepository = ParagraphRepository(),
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(paragraphId) {
        repo.observeComments(paragraphId).collect { comments = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_comment)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.action_comment)) },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = !isSending && input.isNotBlank(),
                    onClick = {
                        val uid = authRepo.currentUid() ?: return@IconButton
                        val text = input.trim()
                        scope.launch {
                            isSending = true
                            val me = userRepo.getUser(uid)
                            repo.addComment(
                                Comment(
                                    paragraphId = paragraphId,
                                    authorId = uid,
                                    authorIdentifier = me?.identifier ?: "",
                                    text = text
                                )
                            )
                            input = ""
                            isSending = false
                        }
                    }
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.action_comment))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            items(comments, key = { it.id }) { c ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${c.authorIdentifier}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
