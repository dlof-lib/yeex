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

class ParagraphRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val paragraphsRef get() = db.getReference("paragraphs")
    private val commentsRef get() = db.getReference("comments")

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

    suspend fun like(paragraphId: String) = bump(paragraphId, "likeCount", 1)
    suspend fun unlike(paragraphId: String) = bump(paragraphId, "likeCount", -1)
    suspend fun dislike(paragraphId: String) = bump(paragraphId, "dislikeCount", 1)

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
