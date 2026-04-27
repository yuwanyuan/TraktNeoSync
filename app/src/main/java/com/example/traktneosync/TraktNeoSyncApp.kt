package com.example.traktneosync

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TraktNeoSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
