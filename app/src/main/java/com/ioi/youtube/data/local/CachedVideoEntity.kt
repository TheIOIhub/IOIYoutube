package com.ioi.youtube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кеш списку відео (головна стрічка та результати пошуку).
 * sourceKey = "home" або "search:<query>" — одним полем покриває обидва випадки.
 */
@Entity(tableName = "cached_videos")
data class CachedVideoEntity(
    @PrimaryKey val id: String,
    val sourceKey: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
    val cachedAt: Long,
)
