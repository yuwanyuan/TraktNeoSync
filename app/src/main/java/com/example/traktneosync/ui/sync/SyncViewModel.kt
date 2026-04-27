package com.example.traktneosync.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.SyncRepository
import com.example.traktneosync.data.neodb.NeoDBMark
import com.example.traktneosync.data.trakt.TraktWatchedItem
import com.example.traktneosync.data.trakt.TraktWatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SyncViewModel"
    }

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    init {
        viewModelScope.launch {
            // 检查登录状态
            combine(
                authRepository.traktAccessToken,
                authRepository.neodbAccessToken
            ) { traktToken, neodbToken ->
                traktToken != null && neodbToken != null
            }.collect { isAuth ->
                _uiState.value = _uiState.value.copy(isAuthenticated = isAuth)
            }
        }
    }

    fun setFilter(filter: SyncFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(syncError = null)
    }

    fun syncNextBatch() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // 1. 获取 Trakt 数据
                val watchedMovies = syncRepository.getTraktWatchedMovies()
                val watchedShows = syncRepository.getTraktWatchedShows()
                val movieWatchlist = syncRepository.getTraktMovieWatchlist()
                val showWatchlist = syncRepository.getTraktShowWatchlist()

                // 2. 获取 NeoDB 数据
                val neodbCompletedMovies = syncRepository.getNeoDBCompletedMovies()
                val neodbCompletedTV = syncRepository.getNeoDBCompletedTV()
                val neodbWishlistMovies = syncRepository.getNeoDBWishlistMovies()
                val neodbWishlistTV = syncRepository.getNeoDBWishlistTV()

                // 3. 合并并检查状态
                val syncItems = mutableListOf<SyncListItem>()

                // 已观看电影
                watchedMovies.forEach { item ->
                    item.movie?.let { movie ->
                        val neodbMark = findMatchingMark(
                            movie.title, movie.year,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV
                        )
                        syncItems.add(
                            SyncListItem(
                                title = movie.title,
                                year = movie.year,
                                type = "电影",
                                status = "已观看",
                                isSynced = neodbMark != null,
                                traktItem = SyncRepository.TraktItem(
                                    title = movie.title,
                                    year = movie.year,
                                    type = "movie",
                                    ids = movie.ids,
                                    watchedAt = item.lastWatchedAt,
                                    plays = item.plays
                                ),
                                neoDBMark = neodbMark
                            )
                        )
                    }
                }

                // 已观看剧集
                watchedShows.forEach { item ->
                    item.show?.let { show ->
                        val neodbMark = findMatchingMark(
                            show.title, show.year,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV
                        )
                        syncItems.add(
                            SyncListItem(
                                title = show.title,
                                year = show.year,
                                type = "剧集",
                                status = "已观看 (${item.plays} 集)",
                                isSynced = neodbMark != null,
                                traktItem = SyncRepository.TraktItem(
                                    title = show.title,
                                    year = show.year,
                                    type = "show",
                                    ids = show.ids,
                                    watchedAt = item.lastWatchedAt,
                                    plays = item.plays
                                ),
                                neoDBMark = neodbMark
                            )
                        )
                    }
                }

                // 待看电影
                movieWatchlist.forEach { item ->
                    item.movie?.let { movie ->
                        val neodbMark = findMatchingMark(
                            movie.title, movie.year,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV
                        )
                        syncItems.add(
                            SyncListItem(
                                title = movie.title,
                                year = movie.year,
                                type = "电影",
                                status = "待看",
                                isSynced = neodbMark != null,
                                traktItem = SyncRepository.TraktItem(
                                    title = movie.title,
                                    year = movie.year,
                                    type = "movie",
                                    ids = movie.ids
                                ),
                                neoDBMark = neodbMark
                            )
                        )
                    }
                }

                // 待看剧集
                showWatchlist.forEach { item ->
                    item.show?.let { show ->
                        val neodbMark = findMatchingMark(
                            show.title, show.year,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV
                        )
                        syncItems.add(
                            SyncListItem(
                                title = show.title,
                                year = show.year,
                                type = "剧集",
                                status = "待看",
                                isSynced = neodbMark != null,
                                traktItem = SyncRepository.TraktItem(
                                    title = show.title,
                                    year = show.year,
                                    type = "show",
                                    ids = show.ids
                                ),
                                neoDBMark = neodbMark
                            )
                        )
                    }
                }

                // 更新统计
                val stats = mapOf(
                    "全部" to syncItems.size,
                    "未同步" to syncItems.count { !it.isSynced },
                    "已同步" to syncItems.count { it.isSynced }
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = syncItems,
                    hasMoreItems = syncItems.any { !it.isSynced },
                    stats = stats
                )

            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    syncError = e.message ?: "同步失败"
                )
            }
        }
    }

    fun addToNeoDB(item: SyncListItem) {
        viewModelScope.launch {
            val shelfType = when (item.status) {
                "已观看", "已观看 (${item.traktItem.plays} 集)" -> "complete"
                "待看" -> "wishlist"
                else -> "wishlist"
            }

            val result = syncRepository.addToNeoDB(item.traktItem, shelfType)

            result.fold(
                onSuccess = {
                    // 更新列表状态
                    val updatedItems = _uiState.value.items.map { listItem ->
                        if (listItem.traktItem.ids.trakt == item.traktItem.ids.trakt) {
                            listItem.copy(isSynced = true)
                        } else listItem
                    }
                    _uiState.value = _uiState.value.copy(items = updatedItems)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(syncError = error.message)
                }
            )
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            val unsyncedItems = _uiState.value.items.filter { !it.isSynced }
            if (unsyncedItems.isEmpty()) return@launch

            unsyncedItems.forEachIndexed { index, item ->
                _uiState.value = _uiState.value.copy(
                    syncProgress = SyncProgress(
                        current = index + 1,
                        total = unsyncedItems.size,
                        currentTitle = item.title
                    )
                )
                
                val shelfType = when {
                    item.status.startsWith("已观看") -> "complete"
                    item.status == "待看" -> "wishlist"
                    else -> "wishlist"
                }
                
                val result = syncRepository.addToNeoDB(item.traktItem, shelfType)
                result.fold(
                    onSuccess = {
                        // 更新列表状态
                        val updatedItems = _uiState.value.items.map { listItem ->
                            if (listItem.traktItem.ids.trakt == item.traktItem.ids.trakt) {
                                listItem.copy(isSynced = true)
                            } else listItem
                        }
                        _uiState.value = _uiState.value.copy(items = updatedItems)
                    },
                    onFailure = { error ->
                        // 记录错误但继续
                        Log.e(TAG, "Failed to add ${item.title}: ${error.message}")
                    }
                )
            }

            _uiState.value = _uiState.value.copy(syncProgress = null)
            
            // 更新统计
            val stats = mapOf(
                "全部" to _uiState.value.items.size,
                "未同步" to _uiState.value.items.count { !it.isSynced },
                "已同步" to _uiState.value.items.count { it.isSynced }
            )
            _uiState.value = _uiState.value.copy(stats = stats)
        }
    }

    private fun findMatchingMark(
        title: String, year: Int?,
        completedMovies: List<NeoDBMark>,
        completedTV: List<NeoDBMark>,
        wishlistMovies: List<NeoDBMark>,
        wishlistTV: List<NeoDBMark>
    ): NeoDBMark? {
        val allMarks = completedMovies + completedTV + wishlistMovies + wishlistTV
        return allMarks.find { mark ->
            mark.item.displayTitle.equals(title, ignoreCase = true) ||
                    mark.item.displayTitle.contains(title, ignoreCase = true)
        }
    }
}

data class SyncListItem(
    val title: String,
    val year: Int?,
    val type: String, // "电影" or "剧集"
    val status: String, // "已观看" or "待看"
    val isSynced: Boolean,
    val traktItem: SyncRepository.TraktItem,
    val neoDBMark: NeoDBMark? = null
)

data class SyncUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val items: List<SyncListItem> = emptyList(),
    val filter: SyncFilter = SyncFilter.NOT_SYNCED,
    val hasMoreItems: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncError: String? = null,
    val stats: Map<String, Int> = emptyMap()
) {
    val filteredItems: List<SyncListItem>
        get() = when (filter) {
            SyncFilter.ALL -> items
            SyncFilter.NOT_SYNCED -> items.filter { !it.isSynced }
            SyncFilter.SYNCED -> items.filter { it.isSynced }
            SyncFilter.MOVIES -> items.filter { it.type == "电影" }
            SyncFilter.SHOWS -> items.filter { it.type == "剧集" }
        }
}

data class SyncProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String
)

enum class SyncFilter(val label: String) {
    ALL("全部"),
    NOT_SYNCED("待添加"),
    SYNCED("已添加"),
    MOVIES("电影"),
    SHOWS("剧集")
}
