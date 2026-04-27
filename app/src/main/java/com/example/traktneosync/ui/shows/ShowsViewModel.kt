package com.example.traktneosync.ui.shows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.SyncRepository
import com.example.traktneosync.data.tmdb.TmdbApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tmdbApi: TmdbApiService,
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
                val rawItems = if (_uiState.value.selectedTab == 0) {
                    // 已观看
                    syncRepository.getTraktWatchedShows().map { watched ->
                        watched.show?.let { show ->
                            ShowItem(
                                title = show.title,
                                year = show.year,
                                plays = watched.plays,
                                imdbId = show.ids.imdb,
                                tmdbId = show.ids.tmdb,
                                posterUrl = null,
                                lastWatchedAt = watched.lastWatchedAt
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
                                posterUrl = null,
                                lastWatchedAt = item.listedAt
                            )
                        }
                    }.filterNotNull()
                }
                // 按时间降序排序
                val sorted = rawItems.sortedByDescending { item ->
                    try {
                        item.lastWatchedAt?.let { Instant.parse(it).epochSecond } ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                // 异步获取 TMDB 海报
                sorted.map { item ->
                    val posterUrl = item.tmdbId?.let { fetchTmdbPoster(it) }
                    item.copy(posterUrl = posterUrl)
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

    private suspend fun fetchTmdbPoster(tmdbId: Long): String? {
        return try {
            tmdbApi.getTvDetail(tmdbId).posterPath?.let {
                "https://image.tmdb.org/t/p/w200$it"
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fetch failed for id=$tmdbId: ${e.message}")
            null
        }
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
    val posterUrl: String? = null,
    val lastWatchedAt: String? = null
)
