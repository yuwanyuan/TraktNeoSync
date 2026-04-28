package com.example.traktneosync.data.tmdb

import com.example.traktneosync.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbApiKeyProvider @Inject constructor() {
    @Volatile
    private var _apiKey: String = BuildConfig.TMDB_API_KEY.takeIf { it.isNotEmpty() } ?: ""

    var apiKey: String
        @Synchronized get() = _apiKey
        @Synchronized set(value) { _apiKey = value }
}
