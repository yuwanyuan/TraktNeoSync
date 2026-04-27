package com.example.traktneosync.data.trakt

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TraktApiService {
    
    // ========== OAuth ==========
    
    @POST("oauth/device/code")
    suspend fun getDeviceCode(
        @Body request: TraktDeviceCodeRequest
    ): TraktDeviceCodeResponse
    
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Body request: TraktTokenRequest
    ): TraktTokenResponse
    
    // ========== 观看历史 ==========
    
    @GET("sync/watched/movies")
    suspend fun getWatchedMovies(
        @Header("Authorization") token: String,
        @Query("extended") extended: String = "full"
    ): List<TraktWatchedItem>
    
    @GET("sync/watched/shows")
    suspend fun getWatchedShows(
        @Header("Authorization") token: String,
        @Query("extended") extended: String = "full"
    ): List<TraktWatchedItem>
    
    // ========== 观看清单 ==========
    
    @GET("sync/watchlist/movies")
    suspend fun getMovieWatchlist(
        @Header("Authorization") token: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktWatchlistItem>
    
    @GET("sync/watchlist/shows")
    suspend fun getShowWatchlist(
        @Header("Authorization") token: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktWatchlistItem>
    
    // ========== 观看进度 ==========
    
    @GET("sync/playback")
    suspend fun getPlaybackProgress(
        @Header("Authorization") token: String,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktPlaybackItem>
    
    // ========== 添加/删除历史 ==========
    
    @POST("sync/history")
    suspend fun addToHistory(
        @Header("Authorization") token: String,
        @Body request: TraktSyncRequest
    ): TraktSyncResponse
    
    @POST("sync/history/remove")
    suspend fun removeFromHistory(
        @Header("Authorization") token: String,
        @Body request: TraktSyncRequest
    ): TraktSyncResponse
    
    // ========== 用户资料 ==========
    
    @GET("users/me")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): TraktUser
}

data class TraktUser(
    val username: String,
    @SerializedName("private") val isPrivate: Boolean,
    val name: String,
    val vip: Boolean,
    @SerializedName("ids") val ids: TraktUserIds
)

data class TraktUserIds(
    val slug: String
)

data class TraktSyncRequest(
    val movies: List<TraktSyncMovie>? = null,
    val shows: List<TraktSyncShow>? = null,
    val episodes: List<TraktSyncEpisode>? = null
)

data class TraktSyncMovie(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIds? = null,
    @SerializedName("watched_at") val watchedAt: String? = null
)

data class TraktSyncShow(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIds? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
    val seasons: List<TraktSyncSeason>? = null
)

data class TraktSyncSeason(
    val number: Int,
    val episodes: List<TraktSyncEpisode>
)

data class TraktSyncEpisode(
    val season: Int? = null,
    val number: Int? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
    val ids: TraktIds? = null
)

data class TraktSyncResponse(
    val added: TraktSyncCounts,
    val existing: TraktSyncCounts,
    val not_found: TraktSyncNotFound
)

data class TraktSyncCounts(
    val movies: Int = 0,
    val episodes: Int = 0
)

data class TraktSyncNotFound(
    val movies: List<TraktSyncMovie> = emptyList(),
    val shows: List<TraktSyncShow> = emptyList(),
    val episodes: List<TraktSyncEpisode> = emptyList()
)
