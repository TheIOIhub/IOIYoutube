package com.ioi.youtube.data.mapper

import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.data.local.CachedVideoDetailsEntity
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoSummary

fun CachedVideoEntity.toDomain(): VideoSummary = VideoSummary(
    id = id,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
    uploadedAt = null,
)

fun VideoSummary.toEntity(sourceKey: String, cachedAt: Long): CachedVideoEntity =
    CachedVideoEntity(
        id = id,
        sourceKey = sourceKey,
        title = title,
        uploaderName = uploaderName,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadDate = uploadDate,
        cachedAt = cachedAt,
    )

fun CachedVideoDetailsEntity.toDomain(): VideoDetails = VideoDetails(
    id = id,
    title = title,
    uploaderName = uploaderName,
    uploaderAvatarUrl = null,
    description = description,
    viewCount = viewCount,
    likeCount = likeCount,
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    recommendedVideos = emptyList(),
)

fun VideoDetails.toDetailsEntity(cachedAt: Long): CachedVideoDetailsEntity =
    CachedVideoDetailsEntity(
        id = id,
        title = title,
        uploaderName = uploaderName,
        description = description,
        viewCount = viewCount,
        likeCount = likeCount,
        uploadDate = uploadDate,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        cachedAt = cachedAt,
    )
