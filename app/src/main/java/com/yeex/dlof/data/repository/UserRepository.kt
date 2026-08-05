package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.yeex.dlof.data.model.User
import kotlinx.coroutines.tasks.await

/**
 * Follow terminology: tapping "Tek" on someone else's profile makes the
 * current user a "Teker" of them (i.e. now following); the target's profile
 * shows an updated "Teking" (followers) count. Implemented with two
 * denormalized index maps so both directions are cheap to query:
 *   /tekers/{targetUid}/{followerUid}  = true   (who is teking this user -> followers list)
 *   /tekedBy/{followerUid}/{targetUid} = true   (who this user is a teker of -> following list)
 */
class UserRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usersRef get() = db.getReference("users")
    private val tekersRef get() = db.getReference("tekers")
    private val tekedByRef get() = db.getReference("tekedBy")
    private val verificationRequestsRef get() = db.getReference("verificationRequests")

    suspend fun getUser(uid: String): User? =
        usersRef.child(uid).get().await().getValue(User::class.java)

    suspend fun isTeking(currentUid: String, targetUid: String): Boolean =
        tekedByRef.child(currentUid).child(targetUid).get().await().exists()

    /** Toggles follow state; returns the new "is following" boolean. */
    suspend fun toggleTek(currentUid: String, targetUid: String): Boolean {
        val alreadyFollowing = isTeking(currentUid, targetUid)
        return if (alreadyFollowing) {
            tekedByRef.child(currentUid).child(targetUid).removeValue().await()
            tekersRef.child(targetUid).child(currentUid).removeValue().await()
            adjustCounts(currentUid, targetUid, delta = -1)
            false
        } else {
            tekedByRef.child(currentUid).child(targetUid).setValue(true).await()
            tekersRef.child(targetUid).child(currentUid).setValue(true).await()
            adjustCounts(currentUid, targetUid, delta = 1)
            true
        }
    }

    private suspend fun adjustCounts(currentUid: String, targetUid: String, delta: Int) {
        usersRef.child(targetUid).child("tekingCount").get().await().let { snap ->
            val v = (snap.getValue(Long::class.java) ?: 0L) + delta
            usersRef.child(targetUid).child("tekingCount").setValue(v.coerceAtLeast(0)).await()
        }
        usersRef.child(currentUid).child("tekerCount").get().await().let { snap ->
            val v = (snap.getValue(Long::class.java) ?: 0L) + delta
            usersRef.child(currentUid).child("tekerCount").setValue(v.coerceAtLeast(0)).await()
        }
    }

    /**
     * Submits self-reported follower counts from other platforms + an ID
     * document reference for admin review. Auto-eligibility (>20k on any
     * listed platform) just flags the request as high-priority; a human
     * admin still confirms and sets `verified = true`, since we cannot
     * verify third-party follower counts programmatically without those
     * platforms' APIs.
     */
    suspend fun submitVerificationRequest(
        uid: String,
        externalFollowerCounts: Map<String, Long>,
        note: String
    ) {
        val eligible = externalFollowerCounts.values.any { it >= 20_000 }
        val payload = mapOf(
            "uid" to uid,
            "externalFollowerCounts" to externalFollowerCounts,
            "note" to note,
            "autoEligible" to eligible,
            "status" to "pending",
            "submittedAt" to System.currentTimeMillis()
        )
        verificationRequestsRef.child(uid).setValue(payload).await()
        usersRef.child(uid).child("externalFollowerCounts").setValue(externalFollowerCounts).await()
    }
}
