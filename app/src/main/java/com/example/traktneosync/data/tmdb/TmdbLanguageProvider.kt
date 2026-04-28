package com.example.traktneosync.data.tmdb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbLanguageProvider @Inject constructor() {
    @Volatile
    private var _language: String = "zh-CN"

    var language: String
        @Synchronized get() = _language
        @Synchronized set(value) { _language = value }
}
