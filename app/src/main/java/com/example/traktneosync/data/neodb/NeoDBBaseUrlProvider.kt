package com.example.traktneosync.data.neodb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeoDBBaseUrlProvider @Inject constructor() {
    @Volatile
    var baseUrl: String = "https://neodb.social/"
}
