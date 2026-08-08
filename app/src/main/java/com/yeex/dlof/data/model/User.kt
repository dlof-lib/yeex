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
    val createdAt: Long = 0L,
    val language: String = "ar",    // ar | en | es
    val isOfficial: Boolean = false, // true only for the yeex.open account
    // ---- Business account (see com.yeex.dlof.util.BusinessCategory) ----
    val accountType: String = "PERSONAL",   // "PERSONAL" | "BUSINESS"
    val businessCategory: String = "",      // one of BusinessCategory.ALL; meaningful only when accountType == "BUSINESS"
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessLinks: Map<String, String> = emptyMap() // label -> url, e.g. "website" -> "https://..."
)
