package com.yeex.dlof.data.model

/** YEEX TOPICS | المواضيع — a permanent, text-first post type, independent of
 * [Paragraph]. Where a paragraph is square, visual, and expires after 24h, a
 * topic never expires and is built for reading/discussion: a title, a long
 * formatted body (headings, quotes, lists, code, images, links, hashtags,
 * mentions), or a pasted external link that YEEX renders as a [LinkPreview]
 * card instead of a bare URL. See TopicRepository/CreateTopicScreen. */
enum class TopicType { TEXT, LINK }

/** How a pasted URL should be rendered — detected from the link's host/path
 * by [com.yeex.dlof.util.LinkPreviewUtil], e.g. YouTube -> video card,
 * GitHub -> project card, a direct image/video URL -> media preview,
 * anything else -> generic website/article card. */
enum class LinkCardType { YOUTUBE, GITHUB, ARTICLE, IMAGE, VIDEO, WEBSITE }

/**
 * The "Link Card" shown instead of a raw pasted URL. Populated on-device by
 * [com.yeex.dlof.util.LinkPreviewUtil] at publish time and stored inline on
 * the [Topic] so the card renders instantly without re-fetching the page
 * every time the topic is viewed.
 */
data class LinkPreview(
    val url: String = "",
    val cardType: String = LinkCardType.WEBSITE.name,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val siteName: String = ""
)

/**
 * One entry in a topic's update log ("Topic Updates") — see [Topic]. Lets the
 * author keep adding to the same permanent topic over time (a new release, a
 * correction, a follow-up) instead of publishing a brand new topic every
 * time, so a topic can grow into a living page rather than a single static
 * post. Stored at /topicUpdates/{topicId}/{updateId}, same shape as
 * /comments — see TopicRepository.observeUpdates.
 */
data class TopicUpdate(
    val id: String = "",
    val topicId: String = "",
    val text: String = "",
    val imageBase64: String = "",
    val createdAt: Long = 0L
)

/**
 * A "موضوع" (topic) — YEEX's permanent, text/discussion-first post type.
 * Unlike [Paragraph] there is no [expiresAt]/24h cleanup: a topic is meant to
 * stay searchable and linkable for years (شرح برمجة / أفكار / أخبار / دروس /
 * تجميع روابط ...).
 */
data class Topic(
    val id: String = "",
    val authorId: String = "",
    val authorIdentifier: String = "",
    val authorVerified: Boolean = false,
    val type: String = TopicType.TEXT.name,
    val title: String = "",
    // Long-form formatted body. Supports a lightweight markdown subset —
    // see com.yeex.dlof.ui.components.MarkdownText — for subheadings (##),
    // quotes (>), lists (-), and code fences (```). Hashtags (#tag) and
    // mentions (@identifier) inside the body are auto-linked at render time.
    val body: String = "",
    val imageBase64: String = "", // optional cover/inline image, "" if none
    val link: LinkPreview? = null, // populated only when type == LINK.name
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    // Paragraphs the author chose to attach to this topic (موضوع ↔ فقرة) —
    // e.g. a "تصميم YEEX الجديد" topic linking out to the image/video
    // paragraphs that showcase it. A linked paragraph may have expired and
    // been purged (paragraphs are still 24h-lived); the detail screen simply
    // skips ids it can no longer resolve.
    val linkedParagraphIds: List<String> = emptyList(),
    val roomId: String = "", // "" if posted outside any room
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L, // bumped whenever a Topic Update is added
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val viewCount: Long = 0,
    val updateCount: Long = 0,
    val engagementScore: Double = 0.0
)
