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
    val createdAt: Long = 0L,
    // "GENERAL" | "TV_CHANNEL" — see com.yeex.dlof.util.RoomType. A
    // TV-channel room is the room-level counterpart of a TV-channel
    // business account (com.yeex.dlof.util.BusinessCategory.TV_CHANNEL).
    val roomType: String = "GENERAL",
    // Owner-only live-stream link, played inline via LiveStreamPlayer when
    // non-blank. Direct .m3u8/.mpd/.mp4 URLs play natively; anything else
    // (a YouTube/Twitch page link, etc.) is embedded via WebView.
    val liveStreamUrl: String = "",
    // "فئة الغرفة" — topical classification, one of com.yeex.dlof.util.RoomCategory.
    // Independent from [roomType], which is about the room's *format*
    // (general feed vs. TV channel), not its subject matter.
    val category: String = "GENERAL",
    // Optional wide banner shown at the top of RoomScreen, above the name —
    // separate from [iconUrl] (the small round avatar used in room lists).
    val coverUrl: String = "",
    // Owner-authored "قوانين الغرفة" (community guidelines), shown to every
    // member on RoomScreen — a lightweight moderation tool so large/public
    // rooms can set expectations without a full rules/mod-log system.
    val rules: String = ""
)
