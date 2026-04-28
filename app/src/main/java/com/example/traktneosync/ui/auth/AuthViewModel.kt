package com.example.traktneosync.ui.auth

import com.example.traktneosync.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.data.trakt.TraktOAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val traktOAuthManager: TraktOAuthManager,
    private val neodbOAuthManager: NeoDBOAuthManager,
    private val authRepository: AuthRepository,
    private val tmdbApi: TmdbApiService,
    private val tmdbKeyProvider: TmdbApiKeyProvider,
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                authRepository.traktAccessToken,
                authRepository.traktUser,
                authRepository.neodbAccessToken,
                authRepository.neodbUser,
                authRepository.tmdbApiKey
            ) { traktToken, traktUser, neodbToken, neodbUser, tmdbKey ->
                AuthUiState(
                    traktConnected = traktToken != null,
                    traktUsername = traktUser,
                    neodbConnected = neodbToken != null,
                    neodbUsername = neodbUser,
                    tmdbApiKey = tmdbKey ?: ""
                )
            }.collect { state ->
                _uiState.value = state
            }
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

            // 先注册/获取应用凭证
            val registered = neodbOAuthManager.registerApp()
            if (!registered) {
                AppLogger.error(TAG, "注册NeoDB应用失败")
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
                // 用 Fight Club (id=550) 测试 API Key 是否可用
                tmdbApi.getMovieDetail(550)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = true)
            } catch (e: Exception) {
                AppLogger.error(TAG, "TMDB Key测试失败", e)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
            } finally {
                // 恢复 Provider 中原先保存的 Key（如果与当前测试的不同）
                if (originalKey != trimmed) {
                    tmdbKeyProvider.apiKey = originalKey
                }
            }
        }
    }
}

data class AuthUiState(
    val traktConnected: Boolean = false,
    val traktUsername: String? = null,
    val neodbConnected: Boolean = false,
    val neodbUsername: String? = null,
    val neodbLoading: Boolean = false,
    val neodbError: String? = null,
    val tmdbApiKey: String = "",
    val tmdbKeyTesting: Boolean = false,
    val tmdbKeyValid: Boolean? = null,
)
