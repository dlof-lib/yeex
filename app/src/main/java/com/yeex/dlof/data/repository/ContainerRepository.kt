package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.yeex.dlof.data.model.Container
import kotlinx.coroutines.tasks.await

class ContainerRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val containersRef get() = db.getReference("containers")

    // Matches search queries like "@container.travel[].me" -> captures "travel"
    private val searchPattern = Regex("^@container\\.([\\p{Ll}0-9.]+)\\[\\]\\.me$")

    suspend fun createContainer(name: String, ownerUid: String, roomIds: List<String>): String {
        val id = containersRef.push().key ?: error("no id")
        val container = Container(
            id = id,
            name = name,
            ownerId = ownerUid,
            roomIds = roomIds,
            memberIds = listOf(ownerUid),
            createdAt = System.currentTimeMillis()
        )
        containersRef.child(id).setValue(container).await()
        return id
    }

    /** Parses "@container.<name>[].me" queries; returns null if the query doesn't match the syntax. */
    fun parseContainerQuery(query: String): String? =
        searchPattern.find(query.trim())?.groupValues?.get(1)

    suspend fun findByName(name: String): List<Container> =
        containersRef.orderByChild("name").equalTo(name).get().await()
            .children.mapNotNull { it.getValue(Container::class.java) }
}
