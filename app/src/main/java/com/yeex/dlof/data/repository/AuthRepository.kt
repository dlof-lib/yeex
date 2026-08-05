package com.yeex.dlof.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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
}
