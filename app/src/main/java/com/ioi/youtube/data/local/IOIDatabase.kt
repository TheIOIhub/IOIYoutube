package com.ioi.youtube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedVideoEntity::class, CachedVideoDetailsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IOIDatabase : RoomDatabase() {
    abstract fun cachedVideoDao(): CachedVideoDao
    abstract fun cachedVideoDetailsDao(): CachedVideoDetailsDao
}
