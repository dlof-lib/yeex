package com.yeex.dlof.data.model

/**
 * "اشتراك" — a paid subscription tier an account defines for its followers.
 * Stored at /subscriptionPlans/{ownerId}/{id}. Any user can create one or
 * more plans on their own account (see SubscriptionRepository.createPlan);
 * the owner freely names the plan and lists whatever perks they're offering
 * subscribers — yeex doesn't prescribe what a "feature" is, it's just a
 * short label the owner writes (e.g. "بث مباشر أسبوعي", "شارة ذهبية",
 * "محتوى حصري"), rendered as a checklist on the subscribe sheet.
 */
data class SubscriptionPlan(
    val id: String = "",
    val ownerId: String = "",
    // Owner-chosen name for the tier, e.g. "الذهبي" / "VIP" / "داعم".
    val name: String = "",
    val description: String = "",
    // Free-form price label the owner writes themselves (e.g. "5$ / شهريًا"),
    // NOT a machine-charged amount — see PaymentCard's doc comment: yeex has
    // no payment-gateway backend wired in, so plans describe a price but
    // actual recurring billing needs a real processor (Stripe Billing /
    // Google Play Billing) integrated server-side before this can charge
    // anyone automatically.
    val priceLabel: String = "",
    // Owner-authored perk checklist, one string per line item.
    val features: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val active: Boolean = true
)

/**
 * A confirmed subscription relationship, stored at
 * /subscribers/{ownerId}/{subscriberId}. One row per (owner, subscriber)
 * pair — a subscriber can only be on one of that owner's plans at a time.
 */
data class Subscriber(
    val subscriberId: String = "",
    val subscriberIdentifier: String = "",
    val planId: String = "",
    val planName: String = "",
    val since: Long = 0L
)
