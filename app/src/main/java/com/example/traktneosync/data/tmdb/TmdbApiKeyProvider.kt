package com.example.traktneosync.data.tmdb

import com.example.traktneosync.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbApiKeyProvider @Inject constructor() {
    @Volatile
    var apiKey: String = BuildConfig.TMDB_API_KEY.takeIf { it.isNotEmpty() } ?: ""
}
