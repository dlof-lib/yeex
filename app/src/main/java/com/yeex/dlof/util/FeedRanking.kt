package com.yeex.dlof.util

import com.yeex.dlof.data.model.Paragraph
import kotlin.math.ln

/**
 * Ranks paragraphs by a mix of engagement and recency (a simplified
 * "hot score", similar in spirit to Reddit's ranking) rather than showing a
 * pure chronological feed. Interest-matching (rooms/containers the user
 * follows or has engaged with) is applied upstream by only fetching
 * paragraphs from the user's joined/public rooms before this function runs.
 *
 * Also filters out anything past its 24h [Paragraph.expiresAt] — the
 * client-side backstop for the free-tier Realtime Database, which has no
 * server-side cron. See README "24-hour expiry" for the full explanation
 * and the optional Cloud Function upgrade path.
 */
object FeedRanking {

    fun rankForFeed(items: List<Paragraph>, nowMillis: Long): List<Paragraph> {
        return items
            .asSequence()
            .filter { it.expiresAt > nowMillis }
            .map { it.copy(engagementScore = score(it, nowMillis)) }
            .sortedByDescending { it.engagementScore }
            .toList()
    }

    private fun score(p: Paragraph, nowMillis: Long): Double {
        val engagement = (p.likeCount * 1.0) + (p.commentCount * 2.0) +
            (p.repostCount * 3.0) - (p.dislikeCount * 1.5)
        val ageHours = ((nowMillis - p.createdAt).coerceAtLeast(1L)) / 3_600_000.0
        // Logarithmic engagement weight so a single viral post doesn't
        // permanently bury everything else, minus a mild age decay.
        return ln(engagement.coerceAtLeast(0.0) + 1.0) - (ageHours / 12.0)
    }
}
