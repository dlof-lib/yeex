package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.yeex.dlof.data.model.Room
import kotlinx.coroutines.tasks.await

class RoomRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val roomsRef get() = db.getReference("rooms")
    private val membersRef get() = db.getReference("roomMembers")
    private val userRoomsRef get() = db.getReference("userRooms")

    suspend fun createRoom(room: Room, ownerUid: String): String {
        val id = roomsRef.push().key ?: error("no id")
        val toSave = room.copy(id = id, ownerId = ownerUid, createdAt = System.currentTimeMillis(), memberCount = 1)
        roomsRef.child(id).setValue(toSave).await()
        membersRef.child(id).child(ownerUid).setValue(true).await()
        userRoomsRef.child(ownerUid).child(id).setValue(true).await()
        return id
    }

    suspend fun joinRoom(roomId: String, uid: String) {
        membersRef.child(roomId).child(uid).setValue(true).await()
        userRoomsRef.child(uid).child(roomId).setValue(true).await()
        val countRef = roomsRef.child(roomId).child("memberCount")
        val current = countRef.get().await().getValue(Long::class.java) ?: 0L
        countRef.setValue(current + 1).await()
    }

    suspend fun getRoom(roomId: String): Room? =
        roomsRef.child(roomId).get().await().getValue(Room::class.java)

    /**
     * Sets/clears the room's live-stream link (empty string clears it) —
     * callable only by the room owner per database.rules.json's room-level
     * `.write` rule (which already gates the whole room node, so this is
     * enforced server-side too, not just by [com.yeex.dlof.ui.room.RoomScreen]
     * hiding the edit control from non-owners).
     */
    suspend fun updateLiveStream(roomId: String, url: String) {
        roomsRef.child(roomId).child("liveStreamUrl").setValue(url).await()
    }

    /**
     * Sets/clears the room's "قوانين الغرفة" community guidelines — owner-only
     * to set per the same room-level `.write` rule as [updateLiveStream].
     */
    suspend fun updateRules(roomId: String, rules: String) {
        roomsRef.child(roomId).child("rules").setValue(rules).await()
    }

    /** Sets/clears the room's cover banner — owner-only, see [updateLiveStream]. */
    suspend fun updateCover(roomId: String, coverUrl: String) {
        roomsRef.child(roomId).child("coverUrl").setValue(coverUrl).await()
    }

    /** Sets the room's topical category (see [com.yeex.dlof.util.RoomCategory]) — owner-only. */
    suspend fun updateCategory(roomId: String, category: String) {
        roomsRef.child(roomId).child("category").setValue(category).await()
    }

    suspend fun listPublicRooms(): List<Room> =
        roomsRef.orderByChild("isPublic").equalTo(true).get().await()
            .children.mapNotNull { it.getValue(Room::class.java) }

    /**
     * Public rooms in a single [com.yeex.dlof.util.RoomCategory] — used by
     * BrowseRoomsScreen's category filter chips. Filters client-side after a
     * `.indexOn` category query since Realtime Database can't combine two
     * `orderByChild` equality filters (isPublic + category) server-side.
     */
    suspend fun listPublicRoomsByCategory(category: String): List<Room> =
        roomsRef.orderByChild("category").equalTo(category).get().await()
            .children.mapNotNull { it.getValue(Room::class.java) }
            .filter { it.isPublic }

    /** Rooms the given user owns or has joined — used by the repost-into-room picker. */
    suspend fun listMyRooms(uid: String): List<Room> {
        val roomIds = userRoomsRef.child(uid).get().await().children.mapNotNull { it.key }
        return roomIds.mapNotNull { getRoom(it) }
    }

    /**
     * Prefix search over room names for [com.yeex.dlof.ui.search.SearchScreen].
     * Only returns public rooms — private rooms stay discoverable solely by
     * direct invite/link, per the "غرف عامة وخاصة" requirement. Like
     * [UserRepository.searchByIdentifierPrefix], this is a lexicographic
     * prefix range query since Realtime Database has no full-text search.
     */
    suspend fun searchByName(prefix: String, limit: Int = 20): List<Room> {
        val clean = prefix.trim()
        if (clean.isEmpty()) return emptyList()
        return roomsRef.orderByChild("name")
            .startAt(clean)
            .endAt(clean + "\uf8ff")
            .limitToFirst(limit)
            .get().await()
            .children.mapNotNull { it.getValue(Room::class.java) }
            .filter { it.isPublic }
    }
}
