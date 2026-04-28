package com.example.traktneosync.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

enum class LogLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3),
    NONE(4)
}

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_FILE_NAME = "app_crash_log.txt"
    private const val MAX_LOG_SIZE = 500 * 1024
    private const val FLUSH_THRESHOLD = 10

    private var appContext: Context? = null
    private var minLogLevel = LogLevel.DEBUG
    private var fileLogLevel = LogLevel.INFO
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var flushCounter = 0

    @Volatile
    private var writerThread: Thread? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        startWriterThread()
    }

    fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    fun setFileLogLevel(level: LogLevel) {
        fileLogLevel = level
    }

    private fun startWriterThread() {
        if (writerThread?.isAlive == true) return
        writerThread = thread(name = "AppLogger-Writer", isDaemon = true) {
            while (true) {
                try {
                    val entry = logQueue.poll()
                    if (entry != null) {
                        writeToFile(entry)
                    } else {
                        Thread.sleep(200)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Writer thread error: ${e.message}")
                }
            }
        }
    }

    private fun enqueue(entry: String) {
        logQueue.add(entry)
        flushCounter++
        if (flushCounter >= FLUSH_THRESHOLD) {
            flushCounter = 0
        }
    }

    private fun writeToFile(entry: String) {
        val ctx = appContext ?: return
        try {
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.writeText("[${formatTimestamp()}] 日志已重置，旧内容超过500KB\n\n")
            }
            logFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    private fun formatTimestamp(): String {
        return dateFormat.format(Date())
    }

    private fun formatLogEntry(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        context: Map<String, Any?>? = null
    ): String {
        val ts = formatTimestamp()
        val levelStr = level.name.padEnd(5)
        val ctxStr = if (!context.isNullOrEmpty()) {
            context.entries.joinToString(", ") { "${it.key}=${it.value}" }.let { " [$it]" }
        } else ""
        val stackStr = throwable?.let {
            "\n  Exception: ${it.javaClass.simpleName}: ${it.message}" +
                it.stackTrace.take(8).joinToString("\n    ", prefix = "\n") { "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" } +
                if (it.stackTrace.size > 8) "\n    ... ${it.stackTrace.size - 8} more" else ""
        } ?: ""
        return "[$ts] $levelStr $tag: $message$ctxStr$stackStr\n\n"
    }

    fun debug(tag: String, message: String, context: Map<String, Any?>? = null) {
        if (minLogLevel.priority > LogLevel.DEBUG.priority) return
        Log.d(tag, message)
        if (fileLogLevel.priority <= LogLevel.DEBUG.priority) {
            enqueue(formatLogEntry(LogLevel.DEBUG, tag, message, context = context))
        }
    }

    fun info(tag: String, message: String, context: Map<String, Any?>? = null) {
        if (minLogLevel.priority > LogLevel.INFO.priority) return
        Log.i(tag, message)
        if (fileLogLevel.priority <= LogLevel.INFO.priority) {
            enqueue(formatLogEntry(LogLevel.INFO, tag, message, context = context))
        }
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null, context: Map<String, Any?>? = null) {
        if (minLogLevel.priority > LogLevel.WARN.priority) return
        Log.w(tag, message, throwable)
        if (fileLogLevel.priority <= LogLevel.WARN.priority) {
            enqueue(formatLogEntry(LogLevel.WARN, tag, message, throwable, context))
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null, context: Map<String, Any?>? = null) {
        if (minLogLevel.priority > LogLevel.ERROR.priority) return
        Log.e(tag, message, throwable)
        if (fileLogLevel.priority <= LogLevel.ERROR.priority) {
            enqueue(formatLogEntry(LogLevel.ERROR, tag, message, throwable, context))
        }
    }

    @Deprecated("Use debug/info/warn/error with tag", ReplaceWith("info(tag, message, throwable)"))
    fun log(message: String, throwable: Throwable? = null) {
        val ctx = appContext
        if (ctx != null) {
            enqueue(formatLogEntry(LogLevel.INFO, "Legacy", message, throwable))
        } else {
            Log.w(TAG, "AppLogger not initialized: $message")
        }
    }

    @Deprecated("Use debug/info/warn/error with tag", ReplaceWith("info(tag, message, throwable)"))
    fun log(context: Context, message: String, throwable: Throwable? = null) {
        enqueue(formatLogEntry(LogLevel.INFO, "Legacy", message, throwable))
    }

    fun getLogContent(context: Context): String {
        return try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.readText().take(10000) else "暂无日志"
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            File(context.filesDir, LOG_FILE_NAME).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log: ${e.message}")
        }
    }

    fun setupGlobalExceptionHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val entry = formatLogEntry(
                LogLevel.ERROR, "Crash",
                "未捕获异常 (线程: ${thread.name})",
                throwable,
                mapOf("thread" to thread.name)
            )
            writeToFile(entry)
            Log.e("Crash", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
