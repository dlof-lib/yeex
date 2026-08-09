package com.yeex.dlof.util

/**
 * Validates "المعرف" (identifiers) per the product spec:
 *  - Lowercase letters only (any script — Arabic, Latin, etc.), no uppercase.
 *  - Digits allowed: Western (0-9) or Arabic-Indic (٠-٩).
 *  - Dots (.) allowed as separators, multiple scripts may be mixed.
 *  - NOT allowed: underscore (_), hyphen (-), tilde (~), spaces, or any
 *    uppercase letter.
 */
object UsernameValidator {

    // Anything that is a letter in Unicode (\p{L} covers both cased letters like Latin/Cyrillic
    // AND caseless scripts like Arabic/CJK/Hebrew — Arabic letters are category Lo, never Ll, so
    // matching only \p{Ll} here silently rejected every Arabic identifier), OR a digit
    // (western/arabic-indic), OR a dot. Actual uppercase letters are already rejected above by
    // the explicit isUpperCase() check, so widening this to \p{L} does not let uppercase through.
    private val ALLOWED_REGEX = Regex("^[\\p{L}0-9\u0660-\u0669.]+$")
    private val FORBIDDEN_CHARS = setOf('_', '-', '~', ' ')

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 24

    data class Result(val isValid: Boolean, val errorKey: String? = null)

    fun validate(raw: String): Result {
        if (raw.length < MIN_LENGTH || raw.length > MAX_LENGTH) {
            return Result(false, "length")
        }
        if (raw.any { it in FORBIDDEN_CHARS }) {
            return Result(false, "forbidden_char")
        }
        if (raw.any { it.isUpperCase() }) {
            return Result(false, "uppercase")
        }
        if (raw.startsWith(".") || raw.endsWith(".") || raw.contains("..")) {
            return Result(false, "dot_placement")
        }
        if (!ALLOWED_REGEX.matches(raw)) {
            return Result(false, "invalid_chars")
        }
        return Result(true)
    }

    /**
     * Firebase Authentication (Spark/free tier) needs an email+password identity.
     * We keep the user-facing "معرف" as the real handle, and derive a pseudo-email
     * under a fixed internal domain purely so Firebase Auth can be used for free —
     * this email is never shown to the user.
     */
    fun toPseudoEmail(identifier: String): String = "$identifier@id.yeex.app"

    /**
     * Derives a valid, best-effort identifier from a Google/GitHub display
     * name or email local-part when a user signs in via a social provider
     * for the first time and has no "معرف" yet. Strips everything outside
     * the allowed charset, lowercases, and collapses/trims dots. The caller
     * is still responsible for resolving collisions (see AuthRepository).
     */
    fun sanitizeForAuto(raw: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^\\p{L}0-9\u0660-\u0669.]"), "")
            .replace(Regex("\\.{2,}"), ".")
            .trim('.')
            .take(MAX_LENGTH)
        return if (cleaned.length >= MIN_LENGTH) cleaned else (cleaned + "yeexuser").take(MAX_LENGTH)
    }
}
