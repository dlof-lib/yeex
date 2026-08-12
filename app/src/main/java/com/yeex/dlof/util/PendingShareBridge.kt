package com.yeex.dlof.util

import android.net.Uri
import androidx.compose.runtime.mutableStateOf

/**
 * In-memory hand-off point between three entry points that all want to land
 * on the same "publish this" flow:
 *  - [ScreenshotWatcher] noticing a new screenshot on-device (opt-in, see
 *    SettingsPrefsStore.screenshotSuggestEnabled).
 *  - [com.yeex.dlof.MainActivity] receiving an ACTION_SEND intent from
 *    another app ("Share" -> YEEX).
 *  - The composer screens themselves (CreateParagraphScreen/CreateTopicScreen)
 *    consuming whatever is pending once the person picks "نشر كفقرة" /
 *    "نشر كموضوع" in [com.yeex.dlof.ui.share.ShareTargetSheet].
 *
 * Deliberately a plain in-memory object rather than SavedStateHandle/nav
 * arguments: the payload can be an arbitrarily-sized content Uri that
 * doesn't belong in a nav route string, and it only ever needs to survive
 * the few seconds between "content arrived" and "person tapped a choice" —
 * process death mid-share is an acceptable edge case to lose (the OS re-runs
 * the whole SEND intent on next launch anyway).
 */
object PendingShareBridge {

    enum class Source { SCREENSHOT, EXTERNAL_SHARE }

    data class PendingContent(
        val imageUri: Uri? = null,
        val text: String = "",
        val source: Source
    )

    /** Non-null while [ShareTargetSheet] should be showing a choice to the person. */
    val pending = mutableStateOf<PendingContent?>(null)

    /** Consumed by CreateParagraphScreen/CreateTopicScreen's LaunchedEffect(Unit) to seed initial state. */
    val consumedForComposer = mutableStateOf<PendingContent?>(null)

    fun offerScreenshot(uri: Uri) {
        pending.value = PendingContent(imageUri = uri, source = Source.SCREENSHOT)
    }

    fun offerExternalShare(imageUri: Uri?, text: String) {
        if (imageUri == null && text.isBlank()) return
        pending.value = PendingContent(imageUri = imageUri, text = text, source = Source.EXTERNAL_SHARE)
    }

    /** Called when the person dismisses the sheet without choosing anything. */
    fun dismiss() {
        pending.value = null
    }

    /** Called by ShareTargetSheet right before navigating to a composer route. */
    fun handOffToComposer() {
        consumedForComposer.value = pending.value
        pending.value = null
    }

    /** Called once by the composer screen after reading the hand-off, so a later recomposition/back-nav doesn't re-apply it. */
    fun clearComposerHandoff() {
        consumedForComposer.value = null
    }
}
