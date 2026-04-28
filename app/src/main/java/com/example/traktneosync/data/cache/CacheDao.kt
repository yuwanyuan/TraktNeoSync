package com.example.traktneosync.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CacheDao {

    // ========== 列表缓存 ==========

    @Query("SELECT * FROM trakt_cache WHERE type = :type AND status = :status ORDER BY cachedAt DESC")
    suspend fun getCachedItems(type: String, status: String): List<TraktCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TraktCacheEntity>)

    @Query("DELETE FROM trakt_cache WHERE type = :type AND status = :status")
    suspend fun clearItems(type: String, status: String)

    @Transaction
    suspend fun replaceItems(type: String, status: String, items: List<TraktCacheEntity>) {
        clearItems(type, status)
        insertItems(items)
    }

    @Query("SELECT COUNT(*) FROM trakt_cache WHERE type = :type AND status = :status")
    suspend fun getItemCount(type: String, status: String): Int

    // ========== 海报缓存 ==========

    @Query("SELECT * FROM poster_cache WHERE tmdbId = :tmdbId")
    suspend fun getPoster(tmdbId: Long): PosterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosters(items: List<PosterCacheEntity>)

    @Query("DELETE FROM poster_cache WHERE tmdbId = :tmdbId")
    suspend fun clearPoster(tmdbId: Long)

    // ========== 同步缓存 ==========

    @Query("SELECT * FROM sync_cache ORDER BY cachedAt DESC")
    suspend fun getSyncCache(): List<SyncCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItems(items: List<SyncCacheEntity>)

    @Query("DELETE FROM sync_cache")
    suspend fun clearSyncCache()

    @Transaction
    suspend fun replaceSyncCache(items: List<SyncCacheEntity>) {
        clearSyncCache()
        insertSyncItems(items)
    }

    // ========== 全局清理 ==========

    @Query("DELETE FROM trakt_cache")
    suspend fun clearAllItems()

    @Query("DELETE FROM poster_cache")
    suspend fun clearAllPosters()

    @Transaction
    suspend fun clearAllCache() {
        clearAllItems()
        clearAllPosters()
        clearSyncCache()
    }
}
