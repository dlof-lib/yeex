package com.yeex.dlof.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.util.MediaBase64
import com.yeex.dlof.util.MediaDuration
import kotlinx.coroutines.launch

@Composable
fun CreateParagraphScreen(
    roomId: String? = null,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    repo: ParagraphRepository = ParagraphRepository(),
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri; videoUri = null
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri; imageUri = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.create_paragraph), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.feed_title)) },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(12.dp))

        Row {
            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.attach_image))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.attach_image))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { pickVideo.launch("video/*") }) {
                Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.attach_video))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.attach_video))
            }
        }

        if (imageUri != null) Text("تم اختيار صورة", modifier = Modifier.padding(top = 8.dp))
        if (videoUri != null) Text("تم اختيار فيديو (يجب أن يكون 5-10 ثوانٍ)", modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isPublishing = true
                    error = null
                    val uid = authRepo.currentUid()
                    if (uid == null) { error = "يجب تسجيل الدخول"; isPublishing = false; return@launch }

                    var type = ParagraphType.TEXT.name
                    var mediaBase64 = ""
                    var mime = ""
                    try {
                        when {
                            imageUri != null -> {
                                mediaBase64 = MediaBase64.encodeImage(context.contentResolver, imageUri!!)
                                type = ParagraphType.IMAGE.name
                                mime = "image/jpeg"
                            }
                            videoUri != null -> {
                                val durationMs = MediaDuration.getDurationMs(context, videoUri!!)
                                if (durationMs == null || durationMs < MediaDuration.MIN_VIDEO_MS || durationMs > MediaDuration.MAX_VIDEO_MS) {
                                    error = "يجب أن تكون مدة الفيديو بين 5 و10 ثوانٍ"
                                    isPublishing = false
                                    return@launch
                                }
                                val encoded = MediaBase64.encodeVideoIfSmallEnough(context.contentResolver, videoUri!!)
                                if (encoded == null) {
                                    error = "حجم الفيديو كبير جدًا"
                                    isPublishing = false
                                    return@launch
                                }
                                mediaBase64 = encoded
                                type = ParagraphType.VIDEO.name
                                mime = "video/mp4"
                            }
                        }
                        // authorIdentifier/authorVerified are denormalized onto every paragraph
                        // so ParagraphCard and the feed never need a per-post user lookup.
                        val me = userRepo.getUser(uid)
                        repo.publish(
                            Paragraph(
                                authorId = uid,
                                authorIdentifier = me?.identifier ?: "",
                                authorVerified = me?.verified ?: false,
                                type = type,
                                text = text,
                                mediaBase64 = mediaBase64,
                                mediaMimeType = mime,
                                roomId = roomId ?: ""
                            )
                        )
                        onPublished()
                    } catch (e: Exception) {
                        error = e.message ?: "خطأ غير معروف"
                    } finally {
                        isPublishing = false
                    }
                }
            },
            enabled = !isPublishing && (text.isNotBlank() || imageUri != null || videoUri != null),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isPublishing) "..." else "نشر")
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.paragraph_expires), style = MaterialTheme.typography.labelSmall)
    }
}
