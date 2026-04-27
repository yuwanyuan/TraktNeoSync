package com.example.traktneosync.ui.shows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ShowsViewModel"
    }

    private val _uiState = MutableStateFlow(ShowsUiState())
    val uiState: StateFlow<ShowsUiState> = _uiState

    init {
        loadShows()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        loadShows()
    }

    private fun loadShows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val items = try {
                if (_uiState.value.selectedTab == 0) {
                    // 已观看
                    syncRepository.getTraktWatchedShows().map { watched ->
                        watched.show?.let { show ->
                            ShowItem(
                                title = show.title,
                                year = show.year,
                                plays = watched.plays,
                                imdbId = show.ids.imdb,
                                tmdbId = show.ids.tmdb,
                                posterUrl = buildPosterUrl(show.ids.tmdb)
                            )
                        }
                    }.filterNotNull()
                } else {
                    // 待看
                    syncRepository.getTraktShowWatchlist().map { item ->
                        item.show?.let { show ->
                            ShowItem(
                                title = show.title,
                                year = show.year,
                                imdbId = show.ids.imdb,
                                tmdbId = show.ids.tmdb,
                                posterUrl = buildPosterUrl(show.ids.tmdb)
                            )
                        }
                    }.filterNotNull()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading shows: ${e.message}")
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = items
            )
        }
    }

    private fun buildPosterUrl(tmdbId: Long?): String? {
        // 使用 TMDB 图片服务构造海报 URL
        // 实际项目中可能需要调用 TMDB API 获取实际的 poster_path
        // 这里使用 TMDB 的占位图服务
        return tmdbId?.let { "https://www.themoviedb.org/t/p/w200${it}" }
    }
}

data class ShowsUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val items: List<ShowItem> = emptyList()
)

data class ShowItem(
    val title: String,
    val year: Int?,
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null
)
