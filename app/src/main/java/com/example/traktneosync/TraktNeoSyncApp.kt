package com.example.traktneosync

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TraktNeoSyncApp : Application() {
    companion object {
        private const val TAG = "TraktNeoSyncApp"
        private const val PREFS_NAME = "crash_logs"
        private const val KEY_LAST_CRASH = "last_crash"
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 初始化 AppLogger 的 Application Context
        AppLogger.init(this)

        // 全局未捕获异常处理器 - 记录崩溃信息到 SharedPreferences
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            saveCrashLog(throwable)
            AppLogger.log("未捕获异常 (线程: ${thread.name})", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 从 DataStore 恢复 TMDB API Key 和语言设置到 Provider（解决重启后图片加载失败）
        applicationScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@TraktNeoSyncApp,
                    AppInitializerEntryPoint::class.java
                )
                val authRepo = entryPoint.authRepository()
                authRepo.initTmdbKey()
                authRepo.initLanguage()
                Log.d(TAG, "TMDB API Key & Language initialized from DataStore")
                AppLogger.log("App启动: TMDB API Key和语言设置已从DataStore恢复")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init TMDB key/language on startup", e)
                AppLogger.log("App启动: 恢复TMDB设置失败", e)
            }
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppInitializerEntryPoint {
    fun authRepository(): AuthRepository
}
