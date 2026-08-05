package com.yeex.dlof.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
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

    sealed class AuthResult {
        data class Success(val user: User) : AuthResult()
        data class Failure(val messageKey: String) : AuthResult()
    }

    suspend fun register(identifier: String, password: String, displayName: String, language: String): AuthResult {
        val validation = UsernameValidator.validate(identifier)
        if (!validation.isValid) return AuthResult.Failure(validation.errorKey ?: "invalid")

        // Reserve the identifier first so two people can't race for the same handle.
        val existing = identifiersRef.child(identifier).get().await()
        if (existing.exists()) return AuthResult.Failure("identifier_taken")

        return try {
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
            usersRef.child(uid).setValue(user).await()
            identifiersRef.child(identifier).setValue(uid).await()
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "unknown")
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
            AuthResult.Failure("invalid_credentials")
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
            AuthResult.Failure("google_cancelled")
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "unknown")
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
            AuthResult.Failure(e.message ?: "unknown")
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
        usersRef.child(fbUser.uid).setValue(user).await()
        identifiersRef.child(candidate).setValue(fbUser.uid).await()
        return AuthResult.Success(user)
    }
}
