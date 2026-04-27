package com.example.traktneosync.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
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
    private val authRepository: AuthRepository
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

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            authRepository.setTmdbApiKey(key)
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
    val tmdbApiKey: String = ""
)
