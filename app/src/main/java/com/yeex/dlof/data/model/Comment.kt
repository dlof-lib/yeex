package com.yeex.dlof.data.model

data class Comment(
    val id: String = "",
    val paragraphId: String = "",
    val authorId: String = "",
    val authorIdentifier: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)
