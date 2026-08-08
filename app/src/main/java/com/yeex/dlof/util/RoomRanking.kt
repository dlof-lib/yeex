package com.yeex.dlof.util

import com.yeex.dlof.data.model.Room
import kotlin.math.ln

/**
 * Ranks public rooms for the "استكشاف" (Explore) tab of
 * [com.yeex.dlof.ui.room.BrowseRoomsScreen] by a trending score instead of
 * whatever order [com.yeex.dlof.data.repository.RoomRepository.listPublicRooms]
 * happened to return — same "hot, not just big" shape as [FeedRanking],
 * scaled to rooms:
 *
 *  - **Size** (`memberCount`) is log-dampened, same reasoning as
 *    [FeedRanking]'s engagement term: a room with 10x the members isn't
 *    automatically 10x more worth surfacing, it should just edge out
 *    smaller ones rather than permanently dominate every list.
 *  - **Freshness boost**: rooms created in the last 3 days get a flat
 *    bonus, so a brand-new room with a handful of early members has a real
 *    chance to surface next to long-established ones instead of being
 *    buried at the bottom until it slowly accumulates members — the
 *    same "give new things a chance" principle behind Reddit/HN's rising
 *    sort, applied to room discovery.
 */
object RoomRanking {

    private const val FRESHNESS_WINDOW_DAYS = 3.0
    private const val FRESHNESS_BOOST = 1.2

    fun rankTrending(rooms: List<Room>, nowMillis: Long = System.currentTimeMillis()): List<Room> =
        rooms.sortedByDescending { trendScore(it, nowMillis) }

    private fun trendScore(room: Room, nowMillis: Long): Double {
        val sizeScore = ln((room.memberCount + 1).toDouble())
        val ageDays = ((nowMillis - room.createdAt).coerceAtLeast(0L)) / 86_400_000.0
        val freshness = if (room.createdAt > 0L && ageDays <= FRESHNESS_WINDOW_DAYS) FRESHNESS_BOOST else 0.0
        return sizeScore + freshness
    }
}
