package com.yeex.dlof.data.model

/**
 * A yeex user profile, stored at /users/{uid} in Realtime Database.
 *
 * [identifier] is the unique, permanent handle (e.g. "majd.k2") — validated by
 * [com.yeex.dlof.util.UsernameValidator]: lowercase letters (any supported script),
 * digits (Arabic-Indic or Western), and dots only. No underscores, hyphens, tildes
 * or uppercase letters.
 */
data class User(
    val uid: String = "",
    val identifier: String = "",
    val displayName: String = "",
    // Lowercased mirror of [displayName], kept in sync by
    // [com.yeex.dlof.data.repository.UserRepository.updateProfile] — lets
    // [com.yeex.dlof.data.repository.UserRepository.searchByDisplayNamePrefix]
    // run a case-insensitive orderByChild prefix query, since Realtime
    // Database range queries are always case-sensitive on the raw field.
    val displayNameLower: String = "",
    val bio: String = "",
    val profileIconUrl: String = "",
    // ---- Profile banner: either a fixed-size cropped image OR a link to an
    // external video (never both — see UserRepository.updateBannerImage /
    // updateBannerVideoUrl, which clear the other field on save) ----
    val bannerUrl: String = "",
    val bannerVideoUrl: String = "",
    val verified: Boolean = false,
    val verifiedReason: String = "", // "manual" | "cross_platform" | "official"
    val externalFollowerCounts: Map<String, Long> = emptyMap(), // self-reported, reviewed by admins
    val tekingCount: Long = 0,      // followers (people "Teking" this user)
    val tekerCount: Long = 0,       // accounts this user is a "Teker" of (following)
    // ---- Views & popularity (see com.yeex.dlof.util.ViewMilestones) ----
    // Real, unique-viewer count of this profile page — see UserRepository.incrementProfileView.
    val profileViewCount: Long = 0,
    // Sum of viewCount across every paragraph this user has authored, bumped
    // alongside Paragraph.viewCount by ParagraphRepository.incrementView.
    // Drives the "مكافآت المشاهدات" milestone badge on the profile.
    val totalViewCount: Long = 0,
    // "الشعبيات" — total star reactions ("الشعبية") received across this
    // user's paragraphs; a star is separate from a like and specifically
    // raises the account's standing. See ParagraphRepository.toggleStar.
    val popularityCount: Long = 0,
    // ---- Subscriptions ("اشتراك") ----
    // True once this account has published at least one SubscriptionPlan
    // (see com.yeex.dlof.data.model.SubscriptionPlan) — lets ProfileScreen
    // show/hide the "اشتراك" entry point without an extra query.
    val hasSubscriptionPlans: Boolean = false,
    // Masked payment-method summary only (never a full card number/CVV — see
    // com.yeex.dlof.data.model.PaymentCard's doc comment on why raw card
    // data is never stored here). Populated once the user links a card from
    // Settings so they can subscribe to other accounts' paid tiers.
    val linkedCardBrand: String = "",
    val linkedCardLast4: String = "",
    val createdAt: Long = 0L,
    val language: String = "ar",    // ar | en | es
    val isOfficial: Boolean = false, // true only for the yeex.open account
    // ---- Business account (see com.yeex.dlof.util.BusinessCategory) ----
    val accountType: String = "PERSONAL",   // "PERSONAL" | "BUSINESS"
    val businessCategory: String = "",      // one of BusinessCategory.ALL; meaningful only when accountType == "BUSINESS"
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessLinks: Map<String, String> = emptyMap(), // label -> url, e.g. "website" -> "https://..."
    // ---- Privacy (Settings & Privacy screen) ----
    // Toggled from Settings; not yet enforced anywhere else in the app (no
    // follow-request gate on the profile/feed) — stored here as the flag a
    // future visibility-gating pass would read, and useful today as a
    // simple self-reported "حساب خاص" signal.
    val isPrivateAccount: Boolean = false,
    // "everyone" | "tekers" | "no_one" — who may comment on this user's
    // paragraphs. Also not yet enforced client-side; see BlockRepository's
    // doc comment for the same caveat on blocking.
    val commentPrivacy: String = "everyone"
)
