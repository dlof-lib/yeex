package com.yeex.dlof.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.TekButton
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    targetUid: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
    onRequestVerification: () -> Unit
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()
    val isMe = myUid == targetUid

    LaunchedEffect(targetUid) {
        user = userRepo.getUser(targetUid)
        if (myUid != null && !isMe) isFollowing = userRepo.isTeking(myUid, targetUid)
        paragraphRepo.observeParagraphs(null).collect { all ->
            latest = all.filter { it.authorId == targetUid }
                .sortedByDescending { it.createdAt }
                .take(5)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        user?.let { u ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(u.displayName, style = MaterialTheme.typography.titleLarge)
                        if (u.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, contentDescription = stringResource(R.string.verified_badge))
                        }
                    }
                    Text("@${u.identifier}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row {
                Text("${u.tekingCount} ${stringResource(R.string.label_teking)}")
                Spacer(Modifier.width(16.dp))
                Text("${u.tekerCount} ${stringResource(R.string.action_teker)}")
            }

            Spacer(Modifier.height(12.dp))
            if (!isMe && myUid != null) {
                TekButton(isFollowing = isFollowing) {
                    scope.launch { isFollowing = userRepo.toggleTek(myUid, targetUid) }
                }
            }
            if (isMe && !u.verified) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestVerification) { Text(stringResource(R.string.request_verification)) }
            }

            Spacer(Modifier.height(20.dp))
            Text("آخر الفقرات", style = MaterialTheme.typography.titleMedium)
            latest.forEach { p ->
                Text(
                    "• " + p.text.ifBlank { "[${p.type}]" },
                    modifier = Modifier.padding(vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}
