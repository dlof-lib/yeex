package com.yeex.dlof.util

/**
 * "مكافآت المشاهدات" — view-count reward tiers. A single ordered ladder used
 * for both a user's aggregate content views ([com.yeex.dlof.data.model.User.totalViewCount],
 * summed across every paragraph they've posted) and, on a per-paragraph/per-room
 * basis, whatever [Milestone] a specific post or room has crossed.
 *
 * Tiers run 10K -> 100M. Each tier has a stable [Milestone.id] (safe to persist,
 * e.g. "as the last-celebrated milestone" if a future pass wants to fire a
 * one-time congratulatory toast) plus a localized-by-caller [label] built from
 * [displayValue]/[displayUnit] so UI stays in Arabic-numeral-friendly short form
 * ("10 آلاف", "5 مليون") rather than a raw long number.
 */
object ViewMilestones {

    enum class Unit { THOUSAND, MILLION }

    data class Milestone(
        val id: String,
        val threshold: Long,
        val displayValue: Int,
        val unit: Unit,
        // 1 = bronze tier ... 8 = top tier, used to pick a badge color/glow in the UI.
        val tier: Int
    )

    /** Ordered ascending; last entry (100M) is the ceiling tier. */
    val ALL: List<Milestone> = listOf(
        Milestone("10k", 10_000L, 10, Unit.THOUSAND, 1),
        Milestone("50k", 50_000L, 50, Unit.THOUSAND, 2),
        Milestone("100k", 100_000L, 100, Unit.THOUSAND, 3),
        Milestone("500k", 500_000L, 500, Unit.THOUSAND, 4),
        Milestone("1m", 1_000_000L, 1, Unit.MILLION, 5),
        Milestone("5m", 5_000_000L, 5, Unit.MILLION, 6),
        Milestone("10m", 10_000_000L, 10, Unit.MILLION, 7),
        Milestone("100m", 100_000_000L, 100, Unit.MILLION, 8)
    )

    /** Highest tier reached so far, or null if [viewCount] hasn't hit the first (10K) tier yet. */
    fun currentMilestone(viewCount: Long): Milestone? =
        ALL.lastOrNull { viewCount >= it.threshold }

    /** All tiers reached so far, ascending — for a "خزانة الأوسمة" badge shelf. */
    fun earnedMilestones(viewCount: Long): List<Milestone> =
        ALL.filter { viewCount >= it.threshold }

    /** Next tier still to reach, or null once past the 100M ceiling. */
    fun nextMilestone(viewCount: Long): Milestone? =
        ALL.firstOrNull { viewCount < it.threshold }

    /** 0f..1f progress toward [nextMilestone], for a progress bar under the badge. */
    fun progressToNext(viewCount: Long): Float {
        val next = nextMilestone(viewCount) ?: return 1f
        val prevThreshold = ALL.lastOrNull { it.threshold <= viewCount }?.threshold ?: 0L
        val span = (next.threshold - prevThreshold).toFloat()
        if (span <= 0f) return 1f
        return ((viewCount - prevThreshold).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Compact Arabic label, e.g. "١٠ آلاف مشاهدة" style value pieces for the caller to compose. */
    fun Milestone.numberLabel(): String = when (unit) {
        Unit.THOUSAND -> "${displayValue}k"
        Unit.MILLION -> "${displayValue}M"
    }

    /** Short, locale-agnostic formatter for any raw view count (e.g. "12.3K", "4.1M"). */
    fun formatCount(count: Long): String = when {
        count >= 1_000_000L -> "%.1fM".format(count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000L -> "%.1fK".format(count / 1_000.0).replace(".0K", "K")
        else -> count.toString()
    }
}
