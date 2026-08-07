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

    suspend fun listPublicRooms(): List<Room> =
        roomsRef.orderByChild("isPublic").equalTo(true).get().await()
            .children.mapNotNull { it.getValue(Room::class.java) }

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
