package com.example.traktneosync.data.omdb

import com.example.traktneosync.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmdbApiKeyProvider @Inject constructor() {
    @Volatile
    private var _apiKey: String = BuildConfig.OMDB_API_KEY.takeIf { it.isNotEmpty() } ?: ""

    var apiKey: String
        @Synchronized get() = _apiKey
        @Synchronized set(value) { _apiKey = value }
}
