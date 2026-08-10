package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.yeex.dlof.data.model.PaymentCard
import com.yeex.dlof.data.model.SubscriptionPlan
import com.yeex.dlof.data.model.Subscriber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * "اشتراك" — lets any account define one or more paid subscription tiers
 * (self-named, self-described perks) and lets other accounts subscribe to
 * one of them, plus linking a payment card summary. See [PaymentCard]'s doc
 * comment: this never stores a full card number, only a masked summary — a
 * real recurring charge needs a licensed payment gateway wired in
 * server-side, which is out of scope for a Firebase-only client.
 *
 * Data layout:
 *   /subscriptionPlans/{ownerId}/{planId}         -> SubscriptionPlan
 *   /subscribers/{ownerId}/{subscriberId}         -> Subscriber
 *   /subscribedTo/{subscriberId}/{ownerId}        -> planId   (reverse index, "my subscriptions")
 *   /paymentMethods/{uid}                         -> PaymentCard (masked only)
 */
class SubscriptionRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val plansRef get() = db.getReference("subscriptionPlans")
    private val subscribersRef get() = db.getReference("subscribers")
    private val subscribedToRef get() = db.getReference("subscribedTo")
    private val paymentMethodsRef get() = db.getReference("paymentMethods")
    private val usersRef get() = db.getReference("users")

    // ---- Plans (owner side) ----

    fun observePlans(ownerId: String): Flow<List<SubscriptionPlan>> = callbackFlow {
        val ref = plansRef.child(ownerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.getValue(SubscriptionPlan::class.java) }
                    .sortedBy { it.createdAt })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun getPlans(ownerId: String): List<SubscriptionPlan> =
        plansRef.child(ownerId).get().await().children
            .mapNotNull { it.getValue(SubscriptionPlan::class.java) }
            .sortedBy { it.createdAt }

    /** Owner creates or edits a plan; any user may add one to their own account. */
    suspend fun savePlan(plan: SubscriptionPlan): String {
        val id = plan.id.ifBlank { plansRef.child(plan.ownerId).push().key ?: error("no id") }
        val toSave = plan.copy(
            id = id,
            createdAt = if (plan.createdAt == 0L) System.currentTimeMillis() else plan.createdAt
        )
        plansRef.child(plan.ownerId).child(id).setValue(toSave).await()
        usersRef.child(plan.ownerId).child("hasSubscriptionPlans").setValue(true).await()
        return id
    }

    suspend fun deletePlan(ownerId: String, planId: String) {
        plansRef.child(ownerId).child(planId).removeValue().await()
        val remaining = plansRef.child(ownerId).get().await().childrenCount
        if (remaining == 0L) {
            usersRef.child(ownerId).child("hasSubscriptionPlans").setValue(false).await()
        }
    }

    // ---- Subscribing (subscriber side) ----

    fun observeSubscribers(ownerId: String): Flow<List<Subscriber>> = callbackFlow {
        val ref = subscribersRef.child(ownerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.getValue(Subscriber::class.java) }
                    .sortedByDescending { it.since })
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun mySubscriptionTo(ownerId: String, subscriberId: String): Subscriber? =
        subscribersRef.child(ownerId).child(subscriberId).get().await().getValue(Subscriber::class.java)

    /**
     * Subscribes [subscriberId] to [plan] on [plan.ownerId]'s account.
     * Requires a linked card to already be on file (see [linkCard]) — the
     * caller (SubscribeSheet) enforces the "ربط بطاقة أولاً" flow, this just
     * writes the resulting relationship once that step has completed.
     */
    suspend fun subscribe(plan: SubscriptionPlan, subscriberId: String, subscriberIdentifier: String) {
        val entry = Subscriber(
            subscriberId = subscriberId,
            subscriberIdentifier = subscriberIdentifier,
            planId = plan.id,
            planName = plan.name,
            since = System.currentTimeMillis()
        )
        subscribersRef.child(plan.ownerId).child(subscriberId).setValue(entry).await()
        subscribedToRef.child(subscriberId).child(plan.ownerId).setValue(plan.id).await()
    }

    suspend fun unsubscribe(ownerId: String, subscriberId: String) {
        subscribersRef.child(ownerId).child(subscriberId).removeValue().await()
        subscribedToRef.child(subscriberId).child(ownerId).removeValue().await()
    }

    // ---- Payment card (masked summary only — see PaymentCard doc comment) ----

    suspend fun getCard(uid: String): PaymentCard? =
        paymentMethodsRef.child(uid).get().await().getValue(PaymentCard::class.java)

    /**
     * Links a card by storing only its masked summary (brand, last 4,
     * expiry, holder name) — never the full number/CVV, which this method
     * doesn't even accept as parameters. See [PaymentCard]'s doc comment.
     */
    suspend fun linkCard(uid: String, brand: String, last4: String, expiryMonth: Int, expiryYear: Int, holderName: String) {
        val card = PaymentCard(
            uid = uid,
            brand = brand,
            last4 = last4,
            expiryMonth = expiryMonth,
            expiryYear = expiryYear,
            holderName = holderName,
            linkedAt = System.currentTimeMillis()
        )
        paymentMethodsRef.child(uid).setValue(card).await()
        usersRef.child(uid).updateChildren(
            mapOf("linkedCardBrand" to brand, "linkedCardLast4" to last4)
        ).await()
    }

    suspend fun unlinkCard(uid: String) {
        paymentMethodsRef.child(uid).removeValue().await()
        usersRef.child(uid).updateChildren(
            mapOf("linkedCardBrand" to "", "linkedCardLast4" to "")
        ).await()
    }
}
