package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.model.Paragraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

enum class Reaction { LIKE, DISLIKE }

class ParagraphRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val paragraphsRef get() = db.getReference("paragraphs")
    private val commentsRef get() = db.getReference("comments")
    private val likesRef get() = db.getReference("paragraphLikes")

    suspend fun publish(paragraph: Paragraph): String {
        val id = paragraphsRef.push().key ?: error("no id")
        val now = System.currentTimeMillis()
        val toSave = paragraph.copy(
            id = id,
            createdAt = now,
            expiresAt = now + TWENTY_FOUR_HOURS_MS
        )
        paragraphsRef.child(id).setValue(toSave).await()
        return id
    }

    suspend fun getParagraph(id: String): Paragraph? =
        paragraphsRef.child(id).get().await().getValue(Paragraph::class.java)

    /** Live feed: either the global public feed (roomId == "") or a specific room. */
    fun observeParagraphs(roomId: String? = null): Flow<List<Paragraph>> = callbackFlow {
        val query = if (roomId != null) {
            paragraphsRef.orderByChild("roomId").equalTo(roomId)
        } else {
            paragraphsRef.orderByChild("createdAt")
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val all = snapshot.children.mapNotNull { it.getValue(Paragraph::class.java) }
                val (expired, live) = all.partition { it.expiresAt <= now }
                trySend(live)
                if (expired.isNotEmpty()) {
                    // Fire-and-forget: actually remove expired rows from Firebase
                    // (not just hide them locally). See purgeExpired() doc.
                    CoroutineScope(Dispatchers.IO).launch { purgeExpired(expired) }
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    /**
     * Per-user reaction (LIKE/DISLIKE), mutually exclusive — matches the
     * "إعجاب / لم يعجبني" spec. Stored at /paragraphLikes/{paragraphId}/{uid}
     * as a single string value (see database.rules.json), so switching from
     * like -> dislike (or clearing it) is one write, not two.
     */
    suspend fun getReaction(paragraphId: String, uid: String): Reaction? {
        val raw = likesRef.child(paragraphId).child(uid).get().await().getValue(String::class.java)
        return raw?.let { runCatching { Reaction.valueOf(it) }.getOrNull() }
    }

    fun observeReaction(paragraphId: String, uid: String): Flow<Reaction?> = callbackFlow {
        val ref = likesRef.child(paragraphId).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val raw = snapshot.getValue(String::class.java)
                trySend(raw?.let { runCatching { Reaction.valueOf(it) }.getOrNull() })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Tapping the like button when already liked clears the reaction; same for dislike. */
    suspend fun toggleLike(paragraphId: String, uid: String) =
        setReaction(paragraphId, uid, if (getReaction(paragraphId, uid) == Reaction.LIKE) null else Reaction.LIKE)

    suspend fun toggleDislike(paragraphId: String, uid: String) =
        setReaction(paragraphId, uid, if (getReaction(paragraphId, uid) == Reaction.DISLIKE) null else Reaction.DISLIKE)

    private suspend fun setReaction(paragraphId: String, uid: String, newReaction: Reaction?) {
        val old = getReaction(paragraphId, uid)
        if (old == newReaction) return
        if (newReaction == null) likesRef.child(paragraphId).child(uid).removeValue().await()
        else likesRef.child(paragraphId).child(uid).setValue(newReaction.name).await()

        if (old == Reaction.LIKE) bump(paragraphId, "likeCount", -1)
        if (old == Reaction.DISLIKE) bump(paragraphId, "dislikeCount", -1)
        if (newReaction == Reaction.LIKE) bump(paragraphId, "likeCount", 1)
        if (newReaction == Reaction.DISLIKE) bump(paragraphId, "dislikeCount", 1)
    }

    fun observeComments(paragraphId: String): Flow<List<Comment>> = callbackFlow {
        val query = commentsRef.child(paragraphId).orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.getValue(Comment::class.java) })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun addComment(comment: Comment) {
        val id = commentsRef.child(comment.paragraphId).push().key ?: error("no id")
        commentsRef.child(comment.paragraphId).child(id)
            .setValue(comment.copy(id = id, createdAt = System.currentTimeMillis())).await()
        bump(comment.paragraphId, "commentCount", 1)
    }

    /**
     * Reposts an existing paragraph into a room, with an optional added comment.
     * The repost is published as a new paragraph *owned by the reposting user*
     * ([reposterUid]/[reposterIdentifier]) — not the original author — since
     * database.rules.json requires authorId === auth.uid on create.
     */
    suspend fun repostIntoRoom(
        original: Paragraph,
        roomId: String,
        comment: String,
        reposterUid: String,
        reposterIdentifier: String,
        reposterVerified: Boolean
    ): String {
        val reposted = original.copy(
            id = "",
            authorId = reposterUid,
            authorIdentifier = reposterIdentifier,
            authorVerified = reposterVerified,
            repostOfId = original.id,
            repostComment = comment,
            roomId = roomId,
            likeCount = 0,
            dislikeCount = 0,
            commentCount = 0,
            repostCount = 0
        )
        val newId = publish(reposted)
        bump(original.id, "repostCount", 1)
        return newId
    }

    /**
     * Best-effort distributed cleanup: since the free (Spark) Firebase plan has
     * no scheduled Cloud Functions to sweep expired paragraphs server-side, any
     * signed-in client that observes an already-expired paragraph deletes it.
     * Safe under database.rules.json, which permits deletion of a paragraph by
     * *any* authenticated user once data.child('expiresAt') is in the past —
     * see the $paragraphId ".write" rule. Duplicate deletes are harmless.
     */
    suspend fun purgeExpired(expired: List<Paragraph>) {
        for (p in expired) {
            runCatching { paragraphsRef.child(p.id).removeValue().await() }
        }
    }

    private suspend fun bump(paragraphId: String, field: String, delta: Int) {
        val ref = paragraphsRef.child(paragraphId).child(field)
        val current = ref.get().await().getValue(Long::class.java) ?: 0L
        ref.setValue((current + delta).coerceAtLeast(0)).await()
    }
}
