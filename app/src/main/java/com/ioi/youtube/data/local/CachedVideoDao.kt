package com.ioi.youtube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedVideoDao {

    @Query("SELECT * FROM cached_videos WHERE sourceKey = :sourceKey ORDER BY rowid ASC")
    suspend fun getBySource(sourceKey: String): List<CachedVideoEntity>

    @Query("SELECT * FROM cached_videos WHERE sourceKey = :sourceKey ORDER BY rowid ASC")
    fun observeBySource(sourceKey: String): Flow<List<CachedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<CachedVideoEntity>)

    @Query("DELETE FROM cached_videos WHERE sourceKey = :sourceKey")
    suspend fun deleteBySource(sourceKey: String)

    @Query("DELETE FROM cached_videos WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Transaction
    suspend fun replaceSource(sourceKey: String, videos: List<CachedVideoEntity>) {
        deleteBySource(sourceKey)
        insertAll(videos)
    }
}
