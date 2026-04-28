package com.example.traktneosync

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.proxy.ProxyProvider
import com.example.traktneosync.data.proxy.ProxyRepository
import com.example.traktneosync.util.AppLogger
import com.example.traktneosync.util.LogLevel
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

        AppLogger.init(this)
        AppLogger.setLogLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO)
        AppLogger.setFileLogLevel(LogLevel.INFO)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.error(TAG, "未捕获异常", throwable, mapOf("thread" to thread.name))
            saveCrashLog(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        applicationScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@TraktNeoSyncApp,
                    AppInitializerEntryPoint::class.java
                )
                val authRepo = entryPoint.authRepository()
                val proxyRepo = entryPoint.proxyRepository()
                val proxyProv = entryPoint.proxyProvider()
                authRepo.initTmdbKey()
                authRepo.initLanguage()
                val proxyConfig = proxyRepo.proxyConfig.first()
                proxyProv.config = proxyConfig
                AppLogger.info(TAG, "TMDB设置已从DataStore恢复", mapOf("proxyEnabled" to proxyConfig.isEnabled.toString()))
            } catch (e: Exception) {
                AppLogger.error(TAG, "恢复TMDB设置失败", e)
            }
        }

        AppLogger.info(TAG, "Application onCreate完成")
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val stackTrace = Log.getStackTraceString(throwable)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_CRASH, stackTrace).apply()
        } catch (e: Exception) {
            AppLogger.error(TAG, "保存崩溃日志失败", e)
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
    fun proxyRepository(): ProxyRepository
    fun proxyProvider(): ProxyProvider
}
