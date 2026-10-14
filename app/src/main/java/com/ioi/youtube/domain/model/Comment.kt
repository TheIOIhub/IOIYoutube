package com.ioi.youtube.domain.model

data class Comment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long,
    val publishedTime: String?,
    val heartedByUploader: Boolean,
    val pinned: Boolean,
)
