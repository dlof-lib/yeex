package com.yeex.dlof.data.model

/**
 * A "حاوية تخصيص" (custom container), internal code name TEX-W — a
 * user-created group of rooms/interests. Searchable in the search screen
 * using the syntax "@container.<name>[].me".
 */
data class Container(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val roomIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = 0L
)
