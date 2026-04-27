package com.example.traktneosync.ui.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.SyncRepository
import com.example.traktneosync.data.tmdb.TmdbApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tmdbApi: TmdbApiService,
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
                val rawItems = if (_uiState.value.selectedTab == 0) {
                    syncRepository.getTraktWatchedMovies().map { watched ->
                        watched.movie?.let { movie ->
                            MovieItem(
                                title = movie.title,
                                year = movie.year,
                                plays = watched.plays,
                                imdbId = movie.ids.imdb,
                                tmdbId = movie.ids.tmdb,
                                posterUrl = null
                            )
                        }
                    }.filterNotNull()
                } else {
                    syncRepository.getTraktMovieWatchlist().map { item ->
                        item.movie?.let { movie ->
                            MovieItem(
                                title = movie.title,
                                year = movie.year,
                                imdbId = movie.ids.imdb,
                                tmdbId = movie.ids.tmdb,
                                posterUrl = null
                            )
                        }
                    }.filterNotNull()
                }
                // 异步获取 TMDB 海报
                rawItems.map { item ->
                    val posterUrl = item.tmdbId?.let { fetchTmdbPoster(it, isMovie = true) }
                    item.copy(posterUrl = posterUrl)
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

    private suspend fun fetchTmdbPoster(tmdbId: Long, isMovie: Boolean): String? {
        return try {
            val path = if (isMovie) {
                tmdbApi.getMovieDetail(tmdbId).posterPath
            } else {
                tmdbApi.getTvDetail(tmdbId).posterPath
            }
            path?.let { "https://image.tmdb.org/t/p/w200$it" }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fetch failed for id=$tmdbId: ${e.message}")
            null
        }
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
