package com.yeex.dlof.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.model.Topic
import com.yeex.dlof.data.model.TopicUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * YEEX TOPICS repository. Deliberately mirrors [ParagraphRepository]'s
 * presence-node patterns for likes/views/comments (one write per unique
 * account, atomic [ServerValue.increment] counters) so the two content types
 * behave consistently — the difference is purely product-level: topics never
 * expire, and additionally support an append-only "Topic Updates" log and
 * links to attached paragraphs.
 */
class TopicRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val topicsRef get() = db.getReference("topics")
    private val viewsRef get() = db.getReference("topicViews")
    private val likesRef get() = db.getReference("topicLikes")
    private val updatesRef get() = db.getReference("topicUpdates")
    private val commentsRef get() = db.getReference("topicComments")
    private val commentLikesRef get() = db.getReference("commentLikes") // shared node, keyed by topicId same as paragraphId
    private val paragraphsRef get() = db.getReference("paragraphs")

    suspend fun publish(topic: Topic): String {
        val id = topicsRef.push().key ?: error("no id")
        val now = System.currentTimeMillis()
        val toSave = topic.copy(id = id, createdAt = now, updatedAt = now)
        topicsRef.child(id).setValue(toSave).await()
        return id
    }

    suspend fun getTopic(id: String): Topic? =
        runCatching { topicsRef.child(id).get().await().getValue(Topic::class.java) }.getOrNull()

    /**
     * NOTE: snapshot.getValue(Topic::class.java) throws (not returns null) if
     * a stored record doesn't exactly match the model — a legacy/partial
     * write, a type mismatch, etc. Every deserialization here is wrapped in
     * runCatching so one malformed topic can never crash the whole screen;
     * onCancelled also no longer closes the flow with an exception (e.g. on a
     * transient permission error) — it just sends an empty/last-known-safe
     * result so the UI shows an empty state instead of the app dying.
     */
    fun observeTopic(id: String): Flow<Topic?> = callbackFlow {
        val ref = topicsRef.child(id)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(runCatching { snapshot.getValue(Topic::class.java) }.getOrNull())
            }
            override fun onCancelled(error: DatabaseError) { trySend(null) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Live topic list — global feed (authorId/roomId both null), a single
     * author's topics (profile), or a single room's topics. Sorted newest
     * first client-side; topics never expire so, unlike
     * [ParagraphRepository.observeParagraphs], nothing is ever purged here.
     */
    fun observeTopics(authorId: String? = null, roomId: String? = null): Flow<List<Topic>> = callbackFlow {
        val query = when {
            authorId != null -> topicsRef.orderByChild("authorId").equalTo(authorId)
            roomId != null -> topicsRef.orderByChild("roomId").equalTo(roomId)
            else -> topicsRef.orderByChild("createdAt")
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = snapshot.children.mapNotNull { runCatching { it.getValue(Topic::class.java) }.getOrNull() }
                trySend(all.sortedByDescending { it.createdAt })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun delete(topicId: String) {
        topicsRef.child(topicId).removeValue().await()
    }

    // ---- Views (unique-per-account, mirrors ParagraphRepository.incrementView) ----

    suspend fun incrementView(topicId: String, viewerUid: String) {
        if (topicId.isBlank() || viewerUid.isBlank()) return
        runCatching {
            val ref = viewsRef.child(topicId).child(viewerUid)
            if (ref.get().await().exists()) return@runCatching
            ref.setValue(true).await()
            topicsRef.child(topicId).child("viewCount").setValue(ServerValue.increment(1)).await()
        }
    }

    // ---- Likes (simple toggle, unlike paragraph like/dislike pair) ----

    suspend fun getLikedByMe(topicId: String, uid: String): Boolean =
        likesRef.child(topicId).child(uid).get().await().exists()

    fun observeLikedByMe(topicId: String, uid: String): Flow<Boolean> = callbackFlow {
        val ref = likesRef.child(topicId).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun toggleLike(topicId: String, uid: String): Boolean {
        val ref = likesRef.child(topicId).child(uid)
        val alreadyLiked = ref.get().await().exists()
        return if (alreadyLiked) {
            ref.removeValue().await()
            topicsRef.child(topicId).child("likeCount").setValue(ServerValue.increment(-1)).await()
            false
        } else {
            ref.setValue(true).await()
            topicsRef.child(topicId).child("likeCount").setValue(ServerValue.increment(1)).await()
            true
        }
    }

    // ---- Topic Updates ("تحديث #1، تحديث #2 ...") ----

    fun observeUpdates(topicId: String): Flow<List<TopicUpdate>> = callbackFlow {
        val query = updatesRef.child(topicId).orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { runCatching { it.getValue(TopicUpdate::class.java) }.getOrNull() })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    /** Adds a new entry to the topic's update log and bumps [Topic.updateCount]/[Topic.updatedAt]. */
    suspend fun addUpdate(topicId: String, text: String, imageBase64: String = ""): String {
        val id = updatesRef.child(topicId).push().key ?: error("no id")
        val now = System.currentTimeMillis()
        val update = TopicUpdate(id = id, topicId = topicId, text = text, imageBase64 = imageBase64, createdAt = now)
        updatesRef.child(topicId).child(id).setValue(update).await()
        val patch = mapOf(
            "topics/$topicId/updateCount" to ServerValue.increment(1),
            "topics/$topicId/updatedAt" to now
        )
        db.reference.updateChildren(patch).await()
        return id
    }

    // ---- موضوع ↔ فقرة: attach / detach a paragraph ----

    /**
     * Attaches [paragraphId] to [topicId]: adds it to
     * [Topic.linkedParagraphIds] and sets the paragraph's own
     * [Paragraph.topicId] so the paragraph side can also show "الموضوع
     * المرتبط". Read-modify-write on the small linkedParagraphIds list is
     * safe here since only the topic's author is allowed to write it (see
     * database.rules.json).
     */
    suspend fun linkParagraph(topicId: String, paragraphId: String) {
        val topic = getTopic(topicId) ?: return
        if (paragraphId in topic.linkedParagraphIds) return
        val updated = topic.linkedParagraphIds + paragraphId
        val patch = mapOf(
            "topics/$topicId/linkedParagraphIds" to updated,
            "paragraphs/$paragraphId/topicId" to topicId
        )
        db.reference.updateChildren(patch).await()
    }

    suspend fun unlinkParagraph(topicId: String, paragraphId: String) {
        val topic = getTopic(topicId) ?: return
        val updated = topic.linkedParagraphIds.filter { it != paragraphId }
        val patch = mapOf(
            "topics/$topicId/linkedParagraphIds" to updated,
            "paragraphs/$paragraphId/topicId" to ""
        )
        db.reference.updateChildren(patch).await()
    }

    /** Resolves the currently-attached paragraphs, silently skipping any that have since expired/been purged. */
    suspend fun getLinkedParagraphs(topic: Topic): List<Paragraph> =
        topic.linkedParagraphIds.mapNotNull { id ->
            runCatching { paragraphsRef.child(id).get().await().getValue(Paragraph::class.java) }.getOrNull()
        }

    /** The signed-in user's own active (not-yet-expired) paragraphs, offered as attach candidates in the topic composer. */
    suspend fun getMyAttachableParagraphs(uid: String): List<Paragraph> {
        val now = System.currentTimeMillis()
        return paragraphsRef.orderByChild("authorId").equalTo(uid).get().await().children
            .mapNotNull { runCatching { it.getValue(Paragraph::class.java) }.getOrNull() }
            .filter { it.expiresAt > now }
            .sortedByDescending { it.createdAt }
    }

    // ---- Comments (mirrors ParagraphRepository's comment thread, on the topicComments node) ----

    fun observeComments(topicId: String): Flow<List<Comment>> = callbackFlow {
        val query = commentsRef.child(topicId).orderByChild("createdAt")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { runCatching { it.getValue(Comment::class.java) }.getOrNull() })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun addComment(comment: Comment) {
        val id = commentsRef.child(comment.paragraphId).push().key ?: error("no id")
        commentsRef.child(comment.paragraphId).child(id)
            .setValue(comment.copy(id = id, createdAt = System.currentTimeMillis())).await()
        topicsRef.child(comment.paragraphId).child("commentCount").setValue(ServerValue.increment(1)).await()
    }
}
