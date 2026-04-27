package com.example.traktneosync.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TraktCacheEntity::class, PosterCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
