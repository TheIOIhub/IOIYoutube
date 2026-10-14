package com.ioi.youtube.data.model

/**
 * "Сірі" моделі даних із NewPipe Extractor.
 * Відділені від домену, щоб маппери бути чистими й тестованими без мережі.
 */
data class RawVideoSummary(
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
)

data class RawVideoDetails(
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
    val recommended: List<RawVideoSummary>,
)

data class RawComment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long,
    val publishedTime: String?,
    val heartedByUploader: Boolean,
    val pinned: Boolean,
)

data class RawVideoStream(
    val url: String,
    val resolution: String,
    val format: String,
)
