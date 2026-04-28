package com.example.traktneosync.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trakt_cache")
data class TraktCacheEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val year: Int?,
    val type: String,
    val status: String,
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val lastWatchedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "poster_cache")
data class PosterCacheEntity(
    @PrimaryKey
    val tmdbId: Long,
    val posterPath: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_cache")
data class SyncCacheEntity(
    @PrimaryKey
    val uuid: String,
    val title: String,
    val year: Int?,
    val type: String,
    val status: String,
    val shelfType: String,
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val traktId: Long = 0,
    val imdbId: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
