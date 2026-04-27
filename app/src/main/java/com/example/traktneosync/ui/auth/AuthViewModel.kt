package com.example.traktneosync.ui.auth

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

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                authRepository.traktAccessToken,
                authRepository.traktUser,
                authRepository.neodbAccessToken,
                authRepository.neodbUser
            ) { traktToken, traktUser, neodbToken, neodbUser ->
                AuthUiState(
                    traktConnected = traktToken != null,
                    traktUsername = traktUser,
                    neodbConnected = neodbToken != null,
                    neodbUsername = neodbUser
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
        neodbOAuthManager.openAuthorizationInBrowser()
    }

    fun disconnectNeoDB() {
        viewModelScope.launch {
            neodbOAuthManager.logout()
        }
    }
}

data class AuthUiState(
    val traktConnected: Boolean = false,
    val traktUsername: String? = null,
    val neodbConnected: Boolean = false,
    val neodbUsername: String? = null
)
