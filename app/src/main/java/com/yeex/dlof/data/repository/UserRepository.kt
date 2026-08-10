package com.yeex.dlof.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.yeex.dlof.data.model.User
import com.yeex.dlof.util.RecommendationRanking
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Follow terminology: tapping "Tek" on someone else's profile makes the
 * current user a "Teker" of them (i.e. now following); the target's profile
 * shows an updated "Teking" (followers) count. Implemented with two
 * denormalized index maps so both directions are cheap to query:
 *   /tekers/{targetUid}/{followerUid}  = true   (who is teking this user -> followers list)
 *   /tekedBy/{followerUid}/{targetUid} = true   (who this user is a teker of -> following list)
 */
class UserRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usersRef get() = db.getReference("users")
    private val identifiersRef get() = db.getReference("identifiers")
    private val tekersRef get() = db.getReference("tekers")
    private val tekedByRef get() = db.getReference("tekedBy")
    private val verificationRequestsRef get() = db.getReference("verificationRequests")

    suspend fun getUser(uid: String): User? =
        usersRef.child(uid).get().await().getValue(User::class.java)

    /**
     * Prefix search over /identifiers (identifier -> uid), used by
     * [com.yeex.dlof.ui.search.SearchScreen] for plain "@identifier" or bare
     * identifier queries (anything that isn't the "@container.name[].me"
     * syntax handled by [ContainerRepository]). Firebase Realtime Database
     * doesn't support arbitrary substring search, so this relies on
     * lexicographic prefix range queries (startAt/endAt with the Unicode
     * "highest character" sentinel) same as [RoomRepository.searchByName].
     * Case is already normalized because [com.yeex.dlof.util.UsernameValidator]
     * forbids uppercase letters in identifiers.
     */
    suspend fun searchByIdentifierPrefix(prefix: String, limit: Int = 20): List<User> {
        val clean = prefix.removePrefix("@").trim()
        if (clean.isEmpty()) return emptyList()
        val snapshot = identifiersRef.orderByKey()
            .startAt(clean)
            .endAt(clean + "\uf8ff")
            .limitToFirst(limit)
            .get().await()
        val uids = snapshot.children.mapNotNull { it.getValue(String::class.java) }
        return uids.mapNotNull { getUser(it) }
    }

    /**
     * Prefix search over /users ordered by [User.displayNameLower], so
     * typing part of someone's actual name (not their @identifier) also
     * surfaces them in [com.yeex.dlof.ui.search.SearchScreen] — previously
     * only an exact identifier prefix returned any results at all. Case is
     * normalized on both sides ([updateProfile] writes the lowercase
     * mirror, this lowercases the query) since RTDB range queries are
     * case-sensitive. Accounts created/edited before this field existed
     * simply won't match here until they next save their profile — the
     * identifier search above still covers them in the meantime.
     */
    suspend fun searchByDisplayNamePrefix(prefix: String, limit: Int = 20): List<User> {
        val clean = prefix.trim().lowercase()
        if (clean.isEmpty()) return emptyList()
        val snapshot = usersRef.orderByChild("displayNameLower")
            .startAt(clean)
            .endAt(clean + "\uf8ff")
            .limitToFirst(limit)
            .get().await()
        return snapshot.children.mapNotNull { it.getValue(User::class.java) }
    }

    /**
     * Live version of [getUser] — pushes updates whenever /users/{uid} changes,
     * including tekingCount/tekerCount right after [toggleTek] writes them.
     * ProfileScreen uses this instead of a one-shot read so the Tek/Teker
     * counters reflect real Firebase data instead of going stale after a tap.
     */
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val ref = usersRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(User::class.java))
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Cross-device sync for LocaleUtil's in-app language switcher. */
    suspend fun updateLanguage(uid: String, languageCode: String) {
        usersRef.child(uid).child("language").setValue(languageCode).await()
    }

    /** "حساب خاص" toggle in the Settings & Privacy screen. */
    suspend fun updatePrivacy(uid: String, isPrivate: Boolean) {
        usersRef.child(uid).child("isPrivateAccount").setValue(isPrivate).await()
    }

    /** Who may comment on this user's paragraphs — "everyone" | "tekers" | "no_one". */
    suspend fun updateCommentPrivacy(uid: String, value: String) {
        usersRef.child(uid).child("commentPrivacy").setValue(value).await()
    }

    /**
     * Updates the editable profile fields (display name + bio) from the
     * "edit account" bottom sheet in [com.yeex.dlof.ui.profile.ProfileScreen].
     * Also refreshes [User.displayNameLower] so
     * [searchByDisplayNamePrefix] keeps finding this account by its current
     * name.
     */
    suspend fun updateProfile(uid: String, displayName: String, bio: String) {
        usersRef.child(uid).child("displayName").setValue(displayName).await()
        usersRef.child(uid).child("displayNameLower").setValue(displayName.lowercase()).await()
        usersRef.child(uid).child("bio").setValue(bio).await()
    }

    /**
     * Stores the account icon as a downscaled Base64 JPEG directly on the user
     * node (same "no Firebase Storage / free plan only" approach used for
     * paragraph media — see [com.yeex.dlof.util.MediaBase64]).
     */
    suspend fun updateProfileIcon(uid: String, base64: String) {
        usersRef.child(uid).child("profileIconUrl").setValue(base64).await()
    }

    /**
     * Saves a picked-and-cropped banner image (see [com.yeex.dlof.util.MediaBase64.encodeBanner])
     * as the profile banner. A banner is either an image or a video link,
     * never both, so this clears any previously-set [bannerVideoUrl].
     */
    suspend fun updateBannerImage(uid: String, base64: String) {
        usersRef.child(uid).updateChildren(
            mapOf("bannerUrl" to base64, "bannerVideoUrl" to "")
        ).await()
    }

    /**
     * Saves a link to any video on the internet as the profile banner
     * instead of an image. Clears any previously-set [bannerUrl] image so
     * the two never both hold a value at once.
     */
    suspend fun updateBannerVideoUrl(uid: String, url: String) {
        usersRef.child(uid).updateChildren(
            mapOf("bannerVideoUrl" to url, "bannerUrl" to "")
        ).await()
    }

    /**
     * Updates the "حساب أعمال" (business account) fields — category, contact
     * info, and links — from [com.yeex.dlof.ui.profile.EditAccountSheet]'s
     * business section. A partial [updateChildren] rather than a full
     * [updateProfile]-style set so it can be saved independently of the
     * display-name/bio fields.
     */
    suspend fun updateBusinessAccount(
        uid: String,
        accountType: String,
        businessCategory: String,
        businessPhone: String,
        businessEmail: String,
        businessLinks: Map<String, String>
    ) {
        usersRef.child(uid).updateChildren(
            mapOf(
                "accountType" to accountType,
                "businessCategory" to businessCategory,
                "businessPhone" to businessPhone,
                "businessEmail" to businessEmail,
                "businessLinks" to businessLinks
            )
        ).await()
    }

    suspend fun isTeking(currentUid: String, targetUid: String): Boolean =
        tekedByRef.child(currentUid).child(targetUid).get().await().exists()

    /**
     * Every uid this account currently teks (follows) — the raw follow-graph
     * edge set that both [com.yeex.dlof.util.FeedRanking] (affinity boost)
     * and [suggestedAccounts] (friend-of-a-friend walk) are built on.
     */
    suspend fun followingUids(uid: String): Set<String> =
        tekedByRef.child(uid).get().await().children.mapNotNull { it.key }.toSet()

    /**
     * "قد تعرفهم" (people you may know): a friend-of-a-friend suggestion —
     * accounts teked by the accounts *this* user already teks, excluding
     * the user themself and anyone already followed, ranked by
     * [RecommendationRanking] on mutual-tek count (how many of the user's
     * own teks also tek that candidate).
     *
     * [expandLimit] caps how many of the viewer's own teks are walked out
     * from, bounding the number of Realtime Database reads for accounts
     * that follow thousands of people — plenty of signal for a suggestion
     * list comes from the most relevant edge of the graph, not the whole
     * thing.
     */
    suspend fun suggestedAccounts(currentUid: String, limit: Int = 10, expandLimit: Int = 20): List<User> {
        val following = followingUids(currentUid)
        if (following.isEmpty()) return emptyList()

        val mutualCounts = mutableMapOf<String, Int>()
        for (followedUid in following.take(expandLimit)) {
            val theirFollowing = followingUids(followedUid)
            for (candidateUid in theirFollowing) {
                if (candidateUid == currentUid || candidateUid in following) continue
                mutualCounts[candidateUid] = (mutualCounts[candidateUid] ?: 0) + 1
            }
        }

        return RecommendationRanking.rank(mutualCounts, limit).mapNotNull { getUser(it) }
    }

    /** Toggles follow state; returns the new "is following" boolean. */
    suspend fun toggleTek(currentUid: String, targetUid: String): Boolean {
        val alreadyFollowing = isTeking(currentUid, targetUid)
        return if (alreadyFollowing) {
            tekedByRef.child(currentUid).child(targetUid).removeValue().await()
            tekersRef.child(targetUid).child(currentUid).removeValue().await()
            adjustCounts(currentUid, targetUid, delta = -1)
            false
        } else {
            tekedByRef.child(currentUid).child(targetUid).setValue(true).await()
            tekersRef.child(targetUid).child(currentUid).setValue(true).await()
            adjustCounts(currentUid, targetUid, delta = 1)
            true
        }
    }

    private suspend fun adjustCounts(currentUid: String, targetUid: String, delta: Int) {
        usersRef.child(targetUid).child("tekingCount").get().await().let { snap ->
            val v = (snap.getValue(Long::class.java) ?: 0L) + delta
            usersRef.child(targetUid).child("tekingCount").setValue(v.coerceAtLeast(0)).await()
        }
        usersRef.child(currentUid).child("tekerCount").get().await().let { snap ->
            val v = (snap.getValue(Long::class.java) ?: 0L) + delta
            usersRef.child(currentUid).child("tekerCount").setValue(v.coerceAtLeast(0)).await()
        }
    }

    /**
     * Submits self-reported follower counts from other platforms + an ID
     * document reference for admin review. Auto-eligibility (>20k on any
     * listed platform) just flags the request as high-priority; a human
     * admin still confirms and sets `verified = true`, since we cannot
     * verify third-party follower counts programmatically without those
     * platforms' APIs.
     */
    suspend fun submitVerificationRequest(
        uid: String,
        externalFollowerCounts: Map<String, Long>,
        note: String
    ) {
        val eligible = externalFollowerCounts.values.any { it >= 20_000 }
        val payload = mapOf(
            "uid" to uid,
            "externalFollowerCounts" to externalFollowerCounts,
            "note" to note,
            "autoEligible" to eligible,
            "status" to "pending",
            "submittedAt" to System.currentTimeMillis()
        )
        verificationRequestsRef.child(uid).setValue(payload).await()
        usersRef.child(uid).child("externalFollowerCounts").setValue(externalFollowerCounts).await()
    }
}
