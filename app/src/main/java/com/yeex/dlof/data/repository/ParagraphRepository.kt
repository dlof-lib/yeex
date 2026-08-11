package com.yeex.dlof.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.yeex.dlof.data.model.Comment
import com.yeex.dlof.data.model.Paragraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

enum class Reaction { LIKE, DISLIKE }

class ParagraphRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val paragraphsRef get() = db.getReference("paragraphs")
    private val commentsRef get() = db.getReference("comments")
    private val likesRef get() = db.getReference("paragraphLikes")
    private val commentLikesRef get() = db.getReference("commentLikes")
    // One presence node per (paragraphId, viewerUid) — same pattern as
    // [likesRef] — so a view only ever counts once per real account instead
    // of once per app session, per the "مشاهدات حقيقية" requirement.
    private val viewsRef get() = db.getReference("paragraphViews")
    // "شعبية" (popularity star) presence node — separate metric from
    // like/dislike, see [toggleStar].
    private val starsRef get() = db.getReference("paragraphStars")

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

    /**
     * One-shot (non-flow) fetch of active paragraphs — for callers that just
     * need a snapshot rather than a live subscription, e.g.
     * [com.yeex.dlof.ui.search.SearchScreen]'s idle-state "الرائج الآن"
     * section, which shouldn't keep a permanent Realtime Database listener
     * open just to compute a top-5 trending list once.
     */
    suspend fun getActiveParagraphs(roomId: String? = null): List<Paragraph> {
        val query = if (roomId != null) {
            paragraphsRef.orderByChild("roomId").equalTo(roomId)
        } else {
            paragraphsRef.orderByChild("createdAt")
        }
        val now = System.currentTimeMillis()
        return query.get().await().children.mapNotNull { it.getValue(Paragraph::class.java) }
            .filter { it.expiresAt > now }
    }

    /**
     * Client-side text search over currently-active (non-expired) paragraphs
     * — matches [Paragraph.text], which is also where hashtags typed inline
     * (e.g. "#باري_تيوب") live, so this doubles as a hashtag search for
     * [com.yeex.dlof.ui.search.SearchScreen]. Realtime Database has no
     * full-text/substring index, so — same trade-off [getActiveParagraphs]
     * above already makes — this reuses that one-shot fetch of the (small,
     * 24h-bounded) set of active paragraphs and filters in memory rather
     * than querying a text index that doesn't exist on the free tier.
     */
    suspend fun searchActiveByText(query: String, limit: Int = 30): List<Paragraph> {
        val q = query.trim().removePrefix("#").lowercase()
        if (q.isEmpty()) return emptyList()
        return getActiveParagraphs()
            .filter { it.text.lowercase().contains(q) }
            .sortedByDescending { it.createdAt }
            .take(limit)
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

    /**
     * Tapping the like button when already liked clears the reaction; same for
     * dislike. Returns the reaction that ended up committed, so callers (e.g.
     * FeedViewModel) can trust it as the source of truth instead of issuing a
     * redundant follow-up read.
     *
     * Race-safety, end to end:
     * 1. The old -> new flip of /paragraphLikes/{paragraphId}/{uid} runs inside
     *    a Realtime Database **transaction**. If the same user fires two taps
     *    in quick succession (double-tap, flaky network retry, multiple
     *    devices), Firebase re-runs the transaction locally against the
     *    latest server value until it commits cleanly — so the reaction node
     *    itself can never end up "stuck" between two conflicting writes.
     * 2. The resulting delta to likeCount/dislikeCount is applied with
     *    [ServerValue.increment], which the Realtime Database server resolves
     *    atomically at write time. Unlike the previous approach (read the
     *    current count, add the delta, write it back), concurrent likes from
     *    *different* users can never race and silently overwrite each other's
     *    increment — every tap's delta is guaranteed to be counted.
     * Both writes for a single tap are also folded into one multi-path
     * [DatabaseReference.updateChildren] call, so a tap costs one round trip
     * instead of the previous four-plus.
     */
    suspend fun toggleLike(paragraphId: String, uid: String): Reaction? =
        toggleReaction(paragraphId, uid, Reaction.LIKE)

    suspend fun toggleDislike(paragraphId: String, uid: String): Reaction? =
        toggleReaction(paragraphId, uid, Reaction.DISLIKE)

    private suspend fun toggleReaction(paragraphId: String, uid: String, tapped: Reaction): Reaction? {
        val reactionRef = likesRef.child(paragraphId).child(uid)

        var oldReaction: Reaction? = null
        var newReaction: Reaction? = null

        reactionRef.awaitTransaction { current ->
            oldReaction = (current.value as? String)?.let { runCatching { Reaction.valueOf(it) }.getOrNull() }
            newReaction = if (oldReaction == tapped) null else tapped
            current.value = newReaction?.name
            Transaction.success(current)
        }

        if (oldReaction == newReaction) return newReaction // nothing actually changed

        val likeDelta = deltaFor(Reaction.LIKE, oldReaction, newReaction)
        val dislikeDelta = deltaFor(Reaction.DISLIKE, oldReaction, newReaction)

        val updates = mutableMapOf<String, Any>()
        if (likeDelta != 0) updates["paragraphs/$paragraphId/likeCount"] = ServerValue.increment(likeDelta.toLong())
        if (dislikeDelta != 0) updates["paragraphs/$paragraphId/dislikeCount"] = ServerValue.increment(dislikeDelta.toLong())
        if (updates.isNotEmpty()) db.reference.updateChildren(updates).await()

        return newReaction
    }

    private fun deltaFor(kind: Reaction, old: Reaction?, new: Reaction?): Int = when {
        old == kind && new != kind -> -1
        old != kind && new == kind -> 1
        else -> 0
    }

    /**
     * Coroutine bridge for [DatabaseReference.runTransaction], which only
     * exposes a callback API. Suspends until the transaction has been applied
     * (retrying internally on the SDK side under contention) or failed.
     */
    private suspend fun DatabaseReference.awaitTransaction(
        update: (MutableData) -> Transaction.Result
    ): DataSnapshot? = suspendCancellableCoroutine { cont ->
        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result = update(currentData)
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) cont.resumeWithException(error.toException())
                else cont.resume(snapshot)
            }
        })
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
     * Simple (non-toggle-between-two-states, unlike paragraph like/dislike)
     * heart on a single comment — returns the new liked state. Mirrors
     * [toggleLike]'s presence-node pattern via `commentLikes/$paragraphId/$commentId/$uid`,
     * with the comment's own `likeCount` bumped atomically alongside it.
     */
    suspend fun toggleCommentLike(paragraphId: String, commentId: String, uid: String): Boolean {
        val ref = commentLikesRef.child(paragraphId).child(commentId).child(uid)
        val alreadyLiked = ref.get().await().exists()
        return if (alreadyLiked) {
            ref.removeValue().await()
            bumpComment(paragraphId, commentId, -1)
            false
        } else {
            ref.setValue(true).await()
            bumpComment(paragraphId, commentId, 1)
            true
        }
    }

    suspend fun getCommentLikedByMe(paragraphId: String, commentId: String, uid: String): Boolean =
        commentLikesRef.child(paragraphId).child(commentId).child(uid).get().await().exists()

    private suspend fun bumpComment(paragraphId: String, commentId: String, delta: Int) {
        commentsRef.child(paragraphId).child(commentId).child("likeCount")
            .setValue(ServerValue.increment(delta.toLong())).await()
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
     * Bumps [Paragraph.viewCount] by one — but only the *first* time this
     * particular signed-in account ([viewerUid]) is recorded against this
     * paragraph, via a presence node at /paragraphViews/{paragraphId}/{uid}
     * (same idea as [likesRef]). This makes the counter a real unique-viewer
     * count instead of counting every re-open/app-relaunch, matching the
     * "مشاهدات حقيقية" requirement. Also bumps [authorId]'s
     * [com.yeex.dlof.data.model.User.totalViewCount] in the same multi-path
     * write, which [com.yeex.dlof.util.ViewMilestones] reads for the
     * profile's view-reward badge.
     *
     * Called once per (paragraph, viewer) from ParagraphCard when the page
     * becomes the pager's active page — see the LaunchedEffect there.
     */
    suspend fun incrementView(paragraphId: String, viewerUid: String, authorId: String = "") {
        if (paragraphId.isBlank() || viewerUid.isBlank()) return
        runCatching {
            val ref = viewsRef.child(paragraphId).child(viewerUid)
            if (ref.get().await().exists()) return@runCatching // already counted for this account
            ref.setValue(true).await()
            val updates = mutableMapOf<String, Any>(
                "paragraphs/$paragraphId/viewCount" to ServerValue.increment(1)
            )
            if (authorId.isNotBlank()) {
                updates["users/$authorId/totalViewCount"] = ServerValue.increment(1)
            }
            db.reference.updateChildren(updates).await()
        }
    }

    /**
     * Toggles this viewer's "شعبية" (popularity star) on a paragraph — a
     * third reaction, independent of like/dislike, meant to specifically
     * signal "this raised my opinion of the account". Starring bumps both
     * [Paragraph.starCount] and the author's
     * [com.yeex.dlof.data.model.User.popularityCount] together; unstarring
     * reverses both. Returns the new starred state.
     */
    suspend fun toggleStar(paragraphId: String, uid: String, authorId: String): Boolean {
        val ref = starsRef.child(paragraphId).child(uid)
        val alreadyStarred = ref.get().await().exists()
        val delta = if (alreadyStarred) -1L else 1L
        if (alreadyStarred) ref.removeValue().await() else ref.setValue(true).await()
        val updates = mutableMapOf<String, Any>(
            "paragraphs/$paragraphId/starCount" to ServerValue.increment(delta)
        )
        if (authorId.isNotBlank()) {
            updates["users/$authorId/popularityCount"] = ServerValue.increment(delta)
        }
        db.reference.updateChildren(updates).await()
        return !alreadyStarred
    }

    suspend fun getStarredByMe(paragraphId: String, uid: String): Boolean =
        starsRef.child(paragraphId).child(uid).get().await().exists()

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

    /**
     * Atomic server-side counter bump via [ServerValue.increment] — used for
     * counters (comment/repost) that aren't part of the like/dislike flip
     * above but still need to be race-safe against concurrent writers, for
     * the same reason described on [toggleReaction]: a naive get-then-set
     * can silently drop increments when two clients bump the same paragraph
     * at nearly the same moment.
     */
    private suspend fun bump(paragraphId: String, field: String, delta: Int) {
        paragraphsRef.child(paragraphId).child(field).setValue(ServerValue.increment(delta.toLong())).await()
    }
}
