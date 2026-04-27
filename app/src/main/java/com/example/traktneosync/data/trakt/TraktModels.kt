package com.example.traktneosync.data.trakt

import com.google.gson.annotations.SerializedName

// ========== Trakt 观看状态模型 ==========

data class TraktWatchedItem(
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null,
    @SerializedName("last_watched_at") val lastWatchedAt: String? = null,
    @SerializedName("plays") val plays: Int = 0
)

data class TraktMovie(
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds
)

data class TraktShow(
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds
)

data class TraktIds(
    @SerializedName("trakt") val trakt: Long,
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("tmdb") val tmdb: Long? = null,
    @SerializedName("tvdb") val tvdb: Long? = null
)

data class TraktWatchlistItem(
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null,
    @SerializedName("rank") val rank: Int = 0,
    @SerializedName("listed_at") val listedAt: String
)

data class TraktHistoryItem(
    @SerializedName("id") val id: Long,
    @SerializedName("watched_at") val watchedAt: String,
    @SerializedName("action") val action: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null,
    @SerializedName("episode") val episode: TraktEpisode? = null
)

data class TraktEpisode(
    @SerializedName("title") val title: String,
    @SerializedName("ids") val ids: TraktIds,
    @SerializedName("season") val season: Int,
    @SerializedName("number") val number: Int
)

data class TraktPlaybackItem(
    @SerializedName("progress") val progress: Double,
    @SerializedName("paused_at") val pausedAt: String,
    @SerializedName("type") val type: String,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("episode") val episode: TraktEpisode? = null,
    @SerializedName("show") val show: TraktShow? = null
)

// ========== OAuth Token ==========

data class TraktTokenRequest(
    @SerializedName("code") val code: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("redirect_uri") val redirectUri: String = "traktneosync://trakt",
    @SerializedName("grant_type") val grantType: String = "authorization_code"
)

data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("scope") val scope: String,
    @SerializedName("created_at") val createdAt: Long
)

data class TraktDeviceCodeRequest(
    @SerializedName("client_id") val clientId: String
)

data class TraktDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_url") val verificationUrl: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("interval") val interval: Int
)
