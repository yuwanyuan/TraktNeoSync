package com.example.traktneosync.data.tmdb

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

interface TmdbApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Long
    ): TmdbMovieDetail

    @GET("tv/{series_id}")
    suspend fun getTvDetail(
        @Path("series_id") seriesId: Long
    ): TmdbTvDetail
}

data class TmdbMovieDetail(
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("release_date") val releaseDate: String = "",
)

data class TmdbTvDetail(
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("name") val name: String = "",
    @SerializedName("first_air_date") val firstAirDate: String = "",
)
