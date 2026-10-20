package com.ioi.youtube.domain.model

data class VideoSummary(
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
    /** Реальна дата публікації, epoch millis. У кеш не пишеться. */
    val uploadedAt: Long? = null,
)
