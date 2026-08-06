package com.yeex.dlof.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.yeex.dlof.R
import com.yeex.dlof.data.model.User
import com.yeex.dlof.util.UsernameValidator
import kotlinx.coroutines.tasks.await

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
     * every registration and every first Google/GitHub sign-in. Omitting them here is safe:
     * they're not in the ".validate" hasChildren(...) requirement, and getValue(User::class.java)
     * fills in the Kotlin data class defaults for any missing keys on read.
     */
    private fun User.toOwnWriteMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "identifier" to identifier,
        "displayName" to displayName,
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

    suspend fun register(identifier: String, password: String, displayName: String, language: String): AuthResult {
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

            val user = User(
                uid = uid,
                identifier = identifier,
                displayName = displayName.ifBlank { identifier },
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
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(mapAuthException("register", e))
        }
    }

    suspend fun login(identifier: String, password: String): AuthResult {
        return try {
            val pseudoEmail = UsernameValidator.toPseudoEmail(identifier)
            val authResult = auth.signInWithEmailAndPassword(pseudoEmail, password).await()
            val uid = authResult.user?.uid ?: return AuthResult.Failure("unknown")
            val snapshot = usersRef.child(uid).get().await()
            val user = snapshot.getValue(User::class.java) ?: return AuthResult.Failure("profile_missing")
            AuthResult.Success(user)
        } catch (e: Exception) {
            // Deliberately don't expose *why* login failed (wrong id vs wrong password vs
            // account doesn't exist) to the UI — that's a standard security practice, not
            // a bug. The real exception is still logged for debugging though.
            val key = mapAuthException("login", e)
            AuthResult.Failure(if (key == "network_error") key else "invalid_credentials")
        }
    }

    fun logout() = auth.signOut()

    fun currentUid(): String? = auth.currentUser?.uid

    // ---- Google Sign-In (Credential Manager) ----------------------------

    suspend fun signInWithGoogle(context: Context): AuthResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val fbUser = authResult.user ?: return AuthResult.Failure("unknown")
            ensureUserProfile(fbUser)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthResult.Failure("google_cancelled")
        } catch (e: Exception) {
            AuthResult.Failure(mapAuthException("Google sign-in", e))
        }
    }

    // ---- GitHub Sign-In (Firebase generic OAuthProvider) -----------------

    private val githubProvider by lazy {
        OAuthProvider.newBuilder("github.com").apply {
            scopes = listOf("read:user", "user:email")
        }.build()
    }

    suspend fun signInWithGithub(activity: Activity): AuthResult {
        return try {
            val pending = auth.pendingAuthResult
            val authResult = if (pending != null) pending.await()
            else auth.startActivityForSignInWithProvider(activity, githubProvider).await()
            val fbUser = authResult.user ?: return AuthResult.Failure("unknown")
            ensureUserProfile(fbUser)
        } catch (e: Exception) {
            AuthResult.Failure(mapAuthException("GitHub sign-in", e))
        }
    }

    // ---- Shared: first-time social sign-in needs a /users/{uid} profile --

    private suspend fun ensureUserProfile(fbUser: FirebaseUser): AuthResult {
        val existingSnap = usersRef.child(fbUser.uid).get().await()
        if (existingSnap.exists()) {
            val user = existingSnap.getValue(User::class.java) ?: return AuthResult.Failure("profile_missing")
            return AuthResult.Success(user)
        }

        val seed = UsernameValidator.sanitizeForAuto(
            fbUser.displayName ?: fbUser.email?.substringBefore("@") ?: "yeexuser"
        )
        var candidate = seed
        var suffix = 0
        while (identifiersRef.child(candidate).get().await().exists()) {
            suffix++
            candidate = "$seed$suffix".take(UsernameValidator.MAX_LENGTH)
        }

        val user = User(
            uid = fbUser.uid,
            identifier = candidate,
            displayName = fbUser.displayName ?: candidate,
            createdAt = System.currentTimeMillis(),
            language = "ar"
        )
        usersRef.child(fbUser.uid).setValue(user.toOwnWriteMap()).await()
        identifiersRef.child(candidate).setValue(fbUser.uid).await()
        return AuthResult.Success(user)
    }
}
