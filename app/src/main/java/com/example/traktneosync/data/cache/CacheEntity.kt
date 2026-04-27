package com.example.traktneosync.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 缓存Trakt电影/剧集列表项，支持增量更新
 */
@Entity(tableName = "trakt_cache")
data class TraktCacheEntity(
    @PrimaryKey
    val id: String, // "movie_watched_{traktId}" 或 "show_watchlist_{traktId}" 等组合键
    val title: String,
    val year: Int?,
    val type: String, // "movie" or "show"
    val status: String, // "watched" or "watchlist"
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val lastWatchedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * 缓存TMDB海报URL，避免重复请求
 */
@Entity(tableName = "poster_cache")
data class PosterCacheEntity(
    @PrimaryKey
    val tmdbId: Long,
    val posterPath: String?, // 原始poster_path，可能为null
    val cachedAt: Long = System.currentTimeMillis()
)
