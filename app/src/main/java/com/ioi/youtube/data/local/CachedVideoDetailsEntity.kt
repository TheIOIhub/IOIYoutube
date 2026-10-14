package com.ioi.youtube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кеш метаданих відео (спека §5.4: "результати пошуку та метадані відео").
 * Рекомендації та коментарі в кеш не пишуть — вони завжди завантажуються з мережі.
 */
@Entity(tableName = "cached_video_details")
data class CachedVideoDetailsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val uploaderName: String,
    val description: String,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String?,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val cachedAt: Long,
)
