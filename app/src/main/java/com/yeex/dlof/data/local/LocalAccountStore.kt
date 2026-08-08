package com.yeex.dlof.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * A locally-remembered account, as shown in the "تبديل الحساب" (switch
 * account) sheet. Deliberately carries no password/token — see
 * [LocalAccountStore] for why the secret itself lives in a separate,
 * encrypted-at-rest file.
 */
data class SavedAccount(
    val uid: String,
    val identifier: String,
    val displayName: String,
    val profileIconUrl: String = "",
    val lastUsedAt: Long = 0L
)

/**
 * Remembers every account that has signed in on this device so the person
 * can:
 *  1. Stay signed in across app restarts / force-closes / device reboots —
 *     this part is actually handled by FirebaseAuth itself (it persists the
 *     session to its own on-device storage by default), this store just adds
 *     the ability to list *which* accounts are available.
 *  2. Switch between multiple accounts on this device without retyping a
 *     password every time — the encrypted file below keeps each account's
 *     password (re-used to silently call [com.yeex.dlof.data.repository.AuthRepository.login]
 *     on switch) protected by an Android Keystore-backed key.
 *
 * Two files are used on purpose:
 *  - `yeex_accounts_meta` (plain [SharedPreferences]): non-secret display
 *    info (uid/identifier/name/avatar). Safe to include in Android Auto
 *    Backup, so after a reinstall on the same device/Google account the
 *    person still *sees* their past accounts in the switcher instead of the
 *    list looking empty.
 *  - `yeex_accounts_secure` ([EncryptedSharedPreferences]): the actual
 *    passwords. Its key lives in the Android Keystore, which — by design,
 *    for security — is tied to this specific app install and is **not**
 *    restored by backup. That file is explicitly excluded from backup (see
 *    res/xml/backup_rules.xml + data_extraction_rules.xml) so a restore
 *    never tries to decrypt data with a key that no longer exists. In
 *    practice this means: after reinstalling the app, previously-used
 *    identifiers still show up for convenience, but each one needs its
 *    password entered once more before it can be switched to instantly
 *    again — the same trade-off every Android app with local biometric/PIN
 *    unlock makes, and unavoidable without storing raw passwords in the
 *    clear (which we won't do).
 */
object LocalAccountStore {
    private const val TAG = "LocalAccountStore"
    private const val META_PREFS = "yeex_accounts_meta"
    private const val SECURE_PREFS = "yeex_accounts_secure"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE_UID = "active_uid"

    private fun metaPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    /**
     * Falls back to a plain (still app-private, sandboxed) prefs file if the
     * Keystore-backed one can't be created — e.g. a handful of OEM devices
     * with a broken Keystore. Passwords just won't be remembered for instant
     * switching on those devices; identifier/password login still works
     * normally.
     */
    private fun securePrefs(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure { Log.e(TAG, "Secure prefs unavailable, falling back to no saved password", it) }
        .getOrNull()

    /** Remembers this account (metadata always, password when available) as the active one. */
    fun remember(context: Context, account: SavedAccount, password: String?) {
        val prefs = metaPrefs(context)
        val existing = readAll(prefs)
        val updated = (existing.filterNot { it.uid == account.uid } + account.copy(lastUsedAt = System.currentTimeMillis()))
            .sortedByDescending { it.lastUsedAt }
        writeAll(prefs, updated)
        prefs.edit().putString(KEY_ACTIVE_UID, account.uid).apply()

        if (password != null) {
            securePrefs(context)?.edit()?.putString(account.uid, password)?.apply()
        }
    }

    fun setActive(context: Context, uid: String) {
        metaPrefs(context).edit().putString(KEY_ACTIVE_UID, uid).apply()
    }

    fun activeUid(context: Context): String? = metaPrefs(context).getString(KEY_ACTIVE_UID, null)

    fun list(context: Context): List<SavedAccount> = readAll(metaPrefs(context)).sortedByDescending { it.lastUsedAt }

    /** The remembered password for instant switching, or null if it was never saved / unavailable on this device. */
    fun passwordFor(context: Context, uid: String): String? = securePrefs(context)?.getString(uid, null)

    fun hasStoredPassword(context: Context, uid: String): Boolean = passwordFor(context, uid) != null

    /** Removes an account from the switcher entirely (e.g. explicit "forget this account"). */
    fun forget(context: Context, uid: String) {
        val prefs = metaPrefs(context)
        writeAll(prefs, readAll(prefs).filterNot { it.uid == uid })
        securePrefs(context)?.edit()?.remove(uid)?.apply()
        if (activeUid(context) == uid) prefs.edit().remove(KEY_ACTIVE_UID).apply()
    }

    private fun readAll(prefs: SharedPreferences): List<SavedAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedAccount(
                    uid = o.getString("uid"),
                    identifier = o.getString("identifier"),
                    displayName = o.optString("displayName", o.getString("identifier")),
                    profileIconUrl = o.optString("profileIconUrl", ""),
                    lastUsedAt = o.optLong("lastUsedAt", 0L)
                )
            }
        }.getOrElse {
            Log.e(TAG, "Corrupt saved-accounts list, resetting", it)
            emptyList()
        }
    }

    private fun writeAll(prefs: SharedPreferences, accounts: List<SavedAccount>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("uid", a.uid)
                    put("identifier", a.identifier)
                    put("displayName", a.displayName)
                    put("profileIconUrl", a.profileIconUrl)
                    put("lastUsedAt", a.lastUsedAt)
                }
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }
}
