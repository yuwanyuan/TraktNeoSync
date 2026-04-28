package com.example.traktneosync.ui.neodb

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBMark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NeoDBViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val neoDBApi: NeoDBApiService
) : ViewModel() {

    companion object {
        private const val TAG = "NeoDBViewModel"
    }

    private val _uiState = MutableStateFlow(NeoDBUiState())
    val uiState: StateFlow<NeoDBUiState> = _uiState

    init {
        viewModelScope.launch {
            authRepository.neodbAccessToken.collect { token ->
                _uiState.value = _uiState.value.copy(isAuthenticated = token != null)
                if (token != null) {
                    loadShelf(_uiState.value.selectedShelf)
                }
            }
        }
    }

    fun selectShelf(shelf: NeoDBShelf) {
        _uiState.value = _uiState.value.copy(selectedShelf = shelf)
        loadShelf(shelf)
    }

    fun refresh() {
        loadShelf(_uiState.value.selectedShelf)
    }

    private fun loadShelf(shelf: NeoDBShelf) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val token = authRepository.neodbAccessToken.first() ?: ""
                if (token.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "未登录 NeoDB")
                    return@launch
                }
                val result = neoDBApi.getShelf(
                    token = "Bearer $token",
                    shelfType = shelf.apiValue
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    marks = result.data,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Load shelf error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
}

data class NeoDBUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val selectedShelf: NeoDBShelf = NeoDBShelf.COMPLETE,
    val marks: List<NeoDBMark> = emptyList(),
    val error: String? = null
)

enum class NeoDBShelf(val label: String, val apiValue: String) {
    COMPLETE("看过", "complete"),
    WISHLIST("想看", "wishlist"),
    PROGRESS("在看", "progress"),
    DROPPED("搁置", "dropped")
}
