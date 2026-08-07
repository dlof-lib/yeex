package com.yeex.dlof.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.components.TekButton
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.MediaBase64
import kotlinx.coroutines.launch

/**
 * Professional profile screen: gradient banner header with an overlapping
 * avatar, verified badge, stat pills for Teking/Teker, a prominent Tek
 * button for other people's profiles, and — on the signed-in user's own
 * profile — quick access to editing, language, verification, and
 * signing out (see [onLogout]) so switching to a different "معرف" is a tap
 * away without leaving this screen.
 */
@Composable
fun ProfileScreen(
    targetUid: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
    onRequestVerification: () -> Unit,
    onLogout: () -> Unit = {}
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()
    val isMe = myUid == targetUid

    LaunchedEffect(targetUid) {
        if (myUid != null && !isMe) isFollowing = userRepo.isTeking(myUid, targetUid)
        launch {
            userRepo.observeUser(targetUid).collect { user = it }
        }
        paragraphRepo.observeParagraphs(null).collect { all ->
            latest = all.filter { it.authorId == targetUid }
                .sortedByDescending { it.createdAt }
                .take(5)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_profile)) },
            actions = {
                if (isMe && user != null) {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_profile))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.switch_account))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logout)) },
                                leadingIcon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showLogoutConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        )

        user?.let { u ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                // ---- Gradient banner + overlapping avatar ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Brush.horizontalGradient(listOf(YeexNavy, YeexAccent)))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-44).dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(4.dp)
                    ) {
                        ProfileAvatar(iconBase64 = u.profileIconUrl, size = 88.dp)
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-32).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            u.displayName.ifBlank { u.identifier },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (u.verified) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = stringResource(R.string.verified_badge),
                                tint = YeexCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        "@${u.identifier}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (u.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(u.bio, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatPill(count = u.tekingCount, label = stringResource(R.string.label_teking))
                        StatPill(count = u.tekerCount, label = stringResource(R.string.action_teker))
                    }

                    Spacer(Modifier.height(16.dp))
                    if (!isMe && myUid != null) {
                        TekButton(isFollowing = isFollowing) {
                            scope.launch { isFollowing = userRepo.toggleTek(myUid, targetUid) }
                        }
                    }
                    if (isMe && !u.verified) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestVerification,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexCrimson, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.request_verification))
                        }
                    }

                    if (isMe) {
                        Spacer(Modifier.height(20.dp))
                        LanguageSwitcher(userRepo = userRepo, myUid = myUid, scope = scope)
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.latest_paragraphs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))

                    if (latest.isEmpty()) {
                        Text(
                            stringResource(R.string.no_paragraphs_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    latest.forEach { p -> LatestParagraphRow(p) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showEditSheet && myUid != null) {
        EditAccountSheet(
            user = user,
            userRepo = userRepo,
            uid = myUid,
            onDismiss = { showEditSheet = false }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    authRepo.logout()
                    onLogout()
                }) { Text(stringResource(R.string.logout), color = YeexCrimson) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun StatPill(count: Long, label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LatestParagraphRow(p: Paragraph) {
    val icon = when (p.type) {
        ParagraphType.IMAGE.name -> Icons.Filled.ImageIcon
        ParagraphType.VIDEO.name -> Icons.Filled.Videocam
        else -> Icons.Filled.TextFields
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                p.text.ifBlank { "[${p.type}]" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (p.commentCount > 0) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Text("${p.commentCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileAvatar(iconBase64: String, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(iconBase64) {
        if (iconBase64.isNotBlank()) runCatching { MediaBase64.decodeToBitmap(iconBase64) }.getOrNull() else null
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Brush.horizontalGradient(listOf(YeexNavy, YeexAccent)), CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/**
 * "Edit account" pop-up (ModalBottomSheet) — lets the user change their
 * account icon, display name, and bio without leaving the profile screen,
 * per the "الحساب أيضًا شاشة منبثقة" + "خيار تغيير أيقونة الحساب" requirements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountSheet(
    user: User?,
    userRepo: UserRepository,
    uid: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var displayName by remember { mutableStateOf(user?.displayName ?: "") }
    var bio by remember { mutableStateOf(user?.bio ?: "") }
    var pendingIconBase64 by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingIconBase64 = runCatching { MediaBase64.encodeAvatar(context.contentResolver, uri) }.getOrNull()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.edit_profile), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(iconBase64 = pendingIconBase64 ?: user?.profileIconUrl ?: "", size = 88.dp)
                IconButton(
                    onClick = { pickIcon.launch("image/*") },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.change_icon),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            TextButton(onClick = { pickIcon.launch("image/*") }) {
                Text(stringResource(R.string.change_icon))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 300) bio = it },
                label = { Text(stringResource(R.string.bio_label)) },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            runCatching {
                                userRepo.updateProfile(uid, displayName.trim(), bio.trim())
                                pendingIconBase64?.let { userRepo.updateProfileIcon(uid, it) }
                            }
                            isSaving = false
                            onDismiss()
                        }
                    },
                    enabled = !isSaving && displayName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSaving) "..." else stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Own-profile-only language picker (ar/en/es). Persists the choice locally
 * (LocaleUtil) so it survives before/after login, writes it through to
 * /users/{uid}/language for cross-device consistency, then recreates the
 * activity so MainActivity.attachBaseContext picks up the new Configuration
 * immediately — no app restart required.
 */
@Composable
private fun LanguageSwitcher(
    userRepo: UserRepository,
    myUid: String?,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(LocaleUtil.getSavedLanguage(context)) }
    val labels = mapOf("ar" to "العربية", "en" to "English", "es" to "Español")

    Column {
        Text(stringResource(R.string.language_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row {
            LocaleUtil.SUPPORTED.forEach { code ->
                FilterChip(
                    selected = current == code,
                    onClick = {
                        if (current != code) {
                            current = code
                            LocaleUtil.saveLanguage(context, code)
                            if (myUid != null) {
                                scope.launch {
                                    runCatching { userRepo.updateLanguage(myUid, code) }
                                }
                            }
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                    label = { Text(labels[code] ?: code) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
