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
    val verified: Boolean = false,
    val verifiedReason: String = "", // "manual" | "cross_platform" | "official"
    val externalFollowerCounts: Map<String, Long> = emptyMap(), // self-reported, reviewed by admins
    val tekingCount: Long = 0,      // followers (people "Teking" this user)
    val tekerCount: Long = 0,       // accounts this user is a "Teker" of (following)
    val createdAt: Long = 0L,
    val language: String = "ar",    // ar | en | es
    val isOfficial: Boolean = false // true only for the yeex.open account
)
