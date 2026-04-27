package com.example.traktneosync.ui.shows

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

            val items = if (_uiState.value.selectedTab == 0) {
                // 已观看
                syncRepository.getTraktWatchedShows().map { watched ->
                    watched.show?.let { show ->
                        ShowItem(
                            title = show.title,
                            year = show.year,
                            plays = watched.plays,
                            imdbId = show.ids.imdb
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
                            imdbId = show.ids.imdb
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

data class ShowsUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val items: List<ShowItem> = emptyList()
)
