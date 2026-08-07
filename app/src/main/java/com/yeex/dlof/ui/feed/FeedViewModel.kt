package com.yeex.dlof.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.data.repository.Reaction
import com.yeex.dlof.util.FeedRanking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repo: ParagraphRepository = ParagraphRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
    private val roomId: String? = null // null = global feed
) : ViewModel() {

    private val _paragraphs = MutableStateFlow<List<Paragraph>>(emptyList())
    val paragraphs: StateFlow<List<Paragraph>> = _paragraphs

    // True until the first Firebase snapshot arrives, so FeedScreen can show
    // a skeleton placeholder instead of prematurely claiming the feed is empty.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // paragraphId -> current user's reaction, so ParagraphCard can highlight
    // the like/dislike button that's already active without a lookup per frame.
    private val _reactions = MutableStateFlow<Map<String, Reaction?>>(emptyMap())
    val reactions: StateFlow<Map<String, Reaction?>> = _reactions

    init {
        viewModelScope.launch {
            repo.observeParagraphs(roomId).collect { raw ->
                _paragraphs.value = FeedRanking.rankForFeed(raw, System.currentTimeMillis())
                _isLoading.value = false
                loadReactionsFor(raw)
            }
        }
    }

    private fun loadReactionsFor(items: List<Paragraph>) {
        val uid = authRepo.currentUid() ?: return
        viewModelScope.launch {
            val updated = _reactions.value.toMutableMap()
            for (p in items) {
                if (p.id !in updated) {
                    updated[p.id] = repo.getReaction(p.id, uid)
                }
            }
            _reactions.value = updated
        }
    }

    /**
     * Applies a like/dislike count delta to the in-memory list immediately
     * (optimistic UI) so the counter on screen reacts the instant the user
     * taps, rather than waiting for the round-trip write + listener refire.
     * The real Firebase listener in [repo].observeParagraphs still owns the
     * source of truth and will reconcile this value right after.
     */
    private fun applyOptimisticDelta(paragraphId: String, likeDelta: Int, dislikeDelta: Int) {
        _paragraphs.value = _paragraphs.value.map { p ->
            if (p.id == paragraphId) {
                p.copy(
                    likeCount = (p.likeCount + likeDelta).coerceAtLeast(0),
                    dislikeCount = (p.dislikeCount + dislikeDelta).coerceAtLeast(0)
                )
            } else p
        }
    }

    fun like(paragraphId: String) {
        val uid = authRepo.currentUid() ?: return
        val current = _reactions.value[paragraphId]
        val goingToLike = current != Reaction.LIKE

        // Optimistic local update — mirrors exactly what ParagraphRepository.setReaction
        // will persist, so the on-screen count is real and instant, not a fake bump.
        when {
            goingToLike && current == Reaction.DISLIKE -> applyOptimisticDelta(paragraphId, likeDelta = 1, dislikeDelta = -1)
            goingToLike -> applyOptimisticDelta(paragraphId, likeDelta = 1, dislikeDelta = 0)
            else -> applyOptimisticDelta(paragraphId, likeDelta = -1, dislikeDelta = 0)
        }
        _reactions.value = _reactions.value.toMutableMap().apply {
            this[paragraphId] = if (goingToLike) Reaction.LIKE else null
        }

        viewModelScope.launch {
            val committed = repo.toggleLike(paragraphId, uid)
            // Reconcile with the exact value the atomic transaction committed
            // (covers the rare case where another device/tab raced this one).
            _reactions.value = _reactions.value.toMutableMap().apply {
                this[paragraphId] = committed
            }
        }
    }

    fun dislike(paragraphId: String) {
        val uid = authRepo.currentUid() ?: return
        val current = _reactions.value[paragraphId]
        val goingToDislike = current != Reaction.DISLIKE

        when {
            goingToDislike && current == Reaction.LIKE -> applyOptimisticDelta(paragraphId, likeDelta = -1, dislikeDelta = 1)
            goingToDislike -> applyOptimisticDelta(paragraphId, likeDelta = 0, dislikeDelta = 1)
            else -> applyOptimisticDelta(paragraphId, likeDelta = 0, dislikeDelta = -1)
        }
        _reactions.value = _reactions.value.toMutableMap().apply {
            this[paragraphId] = if (goingToDislike) Reaction.DISLIKE else null
        }

        viewModelScope.launch {
            val committed = repo.toggleDislike(paragraphId, uid)
            _reactions.value = _reactions.value.toMutableMap().apply {
                this[paragraphId] = committed
            }
        }
    }
}
