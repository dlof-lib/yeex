package com.yeex.dlof.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.yeex.dlof.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The block list behind "الحسابات المحظورة" in the Settings & Privacy
 * screen. Stored at /blocks/{uid}/{targetUid} = serverTimestamp rather than
 * as a child of /users/{uid} — the whole /users node is publicly readable
 * (see database.rules.json), so nesting it there would make everyone's
 * block list public too. /blocks/{uid} is readable/writable only by that
 * uid.
 *
 * Blocking has no visible enforcement elsewhere in the app yet (the feed
 * and comments don't filter by it) — this repository is the data layer a
 * future "hide content from/hide me from blocked accounts" pass would read
 * from, and today it's still useful on its own as a private "people I don't
 * want to see" list the person controls from Settings.
 */
class BlockRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val userRepo: UserRepository = UserRepository()
) {
    private fun blocksRef(uid: String) = db.getReference("blocks").child(uid)

    suspend fun blockUser(myUid: String, targetUid: String) {
        if (myUid == targetUid) return
        blocksRef(myUid).child(targetUid).setValue(System.currentTimeMillis()).await()
    }

    suspend fun unblockUser(myUid: String, targetUid: String) {
        blocksRef(myUid).child(targetUid).removeValue().await()
    }

    suspend fun isBlocked(myUid: String, targetUid: String): Boolean =
        blocksRef(myUid).child(targetUid).get().await().exists()

    /**
     * One-shot fetch of every uid [myUid] has blocked, used by
     * [com.yeex.dlof.ui.feed.FeedViewModel] and
     * [com.yeex.dlof.ui.comments.CommentsSheet] to filter blocked authors'
     * content out of the feed/comments — same one-shot-per-session trade-off
     * FeedViewModel already makes for the follow graph (see its
     * followingUids doc comment): blocking rarely changes mid-session, and a
     * live listener here would add a permanent extra connection to every
     * feed/comments load for a list that's usually empty.
     */
    suspend fun blockedUidSet(myUid: String): Set<String> =
        blocksRef(myUid).get().await().children.mapNotNull { it.key }.toSet()

    /** Every uid currently blocked by [myUid]. */
    private fun observeBlockedUids(myUid: String): Flow<List<String>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.key })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        blocksRef(myUid).addValueEventListener(listener)
        awaitClose { blocksRef(myUid).removeEventListener(listener) }
    }

    /** Every uid currently blocked by [myUid], resolved to full [User] profiles. */
    fun observeBlockedUsers(myUid: String): Flow<List<User>> =
        observeBlockedUids(myUid).map { uids ->
            uids.mapNotNull { uid -> runCatching { userRepo.getUser(uid) }.getOrNull() }
        }

    /**
     * Looks up a "معرف" via the public /identifiers map and blocks that
     * account. Returns null on success, or an error key on failure
     * ("not_found" | "self" | "already_blocked").
     */
    suspend fun blockByIdentifier(myUid: String, identifier: String): String? {
        val trimmed = identifier.trim().removePrefix("@")
        if (trimmed.isBlank()) return "not_found"
        val snapshot = db.getReference("identifiers").child(trimmed).get().await()
        val targetUid = snapshot.getValue(String::class.java) ?: return "not_found"
        if (targetUid == myUid) return "self"
        if (isBlocked(myUid, targetUid)) return "already_blocked"
        blockUser(myUid, targetUid)
        return null
    }
}
