package com.yeex.dlof.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.yeex.dlof.ui.theme.YeexNavyLight
import com.yeex.dlof.ui.theme.YeexPink
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.yeexBrandGradient
import com.yeex.dlof.util.LocaleUtil
import com.yeex.dlof.util.MediaBase64
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

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
    var todayCount by remember { mutableStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showSwitchSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myUid = authRepo.currentUid()
    val isMe = myUid == targetUid

    LaunchedEffect(targetUid) {
        // Every branch here is wrapped/guarded: a plain suspend call
        // (isTeking) can throw directly, and the Firebase-backed flows
        // (observeUser/observeParagraphs) can emit an exception via
        // close(error) if the underlying listener is cancelled — either
        // would otherwise be an uncaught exception that crashes the app.
        if (myUid != null && !isMe) {
            isFollowing = runCatching { userRepo.isTeking(myUid, targetUid) }.getOrDefault(false)
        }
        launch {
            userRepo.observeUser(targetUid)
                .catch { /* keep last-known user rather than crashing */ }
                .collect { user = it }
        }
        paragraphRepo.observeParagraphs(null)
            .catch { /* keep last-known list rather than crashing */ }
            .collect { all ->
                val mine = all.filter { it.authorId == targetUid }.sortedByDescending { it.createdAt }
                latest = mine.take(10)
                todayCount = mine.size // still-live (unexpired) paragraphs — the app's "today"
            }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.nav_profile),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                // ---- Single tidy overflow menu instead of two separate icons:
                // "Edit profile", "Switch account" and "Logout" all live under
                // one MoreVert button so the bar stays clean regardless of how
                // many own-profile actions exist. ----
                if (isMe && user != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.profile_menu)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_profile)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showEditSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.switch_account)) },
                                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showSwitchSheet = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logout), color = YeexCrimson) },
                                leadingIcon = { Icon(Icons.Filled.Logout, contentDescription = null, tint = YeexCrimson) },
                                onClick = {
                                    showMenu = false
                                    showLogoutConfirm = true
                                }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        user?.let { u ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                // ---- Banner (fixed 3:1 crop — image, video-link indicator, or the
                // brand-gradient fallback) followed by the avatar in its own row
                // BELOW it, so nothing overlaps the banner or gets clipped by it ----
                BannerHeader(user = u)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(yeexBrandGradient())
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(iconBase64 = u.profileIconUrl, size = 60.dp)
                        }
                        if (u.verified) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(YeexCrimson),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.verified_badge),
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                u.displayName.ifBlank { u.identifier },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (u.verified) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = stringResource(R.string.verified_badge),
                                    tint = YeexCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            "@${u.identifier}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
                    if (u.accountType == "BUSINESS" && u.businessCategory.isNotBlank()) {
                        Text(
                            com.yeex.dlof.util.BusinessCategory.label(u.businessCategory),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    if (u.bio.isNotBlank()) {
                        Text(u.bio, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(14.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill(count = u.tekerCount, topLabel = "Teker", bottomLabel = stringResource(R.string.action_teker))
                        StatPill(count = u.tekingCount, topLabel = "Teking", bottomLabel = stringResource(R.string.label_teking))
                        StatPill(count = todayCount.toLong(), topLabel = stringResource(R.string.latest_paragraphs_short), bottomLabel = stringResource(R.string.today_label))
                    }

                    Spacer(Modifier.height(14.dp))
                    if (!isMe && myUid != null) {
                        TekButton(isFollowing = isFollowing) {
                            scope.launch { isFollowing = userRepo.toggleTek(myUid, targetUid) }
                        }
                    }
                    if (isMe && !u.verified) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestVerification,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = YeexCrimson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.request_verification))
                        }
                    }

                    if (isMe) {
                        Spacer(Modifier.height(16.dp))
                        LanguageSwitcher(userRepo = userRepo, myUid = myUid, scope = scope)
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.latest_paragraphs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (latest.isNotEmpty()) {
                            Text(
                                stringResource(R.string.view_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (latest.isEmpty()) {
                        Text(
                            stringResource(R.string.no_paragraphs_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 20.dp)
                        ) {
                            items(latest, key = { it.id }) { p -> ParagraphThumbCard(p) }
                        }
                    }
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
private fun RowScope.StatPill(count: Long, topLabel: String, bottomLabel: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = YeexDarkCard,
        modifier = Modifier.weight(1f).height(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(formatStatCount(count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
            Text(topLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(bottomLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Fixed 3:1 banner strip shown above the avatar row — never overlapped by
 * it. Renders, in priority order: the mandatory-size cropped banner image
 * ([User.bannerUrl]), or — if the owner chose a video link instead — a
 * tappable "play" indicator that opens [User.bannerVideoUrl] in the
 * browser, or (neither set) the original brand-gradient fallback.
 */
@Composable
private fun BannerHeader(user: User) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val bitmap = remember(user.bannerUrl) {
        if (user.bannerUrl.isNotBlank()) MediaBase64.decodeToBitmap(user.bannerUrl) else null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(MediaBase64.BANNER_WIDTH.toFloat() / MediaBase64.BANNER_HEIGHT.toFloat())
            .let {
                if (bitmap == null) it.background(Brush.horizontalGradient(listOf(YeexNavyLight, YeexAccent, YeexPink)))
                else it
            }
    ) {
        when {
            bitmap != null -> androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            user.bannerVideoUrl.isNotBlank() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(YeexNavy)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { runCatching { uriHandler.openUri(user.bannerVideoUrl) } },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = stringResource(R.string.banner_video_link_label),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun formatStatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/**
 * A single "latest paragraph" preview card — rounded thumbnail, a "جديد"
 * (new) ribbon while the paragraph is fresh, a small type/duration chip in
 * the opposite corner, and a like/comment/age footer, matching the
 * reference grid design.
 */
@Composable
private fun ParagraphThumbCard(p: Paragraph) {
    val bitmap = remember(p.id) {
        if (p.mediaBase64.isNotEmpty() && p.type != ParagraphType.VIDEO.name) {
            MediaBase64.decodeToBitmap(p.mediaBase64)
        } else null
    }
    val isNew = System.currentTimeMillis() - p.createdAt < 60 * 60 * 1000L // fresh within the last hour
    val ageLabel = relativeAge(p.createdAt)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = YeexDarkCard,
        modifier = Modifier.width(150.dp).height(190.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                bitmap != null -> androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                p.type == ParagraphType.VIDEO.name -> Box(
                    modifier = Modifier.fillMaxSize().background(yeexBrandGradient()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(YeexAccent.copy(alpha = 0.55f), YeexDarkCard))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        p.text.ifBlank { "" },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Bottom scrim for legible footer text over media thumbnails.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            )

            if (isNew) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = YeexCrimson,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        stringResource(R.string.new_badge),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(
                    if (p.type == ParagraphType.VIDEO.name) Icons.Filled.Videocam else if (p.type == ParagraphType.IMAGE.name) Icons.Filled.ImageIcon else Icons.Filled.TextFields,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp).size(14.dp)
                )
            }

            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${p.likeCount}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("${p.commentCount}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(ageLabel, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun relativeAge(createdAt: Long): String {
    if (createdAt <= 0L) return ""
    val diffMin = (System.currentTimeMillis() - createdAt) / 60000L
    return when {
        diffMin < 1 -> "الآن"
        diffMin < 60 -> "منذ ${diffMin}د"
        diffMin < 60 * 24 -> "منذ ${diffMin / 60}س"
        else -> "منذ ${diffMin / (60 * 24)}ي"
    }
}

/**
 * "Edit account" pop-up (ModalBottomSheet) — lets the user change their
 * account icon, display name, and bio without leaving the profile screen,
 * per the "الحساب أيضًا شاشة منبثقة" + "خيار تغيير أيقونة الحساب" requirements.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    // ---- Banner: a picked-and-cropped image OR a video link, never both ----
    var pendingBannerBase64 by remember { mutableStateOf<String?>(null) }
    var useVideoBanner by remember { mutableStateOf(user?.bannerVideoUrl?.isNotBlank() == true) }
    var bannerVideoUrl by remember { mutableStateOf(user?.bannerVideoUrl ?: "") }

    // ---- Business account ----
    var isBusiness by remember { mutableStateOf(user?.accountType == "BUSINESS") }
    var businessCategory by remember { mutableStateOf(user?.businessCategory?.ifBlank { com.yeex.dlof.util.BusinessCategory.COMPANY } ?: com.yeex.dlof.util.BusinessCategory.COMPANY) }
    var businessPhone by remember { mutableStateOf(user?.businessPhone ?: "") }
    var businessEmail by remember { mutableStateOf(user?.businessEmail ?: "") }
    var businessLinksText by remember { mutableStateOf(user?.businessLinks?.values?.joinToString(", ") ?: "") }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingIconBase64 = runCatching { MediaBase64.encodeAvatar(context.contentResolver, uri) }.getOrNull()
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingBannerBase64 = runCatching { MediaBase64.encodeBanner(context.contentResolver, uri) }.getOrNull()
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
            Text(
                stringResource(R.string.avatar_size_hint, MediaBase64.AVATAR_DIMENSION, MediaBase64.AVATAR_DIMENSION),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Banner: fixed-size image by default, or a switch to use a video link instead ----
            Text(stringResource(R.string.banner_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.use_video_banner_toggle), modifier = Modifier.weight(1f))
                Switch(checked = useVideoBanner, onCheckedChange = { useVideoBanner = it })
            }
            Spacer(Modifier.height(10.dp))

            if (useVideoBanner) {
                OutlinedTextField(
                    value = bannerVideoUrl,
                    onValueChange = { bannerVideoUrl = it },
                    label = { Text(stringResource(R.string.banner_video_link_label)) },
                    placeholder = { Text(stringResource(R.string.banner_video_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val bannerBitmap = remember(pendingBannerBase64, user?.bannerUrl) {
                    val b64 = pendingBannerBase64 ?: user?.bannerUrl
                    if (!b64.isNullOrBlank()) MediaBase64.decodeToBitmap(b64) else null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(MediaBase64.BANNER_WIDTH.toFloat() / MediaBase64.BANNER_HEIGHT.toFloat())
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (bannerBitmap == null) Brush.horizontalGradient(listOf(YeexNavyLight, YeexAccent, YeexPink))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { pickBanner.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (bannerBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bannerBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.change_banner),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.banner_size_hint, MediaBase64.BANNER_WIDTH, MediaBase64.BANNER_HEIGHT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.business_account_toggle), modifier = Modifier.weight(1f))
                Switch(checked = isBusiness, onCheckedChange = { isBusiness = it })
            }

            if (isBusiness) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.business_category_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.yeex.dlof.util.BusinessCategory.ALL.forEach { category ->
                        FilterChip(
                            selected = businessCategory == category,
                            onClick = { businessCategory = category },
                            label = { Text(com.yeex.dlof.util.BusinessCategory.label(category)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = businessPhone,
                    onValueChange = { businessPhone = it },
                    label = { Text(stringResource(R.string.business_phone_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = businessEmail,
                    onValueChange = { businessEmail = it },
                    label = { Text(stringResource(R.string.business_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = businessLinksText,
                    onValueChange = { businessLinksText = it },
                    label = { Text(stringResource(R.string.business_links_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
                                if (useVideoBanner) {
                                    if (bannerVideoUrl.trim() != (user?.bannerVideoUrl ?: "")) {
                                        userRepo.updateBannerVideoUrl(uid, bannerVideoUrl.trim())
                                    }
                                } else {
                                    pendingBannerBase64?.let { userRepo.updateBannerImage(uid, it) }
                                }
                                val links = businessLinksText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .mapIndexed { i, url -> "link${i + 1}" to url }
                                    .toMap()
                                userRepo.updateBusinessAccount(
                                    uid = uid,
                                    accountType = if (isBusiness) "BUSINESS" else "PERSONAL",
                                    businessCategory = if (isBusiness) businessCategory else "",
                                    businessPhone = if (isBusiness) businessPhone.trim() else "",
                                    businessEmail = if (isBusiness) businessEmail.trim() else "",
                                    businessLinks = if (isBusiness) links else emptyMap()
                                )
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
