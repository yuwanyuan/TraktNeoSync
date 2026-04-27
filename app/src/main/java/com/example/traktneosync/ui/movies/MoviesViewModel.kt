package com.example.traktneosync.ui.movies

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
class MoviesViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MoviesViewModel"
    }

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState

    init {
        loadMovies()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val items = try {
                if (_uiState.value.selectedTab == 0) {
                    // 已观看
                    syncRepository.getTraktWatchedMovies().map { watched ->
                        watched.movie?.let { movie ->
                            MovieItem(
                                title = movie.title,
                                year = movie.year,
                                plays = watched.plays,
                                imdbId = movie.ids.imdb,
                                tmdbId = movie.ids.tmdb,
                                posterUrl = buildPosterUrl(movie.ids.tmdb)
                            )
                        }
                    }.filterNotNull()
                } else {
                    // 待看
                    syncRepository.getTraktMovieWatchlist().map { item ->
                        item.movie?.let { movie ->
                            MovieItem(
                                title = movie.title,
                                year = movie.year,
                                imdbId = movie.ids.imdb,
                                tmdbId = movie.ids.tmdb,
                                posterUrl = buildPosterUrl(movie.ids.tmdb)
                            )
                        }
                    }.filterNotNull()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading movies: ${e.message}")
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

data class MoviesUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val items: List<MovieItem> = emptyList()
)

data class MovieItem(
    val title: String,
    val year: Int?,
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null
)
