package com.example.traktneosync.data.omdb

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApiService {

    @GET("/")
    suspend fun getByImdbId(
        @Query("i") imdbId: String,
        @Query("plot") plot: String = "short"
    ): OmdbResponse
}

data class OmdbResponse(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Year") val year: String? = null,
    @SerializedName("Rated") val rated: String? = null,
    @SerializedName("Released") val released: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("imdbVotes") val imdbVotes: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Response") val response: String = "False",
    @SerializedName("Error") val error: String? = null,
)
