package com.example.traktneosync.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FILE_NAME = "app_crash_log.txt"
    private const val MAX_LOG_SIZE = 500 * 1024 // 500KB

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException("AppLogger not initialized. Call AppLogger.init(context) in Application.onCreate()")
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    /** 带Context的日志（兼容旧代码） */
    fun log(context: Context, message: String, throwable: Throwable? = null) {
        doLog(context, message, throwable)
    }

    /** 不带Context的日志（推荐，需先调用init） */
    fun log(message: String, throwable: Throwable? = null) {
        val ctx = appContext
        if (ctx != null) {
            doLog(ctx, message, throwable)
        } else {
            Log.w(TAG, "AppLogger not initialized, log to logcat only: $message")
            if (throwable != null) Log.w(TAG, throwable)
        }
    }

    private fun doLog(context: Context, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val stackTrace = throwable?.let {
            "\nException: ${it.javaClass.name}: ${it.message}\n" +
            it.stackTraceToString()
        } ?: ""
        val entry = "[$timestamp] $message$stackTrace\n\n"

        try {
            val logFile = getLogFile(context)
            // 如果日志文件过大，清空旧内容
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.writeText("[日志已重置，旧内容超过 500KB]\n\n")
            }
            logFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    fun getLogContent(context: Context): String {
        return try {
            val logFile = getLogFile(context)
            if (logFile.exists()) {
                logFile.readText().take(10000) // 最多返回 10KB
            } else {
                "暂无日志"
            }
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            getLogFile(context).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log: ${e.message}")
        }
    }

    fun setupGlobalExceptionHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(context, "未捕获异常 (线程: ${thread.name})", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
