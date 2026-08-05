package com.yeex.dlof.ui.verify

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * Self-reported cross-platform follower counts (spec: >20k on any platform
 * makes a user eligible). A human admin (the yeex.open team) still reviews
 * and grants the crimson checkmark from /verificationRequests — see
 * UserRepository.submitVerificationRequest for why this can't be fully
 * automated on-device.
 */
@Composable
fun VerificationRequestScreen(
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    onSubmitted: () -> Unit
) {
    var instagram by remember { mutableStateOf("") }
    var tiktok by remember { mutableStateOf("") }
    var youtube by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.request_verification), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("أدخل عدد متابعيك في المنصات الأخرى (اختياري) — أكثر من 20 ألف بأي منصة يجعلك مؤهلاً للمراجعة",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(instagram, { instagram = it }, label = { Text("Instagram") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(tiktok, { tiktok = it }, label = { Text("TikTok") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(youtube, { youtube = it }, label = { Text("YouTube") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    val uid = authRepo.currentUid() ?: return@launch
                    val counts = buildMap {
                        instagram.toLongOrNull()?.let { put("instagram", it) }
                        tiktok.toLongOrNull()?.let { put("tiktok", it) }
                        youtube.toLongOrNull()?.let { put("youtube", it) }
                    }
                    userRepo.submitVerificationRequest(uid, counts, note)
                    onSubmitted()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("إرسال الطلب") }
    }
}
