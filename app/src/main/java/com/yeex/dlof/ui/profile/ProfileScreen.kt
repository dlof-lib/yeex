package com.yeex.dlof.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.local.SavedAccount
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.ParagraphType
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.auth.authErrorStringRes
import com.yeex.dlof.ui.components.TekButton
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.MediaBase64
import kotlinx.coroutines.launch

/**
 * Professional profile screen: gradient banner header with an overlapping
 * avatar, verified badge, stat pills for Teking/Teker, a prominent Tek
 * button for other people's profiles — so browsing someone else's account
 * and paragraphs (opened via [com.yeex.dlof.ui.components.ParagraphCard]'s
 * author row or a search result) works the same way as your own — and, on
 * the signed-in user's own profile, quick access to editing, language,
 * verification, signing out (see [onLogout]), and switching between every
 * "معرف" saved on this device (see [SwitchAccountSheet]) so hopping to a
 * different account is a tap away without leaving this screen.
 */
@Composable
fun ProfileScreen(
    targetUid: String,
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    paragraphRepo: ParagraphRepository = ParagraphRepository(),
    onRequestVerification: () -> Unit,
    onLogout: () -> Unit = {},
    /** Navigate to the login screen (empty prefill) to sign in a brand-new account. */
    onAddAccount: () -> Unit = {},
    /** A saved account was switched to successfully — navigate to the feed. */
    onAccountSwitched: () -> Unit = {},
    /** The chosen saved account had no remembered password — navigate to login, prefilled. */
    onNeedAccountPassword: (identifier: String) -> Unit = {}
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<List<Paragraph>>(emptyList()) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showSwitchSheet by remember { mutableStateOf(false) }
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
                                text = { Text(stringResource(R.string.switch_account)) },
                                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showSwitchSheet = true
                                }
                            )
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
                        UserAvatar(iconBase64 = u.profileIconUrl, size = 88.dp)
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

    if (showSwitchSheet && myUid != null) {
        SwitchAccountSheet(
            authRepo = authRepo,
            currentUid = myUid,
            onDismiss = { showSwitchSheet = false },
            onAddAccount = {
                showSwitchSheet = false
                onAddAccount()
            },
            onSwitched = {
                showSwitchSheet = false
                onAccountSwitched()
            },
            onNeedPassword = { identifier ->
                showSwitchSheet = false
                onNeedAccountPassword(identifier)
            }
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
private fun RowScope.StatPill(count: Long, label: String) {
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
                UserAvatar(iconBase64 = pendingIconBase64 ?: user?.profileIconUrl ?: "", size = 88.dp)
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
 * "تبديل الحساب" pop-up: lists every account that has ever signed in on this
 * device (see [com.yeex.dlof.data.local.LocalAccountStore]) so the person can
 * hop between them without signing out first, plus an "إضافة حساب" row that
 * opens a fresh login screen.
 *
 * Tapping a saved account other than the current one calls
 * [AuthRepository.switchAccount]:
 *  - if that account's password was remembered on this device, it signs in
 *    instantly and [onSwitched] fires (the caller navigates to the feed);
 *  - otherwise [onNeedPassword] fires with the account's "معرف" so the
 *    caller can navigate to the login screen pre-filled, needing only the
 *    password once more.
 *
 * A trailing "×" on each non-active row calls [AuthRepository.forgetAccount]
 * to remove it from this device's switcher entirely (not delete the account
 * itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchAccountSheet(
    authRepo: AuthRepository,
    currentUid: String,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitched: () -> Unit,
    onNeedPassword: (identifier: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var accounts by remember { mutableStateOf(authRepo.savedAccounts(context)) }
    var switchingUid by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.switch_account),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.switch_account_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (errorKey != null) {
                Text(
                    stringResource(authErrorStringRes(errorKey!!)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            accounts.forEach { account ->
                SavedAccountRow(
                    account = account,
                    isActive = account.uid == currentUid,
                    isSwitching = switchingUid == account.uid,
                    onClick = {
                        if (account.uid == currentUid || switchingUid != null) return@SavedAccountRow
                        errorKey = null
                        switchingUid = account.uid
                        scope.launch {
                            when (val result = authRepo.switchAccount(context, account.uid)) {
                                is AuthRepository.AuthResult.Success -> onSwitched()
                                is AuthRepository.AuthResult.Failure -> {
                                    switchingUid = null
                                    if (result.messageKey == "profile_missing" || result.messageKey == "unknown") {
                                        onNeedPassword(account.identifier)
                                    } else {
                                        errorKey = result.messageKey
                                    }
                                }
                            }
                        }
                    },
                    onForget = {
                        authRepo.forgetAccount(context, account.uid)
                        accounts = authRepo.savedAccounts(context)
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onAddAccount,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(YeexAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = YeexAccent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.add_account),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SavedAccountRow(
    account: SavedAccount,
    isActive: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) YeexAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(iconBase64 = account.profileIconUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName.ifBlank { account.identifier },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "@${account.identifier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                isSwitching -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                isActive -> Icon(Icons.Filled.Check, contentDescription = null, tint = YeexAccent)
                else -> IconButton(onClick = onForget, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.forget_account),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
