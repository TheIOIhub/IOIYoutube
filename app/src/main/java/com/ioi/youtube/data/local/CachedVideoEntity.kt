package com.ioi.youtube.data.local

import androidx.room.Entity

/**
 * Кеш списку відео (головна стрічка та результати пошуку).
 * sourceKey = "home" або "search:<query>" — одним полем покриває обидва випадки.
 */
@Entity(tableName = "cached_videos", primaryKeys = ["sourceKey", "id"])
data class CachedVideoEntity(
    val id: String,
    val sourceKey: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
    val cachedAt: Long,
)
