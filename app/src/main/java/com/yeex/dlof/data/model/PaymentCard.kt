package com.yeex.dlof.data.model

/**
 * A user's linked payment method summary — stored at /paymentMethods/{uid}.
 *
 * IMPORTANT — this deliberately never holds a full card number, CVV, or
 * expiry: storing raw card data in a plain Realtime Database node would be
 * a serious PCI-DSS/security violation (any client with read access to that
 * path could harvest live card numbers). What's kept here is a *masked*
 * summary (brand + last 4 digits + expiry month/year only) purely so the UI
 * can show "Visa •••• 4242" and know a card is on file.
 *
 * To actually charge a subscription, this app needs a real payment
 * processor's client SDK (Stripe, Google Play Billing, etc.) wired in,
 * which tokenizes the card on-device and hands the token to a secure
 * backend — the card number itself never touches Firebase. That backend
 * integration is outside what a Firebase-only client app can safely do on
 * its own, so [com.yeex.dlof.data.repository.SubscriptionRepository.linkCard]
 * only ever writes the masked fields below.
 */
data class PaymentCard(
    val uid: String = "",
    val brand: String = "",       // "Visa" | "Mastercard" | "Mada" | ...
    val last4: String = "",
    val expiryMonth: Int = 0,
    val expiryYear: Int = 0,
    val holderName: String = "",
    val linkedAt: Long = 0L
)
