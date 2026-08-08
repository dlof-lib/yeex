package com.yeex.dlof.data.model

data class Comment(
    val id: String = "",
    val paragraphId: String = "",
    val authorId: String = "",
    val authorIdentifier: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val parentId: String = "",  // "" for a top-level comment; a comment id for a threaded reply
    val likeCount: Long = 0
)
