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

enum class DiscoverSection(
    val key: String,
    val label: String,
    val category: String,
    val defaultEnabled: Boolean = true
) {
    MOVIE("movie", "热门电影", "movie"),
    TV("tv", "热门剧集", "tv"),
    BOOK("book", "热门书籍", "book", defaultEnabled = false),
    MUSIC("music", "热门音乐", "music", defaultEnabled = false),
    GAME("game", "热门游戏", "game", defaultEnabled = false)
}

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
            authRepository.discoverSectionPrefs.collect { prefs ->
                val enabledMap = prefs.toMutableMap()
                DiscoverSection.entries.forEach { section ->
                    if (!enabledMap.containsKey(section.key)) {
                        enabledMap[section.key] = section.defaultEnabled
                    }
                }
                _uiState.value = _uiState.value.copy(sectionEnabled = enabledMap)
            }
        }
    }

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

    fun toggleSection(section: DiscoverSection) {
        viewModelScope.launch {
            val current = _uiState.value.sectionEnabled.toMutableMap()
            current[section.key] = !(current[section.key] ?: section.defaultEnabled)
            authRepository.setDiscoverSectionPrefs(current)
        }
    }

    fun refresh() {
        when (val tab = _uiState.value.selectedTab) {
            NeoDBTab.DISCOVER -> loadTrending()
            is NeoDBTab.ShelfTab -> loadShelf(tab.shelf)
        }
    }

    fun initialLoad() {
        if (_uiState.value.trendingData.isNotEmpty() || _uiState.value.marks.isNotEmpty() || _uiState.value.isLoading) return
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
                val data = mutableMapOf<String, List<NeoDBEntry>>()
                DiscoverSection.entries.forEach { section ->
                    try {
                        data[section.key] = neoDBApi.getTrending(section.category)
                    } catch (e: Exception) {
                        AppLogger.warn(TAG, "加载${section.label}失败", e)
                        data[section.key] = emptyList()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trendingData = data,
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
    val trendingData: Map<String, List<NeoDBEntry>> = emptyMap(),
    val marks: List<NeoDBMark> = emptyList(),
    val error: String? = null,
    val sectionEnabled: Map<String, Boolean> = DiscoverSection.entries.associate { it.key to it.defaultEnabled }
) {
    val trending: List<NeoDBEntry> get() = trendingData.values.flatten()
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
