package com.yeex.dlof.util

/**
 * Scores "people you may know" candidates for
 * [com.yeex.dlof.data.repository.UserRepository.suggestedAccounts] — a
 * lightweight friend-of-a-friend algorithm over yeex's tek (follow) graph.
 *
 * The repository does the graph walk (who the viewer teks, then who *those*
 * accounts tek) and hands this object a plain `uid -> mutual count` map —
 * how many of the viewer's own teks also tek that candidate. This object
 * owns only the scoring/ordering decision, kept separate from the Firebase
 * reads for the same reason [FeedRanking] and [SearchRanking] are pure
 * functions: it's trivial to reason about and tune without touching any
 * network code.
 *
 * Candidates are ordered purely by mutual-tek count — the more of the
 * viewer's own followed accounts also follow a candidate, the stronger the
 * social proof that the suggestion is relevant — with insertion order
 * (effectively arrival order during the graph walk) as a stable tiebreaker
 * rather than re-sorting ties randomly on every call.
 */
object RecommendationRanking {

    fun rank(mutualCounts: Map<String, Int>, limit: Int): List<String> =
        mutualCounts.entries
            .sortedByDescending { it.value }
            .take(limit.coerceAtLeast(0))
            .map { it.key }
}
