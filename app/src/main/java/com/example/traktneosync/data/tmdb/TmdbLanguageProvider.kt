package com.example.traktneosync.data.tmdb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbLanguageProvider @Inject constructor() {
    @Volatile
    var language: String = "zh-CN"
}
