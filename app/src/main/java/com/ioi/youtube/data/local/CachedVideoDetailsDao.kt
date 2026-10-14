package com.ioi.youtube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedVideoDetailsDao {

    @Query("SELECT * FROM cached_video_details WHERE id = :videoId LIMIT 1")
    suspend fun getById(videoId: String): CachedVideoDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: CachedVideoDetailsEntity)

    @Query("DELETE FROM cached_video_details WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
