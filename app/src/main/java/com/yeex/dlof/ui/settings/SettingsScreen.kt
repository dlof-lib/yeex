package com.yeex.dlof.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeex.dlof.R
import com.yeex.dlof.data.model.User
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.BlockRepository
import com.yeex.dlof.data.repository.UserRepository
import com.yeex.dlof.ui.auth.authErrorStringRes
import com.yeex.dlof.ui.components.UserAvatar
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.util.CacheUtil
import com.yeex.dlof.util.DataExportUtil
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.MutedWordsStore
import com.yeex.dlof.util.SettingsPrefsStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * "الإعدادات والخصوصية" — the person's central control panel, reached from
 * [com.yeex.dlof.ui.profile.ProfileScreen]'s overflow menu. Organized into
 * the sections a professional social app settings screen normally has:
 * account, privacy, notifications, appearance/language, about/support, and
 * a destructive "account management" zone at the bottom — instead of
 * scattering these controls across the profile header the way the language
 * switcher used to be the only such control.
 *
 * Everything here writes through immediately (no separate "حفظ" step) —
 * each row/switch/dialog persists on its own, mirroring how the rest of the
 * app's edit flows behave (e.g. [SettingsPrefsStore], [LocaleUtil]).
 */
@Composable
fun SettingsScreen(
    authRepo: AuthRepository = AuthRepository(),
    userRepo: UserRepository = UserRepository(),
    blockRepo: BlockRepository = BlockRepository(),
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAccountDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()

    var user by remember { mutableStateOf<User?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showLinkCard by remember { mutableStateOf(false) }
    var showBlockedAccounts by remember { mutableStateOf(false) }
    var showCommentPrivacy by remember { mutableStateOf(false) }
    var showMutedWords by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showReportProblem by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheSizeLabel by remember { mutableStateOf("…") }
    var isClearingCache by remember { mutableStateOf(false) }

    var notifyPrefs by remember { mutableStateOf(SettingsPrefsStore.getNotificationPrefs(context)) }
    val themeMode by SettingsPrefsStore.themeMode
    val autoplayVideos by SettingsPrefsStore.autoplayVideos
    val textScale by SettingsPrefsStore.textScale
    val screenshotSuggestEnabled by SettingsPrefsStore.screenshotSuggestEnabled
    val downloadDataChooserTitle = stringResource(R.string.download_my_data)

    LaunchedEffect(Unit) {
        cacheSizeLabel = CacheUtil.currentSizeLabel(context)
    }

    LaunchedEffect(myUid) {
        if (myUid != null) {
            userRepo.observeUser(myUid)
                .catch { /* keep last-known user rather than crashing */ }
                .collect { user = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // ---- Account ----
            SettingsSectionHeader(stringResource(R.string.section_account))
            SettingsRow(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.edit_profile),
                subtitle = stringResource(R.string.settings_edit_profile_desc),
                onClick = onEditProfile
            )
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.settings_change_password),
                subtitle = stringResource(R.string.settings_change_password_desc),
                onClick = { showChangePassword = true }
            )
            SettingsRow(
                icon = Icons.Filled.CreditCard,
                title = stringResource(R.string.subscription_manage_card),
                subtitle = if (user?.linkedCardLast4?.isNotBlank() == true)
                    "${user?.linkedCardBrand} •••• ${user?.linkedCardLast4}"
                else stringResource(R.string.subscription_card_none),
                onClick = { showLinkCard = true }
            )

            // ---- Privacy ----
            SettingsSectionHeader(stringResource(R.string.section_privacy))
            SettingsSwitchRow(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.settings_private_account),
                subtitle = stringResource(R.string.settings_private_account_desc),
                checked = user?.isPrivateAccount == true,
                onCheckedChange = { checked ->
                    val uid = myUid ?: return@SettingsSwitchRow
                    user = user?.copy(isPrivateAccount = checked)
                    scope.launch { runCatching { userRepo.updatePrivacy(uid, checked) } }
                }
            )
            SettingsRow(
                icon = Icons.Filled.Comment,
                title = stringResource(R.string.settings_comment_privacy),
                subtitle = commentPrivacyLabel(user?.commentPrivacy ?: "everyone"),
                onClick = { showCommentPrivacy = true }
            )
            SettingsRow(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.settings_blocked_accounts),
                subtitle = stringResource(R.string.settings_blocked_accounts_desc),
                onClick = { showBlockedAccounts = true }
            )
            SettingsRow(
                icon = Icons.Filled.SpeakerNotesOff,
                title = stringResource(R.string.settings_muted_words),
                subtitle = stringResource(R.string.settings_muted_words_desc),
                onClick = { showMutedWords = true }
            )

            // ---- Notifications ----
            SettingsSectionHeader(stringResource(R.string.section_notifications))
            SettingsSwitchRow(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.notify_likes),
                subtitle = stringResource(R.string.notify_likes_desc),
                checked = notifyPrefs.likes,
                onCheckedChange = {
                    notifyPrefs = notifyPrefs.copy(likes = it)
                    SettingsPrefsStore.setNotificationPref(context, SettingsPrefsStore.NOTIFY_LIKES_KEY, it)
                }
            )
            SettingsSwitchRow(
                icon = Icons.Filled.Comment,
                title = stringResource(R.string.notify_comments),
                subtitle = stringResource(R.string.notify_comments_desc),
                checked = notifyPrefs.comments,
                onCheckedChange = {
                    notifyPrefs = notifyPrefs.copy(comments = it)
                    SettingsPrefsStore.setNotificationPref(context, SettingsPrefsStore.NOTIFY_COMMENTS_KEY, it)
                }
            )
            SettingsSwitchRow(
                icon = Icons.Filled.PersonAdd,
                title = stringResource(R.string.notify_tekers),
                subtitle = stringResource(R.string.notify_tekers_desc),
                checked = notifyPrefs.tekers,
                onCheckedChange = {
                    notifyPrefs = notifyPrefs.copy(tekers = it)
                    SettingsPrefsStore.setNotificationPref(context, SettingsPrefsStore.NOTIFY_TEKERS_KEY, it)
                }
            )
            SettingsSwitchRow(
                icon = Icons.Filled.MeetingRoom,
                title = stringResource(R.string.notify_rooms),
                subtitle = stringResource(R.string.notify_rooms_desc),
                checked = notifyPrefs.rooms,
                onCheckedChange = {
                    notifyPrefs = notifyPrefs.copy(rooms = it)
                    SettingsPrefsStore.setNotificationPref(context, SettingsPrefsStore.NOTIFY_ROOMS_KEY, it)
                }
            )

            // ---- Media & data ----
            SettingsSectionHeader(stringResource(R.string.section_media_data))
            SettingsSwitchRow(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.autoplay_videos),
                subtitle = stringResource(R.string.autoplay_videos_desc),
                checked = autoplayVideos,
                onCheckedChange = { SettingsPrefsStore.setAutoplayVideos(context, it) }
            )
            SettingsSwitchRow(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(R.string.settings_screenshot_suggest),
                subtitle = stringResource(R.string.settings_screenshot_suggest_desc),
                checked = screenshotSuggestEnabled,
                onCheckedChange = { SettingsPrefsStore.setScreenshotSuggestEnabled(context, it) }
            )
            SettingsRow(
                icon = Icons.Filled.CleaningServices,
                title = stringResource(R.string.clear_cache),
                subtitle = stringResource(R.string.clear_cache_size, cacheSizeLabel),
                onClick = { showClearCacheConfirm = true }
            )
            SettingsRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.download_my_data),
                subtitle = stringResource(R.string.download_my_data_desc),
                onClick = {
                    val u = user ?: return@SettingsRow
                    val json = DataExportUtil.buildUserDataJson(u)
                    DataExportUtil.shareText(context, downloadDataChooserTitle, json)
                }
            )

            // ---- Accessibility ----
            SettingsSectionHeader(stringResource(R.string.section_accessibility))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FormatSize, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.text_size_label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                val sizeOptions = listOf(
                    SettingsPrefsStore.TEXT_SCALE_SMALL to stringResource(R.string.text_size_small),
                    SettingsPrefsStore.TEXT_SCALE_MEDIUM to stringResource(R.string.text_size_medium),
                    SettingsPrefsStore.TEXT_SCALE_LARGE to stringResource(R.string.text_size_large)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sizeOptions.forEachIndexed { index, (scale, label) ->
                        SegmentedButton(
                            selected = textScale == scale,
                            onClick = { SettingsPrefsStore.setTextScale(context, scale) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sizeOptions.size)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }

            // ---- Appearance & language ----
            SettingsSectionHeader(stringResource(R.string.section_appearance))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DarkMode, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.theme_label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                val options = listOf(
                    Triple(SettingsPrefsStore.THEME_SYSTEM, stringResource(R.string.theme_system), null),
                    Triple(SettingsPrefsStore.THEME_LIGHT, stringResource(R.string.theme_light), null),
                    Triple(SettingsPrefsStore.THEME_DARK, stringResource(R.string.theme_dark), null)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (mode, label, _) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { SettingsPrefsStore.setThemeMode(context, mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.language_label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                SettingsLanguagePicker(userRepo = userRepo, myUid = myUid, scope = scope)
            }

            // ---- About & support ----
            SettingsSectionHeader(stringResource(R.string.section_about))
            SettingsRow(
                icon = Icons.Filled.Email,
                title = stringResource(R.string.settings_help),
                subtitle = stringResource(R.string.settings_help_desc),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:majdsaadi10096@gmail.com"))
                    runCatching { context.startActivity(intent) }
                }
            )
            SettingsRow(
                icon = Icons.Filled.BugReport,
                title = stringResource(R.string.settings_report_problem),
                subtitle = stringResource(R.string.settings_report_problem_desc),
                onClick = { showReportProblem = true }
            )
            SettingsRow(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.settings_terms),
                onClick = { showTerms = true }
            )
            SettingsRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.settings_privacy_policy),
                onClick = { showPrivacyPolicy = true }
            )
            SettingsInfoRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.app_version_label),
                value = remember { appVersionName(context) }
            )

            // ---- Account management (danger zone) ----
            SettingsSectionHeader(stringResource(R.string.section_danger))
            SettingsRow(
                icon = Icons.Filled.Logout,
                title = stringResource(R.string.logout),
                titleColor = YeexCrimson,
                onClick = { showLogoutConfirm = true }
            )
            SettingsRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.delete_account),
                subtitle = stringResource(R.string.delete_account_desc),
                titleColor = YeexCrimson,
                onClick = { showDeleteAccount = true }
            )
        }
    }

    if (showChangePassword) {
        ChangePasswordSheet(authRepo = authRepo, onDismiss = { showChangePassword = false })
    }
    if (showLinkCard && myUid != null) {
        com.yeex.dlof.ui.subscription.LinkCardSheet(
            visible = true,
            uid = myUid,
            onDismiss = { showLinkCard = false },
            onLinked = { showLinkCard = false }
        )
    }

    if (showBlockedAccounts && myUid != null) {
        BlockedAccountsSheet(myUid = myUid, blockRepo = blockRepo, onDismiss = { showBlockedAccounts = false })
    }

    if (showCommentPrivacy && user != null && myUid != null) {
        CommentPrivacyDialog(
            current = user?.commentPrivacy ?: "everyone",
            onDismiss = { showCommentPrivacy = false },
            onSelect = { value ->
                showCommentPrivacy = false
                user = user?.copy(commentPrivacy = value)
                scope.launch { runCatching { userRepo.updateCommentPrivacy(myUid, value) } }
            }
        )
    }

    if (showTerms) {
        LegalTextDialog(
            title = stringResource(R.string.settings_terms),
            body = stringResource(R.string.terms_body),
            onDismiss = { showTerms = false }
        )
    }

    if (showPrivacyPolicy) {
        LegalTextDialog(
            title = stringResource(R.string.settings_privacy_policy),
            body = stringResource(R.string.privacy_policy_body),
            onDismiss = { showPrivacyPolicy = false }
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

    if (showDeleteAccount) {
        DeleteAccountDialog(
            authRepo = authRepo,
            onDismiss = { showDeleteAccount = false },
            onDeleted = {
                showDeleteAccount = false
                onAccountDeleted()
            }
        )
    }

    if (showMutedWords) {
        MutedWordsSheet(onDismiss = { showMutedWords = false })
    }

    if (showClearCacheConfirm) {
        val clearedLabel = stringResource(R.string.clear_cache_done)
        AlertDialog(
            onDismissRequest = { if (!isClearingCache) showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.clear_cache_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isClearingCache = true
                            CacheUtil.clear(context)
                            cacheSizeLabel = CacheUtil.currentSizeLabel(context)
                            isClearingCache = false
                            showClearCacheConfirm = false
                            android.widget.Toast.makeText(context, clearedLabel, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isClearingCache
                ) { Text(stringResource(R.string.clear_cache)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }, enabled = !isClearingCache) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showReportProblem) {
        ReportProblemDialog(
            userIdentifier = user?.identifier ?: "",
            onDismiss = { showReportProblem = false }
        )
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    pInfo.versionName ?: "-"
}.getOrDefault("-")

// ---------------------------------------------------------------------------
// Reusable rows
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = YeexAccent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (titleColor != Color.Unspecified) titleColor else YeexAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = titleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YeexAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun commentPrivacyLabel(value: String): String = when (value) {
    "tekers" -> stringResource(R.string.comment_privacy_tekers)
    "no_one" -> stringResource(R.string.comment_privacy_no_one)
    else -> stringResource(R.string.comment_privacy_everyone)
}

// ---------------------------------------------------------------------------
// Language picker (mirrors ProfileScreen's own switcher so both stay in sync
// through the same LocaleUtil-backed source of truth)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsLanguagePicker(
    userRepo: UserRepository,
    myUid: String?,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(LocaleUtil.getSavedLanguage(context)) }
    val labels = mapOf("ar" to "العربية", "en" to "English", "es" to "Español")

    Row {
        LocaleUtil.SUPPORTED.forEach { code ->
            FilterChip(
                selected = current == code,
                onClick = {
                    if (current != code) {
                        current = code
                        LocaleUtil.saveLanguage(context, code)
                        if (myUid != null) {
                            scope.launch { runCatching { userRepo.updateLanguage(myUid, code) } }
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

// ---------------------------------------------------------------------------
// Change password
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(authRepo: AuthRepository, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.settings_change_password), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (success) {
                Text(
                    stringResource(R.string.settings_password_changed),
                    color = YeexAccent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; errorKey = null },
                label = { Text(stringResource(R.string.current_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; errorKey = null; mismatch = false },
                label = { Text(stringResource(R.string.new_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; mismatch = false },
                label = { Text(stringResource(R.string.confirm_new_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorKey != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(authErrorStringRes(errorKey!!)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (mismatch) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.error_password_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (newPassword != confirmPassword) {
                        mismatch = true
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        errorKey = null
                        when (val result = authRepo.changePassword(currentPassword, newPassword)) {
                            is AuthRepository.AuthResult.Success -> {
                                success = true
                                currentPassword = ""; newPassword = ""; confirmPassword = ""
                            }
                            is AuthRepository.AuthResult.Failure -> errorKey = result.messageKey
                        }
                        isSaving = false
                    }
                },
                enabled = !isSaving && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Blocked accounts
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAccountsSheet(myUid: String, blockRepo: BlockRepository, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<List<User>>(emptyList()) }
    var identifierInput by remember { mutableStateOf("") }
    var blockError by remember { mutableStateOf<String?>(null) }
    var isBlocking by remember { mutableStateOf(false) }

    LaunchedEffect(myUid) {
        blockRepo.observeBlockedUsers(myUid)
            .catch { /* keep last-known list rather than crashing */ }
            .collect { blocked = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp)) {
            Text(stringResource(R.string.settings_blocked_accounts), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.block_by_identifier_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = identifierInput,
                    onValueChange = { identifierInput = it; blockError = null },
                    label = { Text(stringResource(R.string.field_identifier)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val id = identifierInput.trim()
                        if (id.isBlank()) return@Button
                        scope.launch {
                            isBlocking = true
                            val error = blockRepo.blockByIdentifier(myUid, id)
                            blockError = error
                            if (error == null) identifierInput = ""
                            isBlocking = false
                        }
                    },
                    enabled = !isBlocking && identifierInput.isNotBlank()
                ) { Text(stringResource(R.string.btn_block)) }
            }
            if (blockError != null) {
                Spacer(Modifier.height(6.dp))
                val msg = when (blockError) {
                    "self" -> stringResource(R.string.block_error_self)
                    "already_blocked" -> stringResource(R.string.block_error_already_blocked)
                    else -> stringResource(R.string.block_error_not_found)
                }
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (blocked.isEmpty()) {
                Text(
                    stringResource(R.string.blocked_accounts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    blocked.forEach { blockedUser ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(iconBase64 = blockedUser.profileIconUrl, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    blockedUser.displayName.ifBlank { blockedUser.identifier },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@${blockedUser.identifier}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                scope.launch { runCatching { blockRepo.unblockUser(myUid, blockedUser.uid) } }
                            }) { Text(stringResource(R.string.btn_unblock), color = YeexCrimson) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Comment privacy
// ---------------------------------------------------------------------------

@Composable
private fun CommentPrivacyDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf(
        "everyone" to stringResource(R.string.comment_privacy_everyone),
        "tekers" to stringResource(R.string.comment_privacy_tekers),
        "no_one" to stringResource(R.string.comment_privacy_no_one)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_comment_privacy)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == value, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------------------------------------------------------------------------
// Legal text (Terms / Privacy Policy) — simple in-app reader
// ---------------------------------------------------------------------------

@Composable
private fun LegalTextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

// ---------------------------------------------------------------------------
// Delete account
// ---------------------------------------------------------------------------

@Composable
private fun DeleteAccountDialog(authRepo: AuthRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text(stringResource(R.string.delete_account_confirm_title), color = YeexCrimson) },
        text = {
            Column {
                Text(stringResource(R.string.delete_account_confirm_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorKey = null },
                    label = { Text(stringResource(R.string.current_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorKey != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(authErrorStringRes(errorKey!!)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isDeleting = true
                        errorKey = null
                        when (val result = authRepo.deleteAccount(context, password)) {
                            is AuthRepository.AuthResult.Success -> onDeleted()
                            is AuthRepository.AuthResult.Failure -> {
                                errorKey = result.messageKey
                                isDeleting = false
                            }
                        }
                    }
                },
                enabled = !isDeleting && password.isNotBlank()
            ) {
                Text(stringResource(R.string.delete_account_button), color = YeexCrimson)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------------------------------------------------------------------------
// Muted words
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MutedWordsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var words by remember { mutableStateOf(MutedWordsStore.getAll(context)) }
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(20.dp)) {
            Text(stringResource(R.string.settings_muted_words), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.muted_words_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.muted_words_add_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val w = input.trim()
                        if (w.isNotBlank()) {
                            MutedWordsStore.add(context, w)
                            words = MutedWordsStore.getAll(context)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.btn_add)) }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (words.isEmpty()) {
                Text(
                    stringResource(R.string.muted_words_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    words.forEach { word ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(word, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                MutedWordsStore.remove(context, word)
                                words = MutedWordsStore.getAll(context)
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_remove), tint = YeexCrimson)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Report a problem — separate from the general "help" mailto, this one
// bundles device/app diagnostics (see DataExportUtil.deviceDiagnosticsText)
// so support isn't stuck asking a follow-up question for the app version.
// ---------------------------------------------------------------------------

@Composable
private fun ReportProblemDialog(userIdentifier: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_report_problem)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.report_problem_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.report_problem_placeholder)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val body = buildString {
                        appendLine(description)
                        appendLine()
                        appendLine("— @$userIdentifier —")
                        append(DataExportUtil.deviceDiagnosticsText())
                    }
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:majdsaadi10096@gmail.com")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "yeex — الإبلاغ عن مشكلة")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                },
                enabled = description.isNotBlank()
            ) { Text(stringResource(R.string.report_problem_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
