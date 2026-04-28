package com.example.traktneosync.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.util.AppLogger
import com.example.traktneosync.util.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktOAuthManager: TraktOAuthManager,
    private val neodbOAuthManager: NeoDBOAuthManager,
    private val authRepository: AuthRepository,
    private val tmdbApi: TmdbApiService,
    private val tmdbKeyProvider: TmdbApiKeyProvider,
    private val cacheDao: CacheDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        const val GITHUB_REPO_URL = "https://github.com/yuwanyuan/TraktNeoSync"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            refreshCacheSize()

            combine(
                authRepository.traktAccessToken,
                authRepository.traktUser,
                authRepository.neodbAccessToken,
                authRepository.neodbUser,
                authRepository.tmdbApiKey,
                authRepository.preferredLanguage,
                authRepository.darkTheme
            ) { values: Array<String?> ->
                val traktToken = values[0]
                val traktUser = values[1]
                val neodbToken = values[2]
                val neodbUser = values[3]
                val tmdbKey = values[4]
                val lang = values[5]
                val dark = values[6]
                _uiState.value.copy(
                    traktConnected = traktToken != null,
                    traktUsername = traktUser,
                    neodbConnected = neodbToken != null,
                    neodbUsername = neodbUser,
                    tmdbApiKey = tmdbKey ?: "",
                    preferredLanguage = lang ?: "zh-CN",
                    darkThemeMode = dark ?: "system"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = calculateCacheSize()
            _uiState.value = _uiState.value.copy(cacheSize = size)
        }
    }

    private fun calculateCacheSize(): String {
        return try {
            val dbFile = context.getDatabasePath("traktneosync_cache.db")
            val walFile = File(dbFile.parent, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
            val logFile = File(context.filesDir, "app_crash_log.txt")

            var totalBytes = 0L
            listOf(dbFile, walFile, shmFile, logFile).forEach { f ->
                if (f.exists()) totalBytes += f.length()
            }
            formatBytes(totalBytes)
        } catch (e: Exception) {
            "0 B"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun connectTrakt() {
        traktOAuthManager.openAuthorizationInBrowser()
    }

    fun disconnectTrakt() {
        viewModelScope.launch {
            traktOAuthManager.logout()
        }
    }

    fun connectNeoDB() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(neodbLoading = true, neodbError = null)
            val registered = try {
                neodbOAuthManager.registerApp()
            } catch (e: Exception) {
                AppLogger.error(TAG, "NeoDB注册异常", e)
                false
            }
            if (!registered) {
                AppLogger.error(TAG, "注册NeoDB应用失败，无法打开授权页面")
                _uiState.value = _uiState.value.copy(
                    neodbLoading = false,
                    neodbError = "无法连接 NeoDB 服务器，请检查网络或稍后重试"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(neodbLoading = false)
            neodbOAuthManager.openAuthorizationInBrowser()
        }
    }

    fun disconnectNeoDB() {
        viewModelScope.launch {
            neodbOAuthManager.logout()
        }
    }

    fun clearNeoDBError() {
        _uiState.value = _uiState.value.copy(neodbError = null)
    }

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            authRepository.setTmdbApiKey(key)
        }
    }

    fun testTmdbApiKey(key: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tmdbKeyTesting = true, tmdbKeyValid = null)
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
                return@launch
            }
            val originalKey = tmdbKeyProvider.apiKey
            tmdbKeyProvider.apiKey = trimmed
            try {
                tmdbApi.getMovieDetail(550)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = true)
            } catch (e: Exception) {
                AppLogger.error(TAG, "TMDB Key测试失败", e)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
            } finally {
                if (originalKey != trimmed) {
                    tmdbKeyProvider.apiKey = originalKey
                }
            }
        }
    }

    fun setPreferredLanguage(language: String) {
        viewModelScope.launch {
            authRepository.setPreferredLanguage(language)
            AppLogger.info(TAG, "首选语言切换为 $language")
        }
    }

    fun setDarkTheme(mode: String) {
        viewModelScope.launch {
            authRepository.setDarkTheme(mode)
            AppLogger.info(TAG, "深色模式切换为 $mode")
        }
    }

    fun setLogLevel(level: LogLevel) {
        AppLogger.setLogLevel(level)
        AppLogger.info(TAG, "日志级别切换为 ${level.name}")
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                cacheDao.clearAllCache()
                refreshCacheSize()
                AppLogger.info(TAG, "用户手动清理了全部缓存")
                onDone()
            } catch (e: Exception) {
                AppLogger.error(TAG, "清理缓存失败", e)
            }
        }
    }
}

data class SettingsUiState(
    val traktConnected: Boolean = false,
    val traktUsername: String? = null,
    val neodbConnected: Boolean = false,
    val neodbUsername: String? = null,
    val neodbLoading: Boolean = false,
    val neodbError: String? = null,
    val tmdbApiKey: String = "",
    val tmdbKeyTesting: Boolean = false,
    val tmdbKeyValid: Boolean? = null,
    val preferredLanguage: String = "zh-CN",
    val darkThemeMode: String = "system",
    val cacheSize: String = "0 B",
)
