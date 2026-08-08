package com.yeex.dlof.util

import com.yeex.dlof.data.model.Paragraph
import kotlin.math.ln

/**
 * yeex's "For You" feed ranking algorithm.
 *
 * Three signals combine into one score, then a diversity pass reorders the
 * result — same overall shape as production short-form-video feeds:
 *
 *  1. **Hot score** (engagement vs. age) — a simplified Reddit-style "hot"
 *     ranking: likes/comments/reposts are weighted (comments and reposts
 *     count for more than a like, since they're a stronger signal of real
 *     interest), dislikes subtract, the whole thing is compressed with
 *     `ln(...)` so one viral paragraph can't bury everything else, and a
 *     mild linear age decay keeps the feed from calcifying around a single
 *     old post for the full 24h lifetime.
 *  2. **Affinity boost** — paragraphs from accounts the viewer already teks
 *     (follows) get a fixed score bonus, the same way a "following" signal
 *     nudges a for-you feed without fully overriding it — a great post from
 *     someone new can still outrank a mediocre one from someone followed.
 *  3. **Diversity re-ranking** — after sorting purely by score, a greedy
 *     pass enforces an "author cooldown": the same author's posts don't
 *     appear twice within [authorCooldown] of each other unless there's
 *     genuinely nothing else left, so one prolific poster can't monopolize
 *     several consecutive swipes even if their engagementScore is highest.
 *
 * Also filters out anything past its 24h [Paragraph.expiresAt] — the
 * client-side backstop for the free-tier Realtime Database, which has no
 * server-side cron. See README "24-hour expiry" for the full explanation
 * and the optional Cloud Function upgrade path.
 */
object FeedRanking {

    /** Fixed score bonus applied to paragraphs from accounts the viewer teks. */
    private const val AFFINITY_BOOST = 0.6

    /** How many of the most-recent output slots an author must sit out before repeating. */
    private const val AUTHOR_COOLDOWN = 2

    fun rankForFeed(
        items: List<Paragraph>,
        nowMillis: Long,
        followingUids: Set<String> = emptySet()
    ): List<Paragraph> {
        val sorted = items
            .asSequence()
            .filter { it.expiresAt > nowMillis }
            .map { it.copy(engagementScore = score(it, nowMillis, followingUids)) }
            .sortedByDescending { it.engagementScore }
            .toList()
        return diversifyByAuthor(sorted, AUTHOR_COOLDOWN)
    }

    /**
     * Global "trending now" top-N, used by [com.yeex.dlof.ui.search.SearchScreen]'s
     * idle-state "الرائج الآن" section. Same hot-score math as [rankForFeed]
     * but with no per-viewer affinity boost (trending is impersonal, not
     * personalized), still passed through [diversifyByAuthor] so one prolific
     * poster can't take every slot in a list this short.
     */
    fun topTrending(items: List<Paragraph>, nowMillis: Long, limit: Int = 5): List<Paragraph> {
        val sorted = items
            .asSequence()
            .filter { it.expiresAt > nowMillis }
            .map { it.copy(engagementScore = score(it, nowMillis, emptySet())) }
            .sortedByDescending { it.engagementScore }
            .toList()
        return diversifyByAuthor(sorted, AUTHOR_COOLDOWN).take(limit)
    }

    private fun score(p: Paragraph, nowMillis: Long, followingUids: Set<String>): Double {
        val engagement = (p.likeCount * 1.0) + (p.commentCount * 2.0) +
            (p.repostCount * 3.0) - (p.dislikeCount * 1.5)
        val ageHours = ((nowMillis - p.createdAt).coerceAtLeast(1L)) / 3_600_000.0
        // Logarithmic engagement weight so a single viral post doesn't
        // permanently bury everything else, minus a mild age decay.
        val hotScore = ln(engagement.coerceAtLeast(0.0) + 1.0) - (ageHours / 12.0)
        val affinity = if (p.authorId.isNotBlank() && p.authorId in followingUids) AFFINITY_BOOST else 0.0
        return hotScore + affinity
    }

    /**
     * Greedy author-cooldown re-ranking: walks the score-sorted list and, at
     * each output position, picks the highest-scoring remaining paragraph
     * whose author hasn't appeared in the last [cooldown] picks — falling
     * back to the next-best overall pick only when every remaining
     * candidate is on cooldown (e.g. the whole remainder is one author).
     * Stable/no-op for feeds with [cooldown] or fewer items.
     */
    private fun diversifyByAuthor(sorted: List<Paragraph>, cooldown: Int): List<Paragraph> {
        if (sorted.size <= cooldown || cooldown <= 0) return sorted

        val remaining = ArrayDeque(sorted)
        val result = ArrayList<Paragraph>(sorted.size)
        val recentAuthors = ArrayDeque<String>()

        while (remaining.isNotEmpty()) {
            val pickIndex = remaining.indexOfFirst { candidate ->
                candidate.authorId.isBlank() || candidate.authorId !in recentAuthors
            }
            val index = if (pickIndex >= 0) pickIndex else 0
            val picked = remaining.removeAt(index)
            result += picked

            if (picked.authorId.isNotBlank()) {
                recentAuthors.addLast(picked.authorId)
                if (recentAuthors.size > cooldown) recentAuthors.removeFirst()
            }
        }
        return result
    }
}
