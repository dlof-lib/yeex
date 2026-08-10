package com.yeex.dlof.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.yeex.dlof.BuildConfig
import com.yeex.dlof.data.model.User
import org.json.JSONObject

/**
 * "تنزيل بياناتي" — a lightweight, on-device "download your data" export.
 * Bundles the fields [com.yeex.dlof.data.repository.UserRepository] and
 * [com.yeex.dlof.data.repository.AuthRepository] actually store for this
 * account into one JSON object and hands it to the OS share sheet (no file
 * write / FileProvider needed — ACTION_SEND with EXTRA_TEXT is enough for a
 * blob this small, and it's exactly what "share/save this text" already
 * means to every app on the share sheet: Files, email, Drive, etc.).
 *
 * Deliberately doesn't include paragraphs/comments/rooms — those are public
 * content already visible on the profile, not private account data, and
 * paragraphs self-expire after 24h anyway (see database.rules.json) so a
 * point-in-time export of them would be stale within a day regardless.
 */
object DataExportUtil {

    fun buildUserDataJson(user: User): String {
        val json = JSONObject()
        json.put("uid", user.uid)
        json.put("identifier", user.identifier)
        json.put("displayName", user.displayName)
        json.put("bio", user.bio)
        json.put("accountType", user.accountType)
        json.put("language", user.language)
        json.put("verified", user.verified)
        json.put("isPrivateAccount", user.isPrivateAccount)
        json.put("commentPrivacy", user.commentPrivacy)
        json.put("tekingCount", user.tekingCount)
        json.put("tekerCount", user.tekerCount)
        json.put("createdAt", user.createdAt)
        return json.toString(2)
    }

    /** Opens the OS share sheet with [content] as plain text — the person picks where it ends up (Files, email, Drive, ...). */
    fun shareText(context: Context, chooserTitle: String, content: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }

    /**
     * Diagnostic block appended to "الإبلاغ عن مشكلة" emails — app version,
     * OS version, and device model, the three things support most often has
     * to ask a follow-up question to get anyway.
     */
    fun deviceDiagnosticsText(): String = buildString {
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}
