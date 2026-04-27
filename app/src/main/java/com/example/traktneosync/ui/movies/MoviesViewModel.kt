package com.example.traktneosync.ui.movies

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

            val items = if (_uiState.value.selectedTab == 0) {
                // 已观看
                syncRepository.getTraktWatchedMovies().map { watched ->
                    watched.movie?.let { movie ->
                        MovieItem(
                            title = movie.title,
                            year = movie.year,
                            plays = watched.plays,
                            imdbId = movie.ids.imdb
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
                            imdbId = movie.ids.imdb
                        )
                    }
                }.filterNotNull()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = items
            )
        }
    }
}

data class MoviesUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val items: List<MovieItem> = emptyList()
)
