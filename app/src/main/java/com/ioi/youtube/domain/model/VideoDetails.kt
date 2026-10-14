package com.ioi.youtube.domain.model

data class VideoDetails(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val description: String,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String?,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val recommendedVideos: List<VideoSummary> = emptyList(),
)
