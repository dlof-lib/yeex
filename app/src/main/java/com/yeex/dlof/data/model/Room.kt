package com.yeex.dlof.data.model

/**
 * A "غرفة" (room) — a topic/interest space. Public rooms are discoverable and
 * open to join; private rooms require an invite/approval (enforced in
 * database.rules.json via the `members` map).
 */
data class Room(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val isPublic: Boolean = true,
    val bio: String = "",              // "سيرة الغرفة"
    val interests: List<String> = emptyList(),
    val socialLinks: Map<String, String> = emptyMap(), // e.g. "instagram" -> url
    val phone: String = "",            // optional
    val iconUrl: String = "",
    val memberCount: Long = 0,
    val createdAt: Long = 0L
)
