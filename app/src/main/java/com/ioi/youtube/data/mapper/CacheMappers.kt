package com.ioi.youtube.data.mapper

import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.domain.model.VideoSummary

fun CachedVideoEntity.toDomain(): VideoSummary = VideoSummary(
    id = id,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
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
