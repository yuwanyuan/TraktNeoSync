package com.example.traktneosync.ui.neodb

import com.example.traktneosync.util.AppLogger
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

    fun checkAuth() {
        viewModelScope.launch {
            val token = authRepository.neodbAccessToken.first()
            _uiState.value = _uiState.value.copy(isAuthenticated = token != null)
        }
    }

    fun selectShelf(shelf: NeoDBShelf) {
        if (_uiState.value.selectedShelf == shelf && _uiState.value.marks.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(selectedShelf = shelf)
        loadShelf(shelf)
    }

    fun refresh() {
        loadShelf(_uiState.value.selectedShelf)
    }

    fun initialLoad() {
        if (_uiState.value.marks.isNotEmpty() || _uiState.value.isLoading) return
        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first()
                _uiState.value = _uiState.value.copy(isAuthenticated = token != null)
                if (token != null) {
                    loadShelf(_uiState.value.selectedShelf)
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "初始化加载失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
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
                val allMarks = mutableListOf<NeoDBMark>()
                var page = 1
                while (true) {
                    val result = neoDBApi.getShelf(
                        token = "Bearer $token",
                        shelfType = shelf.apiValue,
                        page = page
                    )
                    allMarks.addAll(result.data)
                    if (page >= result.pages || result.pages <= 0) break
                    page++
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    marks = allMarks,
                    error = null
                )
            } catch (e: Exception) {
                AppLogger.error(TAG, "加载书架失败", e, mapOf("shelf" to shelf.apiValue))
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
