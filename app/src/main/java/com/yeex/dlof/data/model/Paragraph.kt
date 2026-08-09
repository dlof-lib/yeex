package com.yeex.dlof.data.model

enum class ParagraphType { TEXT, IMAGE, VIDEO, MOMENT }

/**
 * A "فقرة" (paragraph) — the core post unit. Square aspect ratio in the UI,
 * swiped left/right in the feed. Expires 24h after [createdAt]; see
 * [com.yeex.dlof.util.FeedRanking] for the "not expired" filter and
 * database.rules.json for server-side enforcement of writes.
 *
 * Media ([mediaBase64]) is stored inline as a Base64 string per the product
 * spec, keeping everything on the free Realtime Database tier with no
 * Cloud Storage / Blaze plan requirement. Videos must be 5–10 seconds.
 */
data class Paragraph(
    val id: String = "",
    val authorId: String = "",
    val authorIdentifier: String = "",
    val authorVerified: Boolean = false,
    val type: String = ParagraphType.TEXT.name,
    val text: String = "",
    val mediaBase64: String = "",   // "" for TEXT paragraphs
    val mediaMimeType: String = "", // e.g. image/jpeg, video/mp4
    val roomId: String = "",        // "" if posted outside any room
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,       // createdAt + 24h, indexed for cleanup queries
    val likeCount: Long = 0,
    val dislikeCount: Long = 0,
    val commentCount: Long = 0,
    val repostCount: Long = 0,
    val repostOfId: String = "",    // set when this is a repost into a room
    val repostComment: String = "",
    val viewCount: Long = 0,        // bumped once per viewer via ParagraphRepository.incrementView
    val engagementScore: Double = 0.0, // maintained by FeedRanking, used to sort the feed
    // YEEX MOMENT ("لحظة") — only populated when type == MOMENT.name. An ordered
    // sequence of stages (see MomentStep) rendered as a connected timeline
    // instead of a single flat text/image/video. [text] above is still used
    // as the moment's overall title/intro line (e.g. "📍 رحلتي إلى بيروت").
    val momentSteps: List<MomentStep> = emptyList()
)
