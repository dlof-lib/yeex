package com.yeex.dlof.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeex.dlof.data.model.Paragraph
import com.yeex.dlof.data.repository.ParagraphRepository
import com.yeex.dlof.util.FeedRanking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repo: ParagraphRepository = ParagraphRepository(),
    private val roomId: String? = null // null = global feed
) : ViewModel() {

    private val _paragraphs = MutableStateFlow<List<Paragraph>>(emptyList())
    val paragraphs: StateFlow<List<Paragraph>> = _paragraphs

    init {
        viewModelScope.launch {
            repo.observeParagraphs(roomId).collect { raw ->
                _paragraphs.value = FeedRanking.rankForFeed(raw, System.currentTimeMillis())
            }
        }
    }

    fun like(paragraphId: String) = viewModelScope.launch { repo.like(paragraphId) }
    fun dislike(paragraphId: String) = viewModelScope.launch { repo.dislike(paragraphId) }
}
