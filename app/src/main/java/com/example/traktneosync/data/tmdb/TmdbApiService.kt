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

    @GET("movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: Long
    ): TmdbImagesResponse

    @GET("tv/{series_id}/images")
    suspend fun getTvImages(
        @Path("series_id") seriesId: Long
    ): TmdbImagesResponse
}

data class TmdbMovieDetail(
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("release_date") val releaseDate: String = "",
    @SerializedName("overview") val overview: String? = null,
)

data class TmdbTvDetail(
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("name") val name: String = "",
    @SerializedName("first_air_date") val firstAirDate: String = "",
    @SerializedName("overview") val overview: String? = null,
)

data class TmdbImagesResponse(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("backdrops") val backdrops: List<TmdbImageItem>? = null,
    @SerializedName("posters") val posters: List<TmdbImageItem>? = null,
)

data class TmdbImageItem(
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("vote_average") val voteAverage: Float = 0f,
    @SerializedName("vote_count") val voteCount: Int = 0,
)
