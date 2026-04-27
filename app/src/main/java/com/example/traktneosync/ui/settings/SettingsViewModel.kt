package com.example.traktneosync.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktOAuthManager: TraktOAuthManager,
    private val neodbOAuthManager: NeoDBOAuthManager,
    private val authRepository: AuthRepository,
    private val tmdbApi: TmdbApiService,
    private val tmdbKeyProvider: TmdbApiKeyProvider,
    private val cacheDao: CacheDao,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        const val GITHUB_REPO_URL = "https://github.com/yuwanyuan/TraktNeoSync"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                authRepository.traktAccessToken,
                authRepository.traktUser,
                authRepository.neodbAccessToken,
                authRepository.neodbUser,
                authRepository.tmdbApiKey,
                authRepository.preferredLanguage
            ) { values: Array<String?> ->
                val traktToken = values[0]
                val traktUser = values[1]
                val neodbToken = values[2]
                val neodbUser = values[3]
                val tmdbKey = values[4]
                val lang = values[5]
                SettingsUiState(
                    traktConnected = traktToken != null,
                    traktUsername = traktUser,
                    neodbConnected = neodbToken != null,
                    neodbUsername = neodbUser,
                    tmdbApiKey = tmdbKey ?: "",
                    preferredLanguage = lang ?: "zh-CN"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ========== Trakt ==========
    fun connectTrakt() {
        traktOAuthManager.openAuthorizationInBrowser()
    }

    fun disconnectTrakt() {
        viewModelScope.launch {
            traktOAuthManager.logout()
        }
    }

    // ========== NeoDB ==========
    fun connectNeoDB() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(neodbLoading = true, neodbError = null)
            val registered = neodbOAuthManager.registerApp()
            if (!registered) {
                Log.e(TAG, "Failed to register NeoDB app")
                _uiState.value = _uiState.value.copy(
                    neodbLoading = false,
                    neodbError = "无法注册 NeoDB 应用，请检查网络连接"
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

    // ========== TMDB Key ==========
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
                Log.e(TAG, "TMDB key test failed: ${e.message}")
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
            } finally {
                if (originalKey != trimmed) {
                    tmdbKeyProvider.apiKey = originalKey
                }
            }
        }
    }

    // ========== 首选语言 ==========
    fun setPreferredLanguage(language: String) {
        viewModelScope.launch {
            authRepository.setPreferredLanguage(language)
            AppLogger.log("SettingsViewModel: 首选语言切换为 $language")
        }
    }

    // ========== 清理缓存 ==========
    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                cacheDao.clearAllCache()
                AppLogger.log("SettingsViewModel: 用户手动清理了全部缓存")
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "Clear cache failed: ${e.message}")
                AppLogger.log("SettingsViewModel: 清理缓存失败", e)
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
)
