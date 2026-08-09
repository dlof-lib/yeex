package com.yeex.dlof.util

import com.yeex.dlof.data.model.Room
import com.yeex.dlof.data.model.User
import kotlin.math.ln

/**
 * Re-ranks the raw prefix-query results from [com.yeex.dlof.data.repository.UserRepository.searchByIdentifierPrefix]
 * and [com.yeex.dlof.data.repository.RoomRepository.searchByName] by actual
 * relevance instead of showing them in whatever order Realtime Database's
 * `startAt`/`endAt` range scan happened to return them.
 *
 * Firebase Realtime Database has no query-time relevance scoring at all —
 * results come back in plain lexicographic key order — so every ranking
 * signal below is applied client-side, after the (already-narrow) prefix
 * results are fetched. This keeps the algorithm cheap: it only ever
 * re-sorts the handful of candidates the prefix query already returned, it
 * never scans the whole users/rooms table.
 */
object SearchRanking {

    /**
     * Accounts: an exact "@handle" match always wins, then shorter
     * identifiers that start with the query outrank longer ones (typing
     * "ali" should surface "@ali" before "@alialialiali"), a display-name
     * substring hit adds a smaller bonus, verified accounts get a modest
     * trust boost, and popularity (Teking/follower count) breaks remaining
     * ties with a log-dampened weight so one viral account doesn't bury
     * every relevant-but-smaller match.
     */
    fun rankUsers(query: String, users: List<User>): List<User> {
        val q = query.removePrefix("@").trim().lowercase()
        if (q.isEmpty()) return users
        return users.sortedByDescending { userRelevance(it, q) }
    }

    private fun userRelevance(user: User, q: String): Double {
        val identifier = user.identifier.lowercase()
        val displayName = user.displayName.lowercase()

        var score = 0.0
        score += when {
            identifier == q -> 100.0
            identifier.startsWith(q) -> 60.0 - (identifier.length - q.length).coerceAtMost(40)
            // A mid-identifier hit (e.g. "@dev.majd" matching "majd") still
            // outranks a bare display-name/popularity match, mirroring the
            // "contains" tier [roomRelevance] already gives room names —
            // previously any non-prefix identifier match scored the same as
            // no match at all.
            identifier.contains(q) -> 25.0
            else -> 0.0
        }
        if (displayName.contains(q)) score += 15.0
        if (user.verified) score += 10.0
        score += ln((user.tekingCount + 1).toDouble()) * 2.0
        return score
    }

    /**
     * Rooms: same exact/prefix-length shaping as accounts, plus a size
     * signal (bigger, more-established rooms edge out near-empty ones on a
     * tied name match) and a small public-room boost, since a private room
     * a stranger can't join is a less useful top result for a bare-name
     * search than an open one with the same name.
     */
    fun rankRooms(query: String, rooms: List<Room>): List<Room> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rooms
        return rooms.sortedByDescending { roomRelevance(it, q) }
    }

    private fun roomRelevance(room: Room, q: String): Double {
        val name = room.name.lowercase()

        var score = 0.0
        score += when {
            name == q -> 100.0
            name.startsWith(q) -> 60.0 - (name.length - q.length).coerceAtMost(40)
            name.contains(q) -> 30.0
            room.interests.any { it.lowercase().contains(q) } -> 12.0
            else -> 0.0
        }
        score += ln((room.memberCount + 1).toDouble()) * 2.0
        if (room.isPublic) score += 4.0
        return score
    }
}
