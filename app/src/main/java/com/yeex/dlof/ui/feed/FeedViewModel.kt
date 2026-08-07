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

    // paragraphId -> current user's reaction, so ParagraphCard can highlight
    // the like/dislike button that's already active without a lookup per frame.
    private val _reactions = MutableStateFlow<Map<String, Reaction?>>(emptyMap())
    val reactions: StateFlow<Map<String, Reaction?>> = _reactions

    init {
        viewModelScope.launch {
            repo.observeParagraphs(roomId).collect { raw ->
                _paragraphs.value = FeedRanking.rankForFeed(raw, System.currentTimeMillis())
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

    fun like(paragraphId: String) {
        val uid = authRepo.currentUid() ?: return
        viewModelScope.launch {
            repo.toggleLike(paragraphId, uid)
            _reactions.value = _reactions.value.toMutableMap().apply {
                this[paragraphId] = repo.getReaction(paragraphId, uid)
            }
        }
    }

    fun dislike(paragraphId: String) {
        val uid = authRepo.currentUid() ?: return
        viewModelScope.launch {
            repo.toggleDislike(paragraphId, uid)
            _reactions.value = _reactions.value.toMutableMap().apply {
                this[paragraphId] = repo.getReaction(paragraphId, uid)
            }
        }
    }
}
