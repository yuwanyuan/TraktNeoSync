package com.example.traktneosync

import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TraktNeoSyncApp : Application() {
    companion object {
        private const val TAG = "TraktNeoSyncApp"
        private const val PREFS_NAME = "crash_logs"
        private const val KEY_LAST_CRASH = "last_crash"
    }

    override fun onCreate() {
        super.onCreate()

        // 全局未捕获异常处理器 - 记录崩溃信息到 SharedPreferences
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            saveCrashLog(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "Application onCreate - Hilt initialized successfully")
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val stackTrace = Log.getStackTraceString(throwable)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_CRASH, stackTrace).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    fun getLastCrash(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_CRASH, null)
    }

    fun clearLastCrash() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_CRASH).apply()
    }
}
