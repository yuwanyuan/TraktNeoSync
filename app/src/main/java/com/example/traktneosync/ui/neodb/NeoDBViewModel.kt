package com.example.traktneosync.ui.neodb

import com.example.traktneosync.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBEntry
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

    fun selectTab(tab: NeoDBTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        when (tab) {
            NeoDBTab.DISCOVER -> loadTrending()
            is NeoDBTab.ShelfTab -> loadShelf(tab.shelf)
        }
    }

    fun selectShelf(shelf: NeoDBShelf) {
        selectTab(NeoDBTab.ShelfTab(shelf))
    }

    fun refresh() {
        when (val tab = _uiState.value.selectedTab) {
            NeoDBTab.DISCOVER -> loadTrending()
            is NeoDBTab.ShelfTab -> loadShelf(tab.shelf)
        }
    }

    fun initialLoad() {
        if (_uiState.value.trending.isNotEmpty() || _uiState.value.marks.isNotEmpty() || _uiState.value.isLoading) return
        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first()
                _uiState.value = _uiState.value.copy(isAuthenticated = token != null)
                loadTrending()
            } catch (e: Exception) {
                AppLogger.error(TAG, "初始化加载失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val movies = neoDBApi.getTrending("movie")
                val tv = neoDBApi.getTrending("tv")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trendingMovies = movies,
                    trendingTV = tv,
                    error = null
                )
            } catch (e: Exception) {
                AppLogger.error(TAG, "加载热门失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载热门失败"
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
    val selectedTab: NeoDBTab = NeoDBTab.DISCOVER,
    val trendingMovies: List<NeoDBEntry> = emptyList(),
    val trendingTV: List<NeoDBEntry> = emptyList(),
    val marks: List<NeoDBMark> = emptyList(),
    val error: String? = null
) {
    val trending: List<NeoDBEntry> get() = trendingMovies + trendingTV
}

sealed class NeoDBTab {
    object DISCOVER : NeoDBTab()
    data class ShelfTab(val shelf: NeoDBShelf) : NeoDBTab()
}

enum class NeoDBShelf(val label: String, val apiValue: String) {
    COMPLETE("看过", "complete"),
    WISHLIST("想看", "wishlist"),
    PROGRESS("在看", "progress"),
    DROPPED("搁置", "dropped")
}
