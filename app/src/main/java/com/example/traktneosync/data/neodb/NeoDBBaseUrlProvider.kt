package com.example.traktneosync.data.neodb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeoDBBaseUrlProvider @Inject constructor() {
    @Volatile
    private var _baseUrl: String = "https://neodb.social/"

    var baseUrl: String
        @Synchronized get() = _baseUrl
        @Synchronized set(value) {
            _baseUrl = if (value.isBlank()) "https://neodb.social/"
                       else if (!value.endsWith("/")) "$value/"
                       else value
        }
}
