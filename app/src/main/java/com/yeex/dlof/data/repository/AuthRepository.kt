package com.yeex.dlof.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase
import com.yeex.dlof.data.local.LocalAccountStore
import com.yeex.dlof.data.local.SavedAccount
import com.yeex.dlof.data.model.User
import com.yeex.dlof.util.UsernameValidator
import kotlinx.coroutines.tasks.await

/**
 * Identifier + password is the only sign-in method — there is no Google or
 * GitHub sign-in in this app. Every successful login/register is also
 * remembered locally (see [LocalAccountStore]) so:
 *  - the session survives app restarts/force-closes (FirebaseAuth's own
 *    default on-device persistence already does this — nothing extra
 *    needed), and
 *  - the person can add several accounts on the same device and switch
 *    between them from the profile screen (see [switchAccount]) instead of
 *    signing out and typing credentials again every time.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usersRef get() = db.getReference("users")
    private val identifiersRef get() = db.getReference("identifiers") // identifier -> uid, for uniqueness + lookup

    /**
     * Fields a normal (non-admin) user is allowed to write to their own /users/{uid} node,
     * per database.rules.json. "verified", "verifiedReason", and "isOfficial" have
     * admin-only .write rules — writing the full User object (which always carries their
     * false/"" defaults) via setValue() therefore fails the whole multi-location write with
     * PERMISSION_DENIED, which was surfacing to users as a generic "حدث خطأ غير متوقع" on
     * every registration. Omitting them here is safe: they're not in the ".validate"
     * hasChildren(...) requirement, and getValue(User::class.java) fills in the Kotlin data
     * class defaults for any missing keys on read.
     */
    private fun User.toOwnWriteMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "identifier" to identifier,
        "displayName" to displayName,
        "displayNameLower" to displayNameLower,
        "bio" to bio,
        "profileIconUrl" to profileIconUrl,
        "externalFollowerCounts" to externalFollowerCounts,
        "tekingCount" to tekingCount,
        "tekerCount" to tekerCount,
        "createdAt" to createdAt,
        "language" to language
    )

    sealed class AuthResult {
        data class Success(val user: User) : AuthResult()
        data class Failure(val messageKey: String) : AuthResult()
    }

    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Turns a raw exception into a UI-facing error key. Previously every failure that
     * wasn't already a known key fell into "unknown" ("حدث خطأ غير متوقع") with no way
     * to tell what actually happened — this logs the real exception to Logcat (adb logcat
     * -s AuthRepository) and returns a specific key for the common Firebase Auth failure
     * types so they can be shown/localized precisely instead of hidden behind one message.
     */
    private fun mapAuthException(context: String, e: Exception): String {
        Log.e(TAG, "$context failed: ${e.javaClass.simpleName}: ${e.message}", e)
        return when (e) {
            is FirebaseAuthUserCollisionException -> "identifier_taken"
            is FirebaseAuthWeakPasswordException -> "weak_password"
            is FirebaseAuthInvalidCredentialsException -> "invalid_credentials"
            is FirebaseNetworkException -> "network_error"
            else -> e.message ?: "unknown"
        }
    }

    suspend fun register(
        identifier: String,
        password: String,
        displayName: String,
        language: String,
        context: Context? = null
    ): AuthResult {
        val validation = UsernameValidator.validate(identifier)
        if (!validation.isValid) return AuthResult.Failure(validation.errorKey ?: "invalid")

        return try {
            // Reserve the identifier first so two people can't race for the same handle.
            // (This read was previously outside the try/catch: any hiccup here — a slow
            // or dropped connection, a permission edge case — threw all the way up through
            // viewModelScope.launch with nothing to catch it, crashing the whole app instead
            // of showing an error. That's the "doesn't create an account and kicks me out
            // of the app" bug.)
            val existing = identifiersRef.child(identifier).get().await()
            if (existing.exists()) return AuthResult.Failure("identifier_taken")

            val pseudoEmail = UsernameValidator.toPseudoEmail(identifier)
            val authResult = auth.createUserWithEmailAndPassword(pseudoEmail, password).await()
            val uid = authResult.user?.uid ?: return AuthResult.Failure("unknown")

            val resolvedDisplayName = displayName.ifBlank { identifier }
            val user = User(
                uid = uid,
                identifier = identifier,
                displayName = resolvedDisplayName,
                displayNameLower = resolvedDisplayName.lowercase(),
                createdAt = System.currentTimeMillis(),
                language = language
            )
            try {
                usersRef.child(uid).setValue(user.toOwnWriteMap()).await()
                identifiersRef.child(identifier).setValue(uid).await()
            } catch (writeError: Exception) {
                // The auth account was created but the profile/identifier writes failed
                // (e.g. rules mismatch, connection dropped mid-registration). Delete the
                // orphaned auth account so a retry with the same "معرف" doesn't fail with
                // a confusing "email already in use" error.
                authResult.user?.delete()?.await()
                throw writeError
            }
            context?.let { rememberLocally(it, user, password) }
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(mapAuthException("register", e))
        }
    }

    suspend fun login(identifier: String, password: String, context: Context? = null): AuthResult {
        return try {
            val pseudoEmail = UsernameValidator.toPseudoEmail(identifier)
            val authResult = auth.signInWithEmailAndPassword(pseudoEmail, password).await()
            val uid = authResult.user?.uid ?: return AuthResult.Failure("unknown")
            val snapshot = usersRef.child(uid).get().await()
            val user = snapshot.getValue(User::class.java) ?: return AuthResult.Failure("profile_missing")
            context?.let { rememberLocally(it, user, password) }
            AuthResult.Success(user)
        } catch (e: Exception) {
            // Deliberately don't expose *why* login failed (wrong id vs wrong password vs
            // account doesn't exist) to the UI — that's a standard security practice, not
            // a bug. The real exception is still logged for debugging though.
            val key = mapAuthException("login", e)
            AuthResult.Failure(if (key == "network_error") key else "invalid_credentials")
        }
    }

    /**
     * Signs out of the *current* account only. Accounts previously
     * remembered via [rememberLocally] stay in [LocalAccountStore] so they
     * still show up in the "تبديل الحساب" switcher afterwards — logging out
     * is not the same as forgetting an account (see [forgetAccount] for that).
     */
    fun logout() = auth.signOut()

    fun currentUid(): String? = auth.currentUser?.uid

    // ---- Multi-account: remember, list, switch, forget ---------------------

    private fun rememberLocally(context: Context, user: User, password: String) {
        LocalAccountStore.remember(
            context,
            SavedAccount(
                uid = user.uid,
                identifier = user.identifier,
                displayName = user.displayName,
                profileIconUrl = user.profileIconUrl
            ),
            password
        )
    }

    /** Every account that has ever signed in on this device, most-recently-used first. */
    fun savedAccounts(context: Context): List<SavedAccount> = LocalAccountStore.list(context)

    fun canSwitchInstantly(context: Context, uid: String): Boolean =
        LocalAccountStore.hasStoredPassword(context, uid)

    /**
     * Switches to a previously-used account on this device. If its password
     * was remembered (the common case — see [LocalAccountStore]) this signs
     * the person straight in with no extra input; otherwise it fails with
     * "profile_missing" so the caller falls back to the normal login screen
     * pre-filled with that identifier.
     */
    suspend fun switchAccount(context: Context, uid: String): AuthResult {
        val account = savedAccounts(context).find { it.uid == uid }
            ?: return AuthResult.Failure("unknown")
        val password = LocalAccountStore.passwordFor(context, uid)
            ?: return AuthResult.Failure("profile_missing")
        auth.signOut()
        return login(account.identifier, password, context)
    }

    /** Removes an account from the on-device switcher entirely (not the account itself). */
    fun forgetAccount(context: Context, uid: String) = LocalAccountStore.forget(context, uid)

    /**
     * Changes the signed-in user's "معرف" (identifier) — the "تغيير المعرف"
     * action in [com.yeex.dlof.ui.profile.ProfileScreen]'s edit sheet.
     *
     * The identifier drives two other things that must move with it:
     *  1. FirebaseAuth's email (our free-tier pseudo-email, see
     *     [UsernameValidator.toPseudoEmail]) — changing it needs a *recent*
     *     sign-in, which the person may not have, so this re-authenticates
     *     with their current password first rather than surfacing a raw
     *     "requires-recent-login" failure.
     *  2. The /identifiers uniqueness map — the new handle is reserved
     *     BEFORE the old one is released (see database.rules.json: freeing
     *     an /identifiers entry is only allowed to whoever currently owns
     *     it), so an interruption partway through never leaves the account
     *     with no reserved identifier at all — worst case it temporarily
     *     holds both.
     */
    suspend fun changeIdentifier(context: Context, newIdentifier: String, currentPassword: String): AuthResult {
        val validation = UsernameValidator.validate(newIdentifier)
        if (!validation.isValid) return AuthResult.Failure(validation.errorKey ?: "invalid")

        val firebaseUser = auth.currentUser ?: return AuthResult.Failure("unknown")
        val uid = firebaseUser.uid

        return try {
            val snapshot = usersRef.child(uid).get().await()
            val current = snapshot.getValue(User::class.java) ?: return AuthResult.Failure("profile_missing")
            val oldIdentifier = current.identifier
            if (newIdentifier == oldIdentifier) return AuthResult.Success(current)

            val existing = identifiersRef.child(newIdentifier).get().await()
            if (existing.exists()) return AuthResult.Failure("identifier_taken")

            val oldPseudoEmail = UsernameValidator.toPseudoEmail(oldIdentifier)
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(oldPseudoEmail, currentPassword)
            firebaseUser.reauthenticate(credential).await()

            val newPseudoEmail = UsernameValidator.toPseudoEmail(newIdentifier)
            firebaseUser.updateEmail(newPseudoEmail).await()

            identifiersRef.child(newIdentifier).setValue(uid).await()
            usersRef.child(uid).child("identifier").setValue(newIdentifier).await()
            identifiersRef.child(oldIdentifier).removeValue().await()

            val updated = current.copy(identifier = newIdentifier)
            rememberLocally(context, updated, currentPassword)
            AuthResult.Success(updated)
        } catch (e: Exception) {
            AuthResult.Failure(mapAuthException("changeIdentifier", e))
        }
    }
}
