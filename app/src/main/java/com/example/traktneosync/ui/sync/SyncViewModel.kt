package com.example.traktneosync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.SyncRepository
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.cache.PosterCacheEntity
import com.example.traktneosync.data.cache.SyncCacheEntity
import com.example.traktneosync.data.neodb.NeoDBMark
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val tmdbApi: TmdbApiService,
    private val cacheDao: CacheDao
) : ViewModel() {

    companion object {
        private const val TAG = "SyncViewModel"
        private const val TMDB_CONCURRENCY = 5
    }

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    init {
        viewModelScope.launch {
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

    fun toggleSelectAll() {
        val currentItems = _uiState.value.items
        val allSelected = currentItems.all { it.isSelected }
        _uiState.value = _uiState.value.copy(
            items = currentItems.map { it.copy(isSelected = !allSelected) }
        )
    }

    fun toggleSelect(itemUuid: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.uuid == itemUuid) it.copy(isSelected = !it.isSelected) else it
            }
        )
    }

    fun checkSync(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            if (!force) {
                val cached = try { cacheDao.getSyncCache() } catch (e: Exception) { emptyList() }
                if (cached.isNotEmpty()) {
                    val cachedItems = cached.map { it.toSyncListItem() }
                    val stats = buildStats(cachedItems)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = cachedItems,
                        hasMoreItems = cachedItems.isNotEmpty(),
                        stats = stats
                    )
                    AppLogger.debug(TAG, "从缓存加载同步数据", mapOf("count" to cached.size))
                    return@launch
                }
            }

            try {
                val watchedMovies = syncRepository.getTraktWatchedMovies()
                val watchedShows = syncRepository.getTraktWatchedShows()
                val movieWatchlist = syncRepository.getTraktMovieWatchlist()
                val showWatchlist = syncRepository.getTraktShowWatchlist()

                val neodbCompletedMovies = syncRepository.getNeoDBCompletedMovies()
                val neodbCompletedTV = syncRepository.getNeoDBCompletedTV()
                val neodbWishlistMovies = syncRepository.getNeoDBWishlistMovies()
                val neodbWishlistTV = syncRepository.getNeoDBWishlistTV()
                val neodbProgressMovies = syncRepository.getNeoDBProgressMovies()
                val neodbProgressTV = syncRepository.getNeoDBProgressTV()

                val syncItems = mutableListOf<SyncListItem>()

                watchedMovies.forEach { item ->
                    item.movie?.let { movie ->
                        val traktItem = SyncRepository.TraktItem(
                            title = movie.title,
                            year = movie.year,
                            type = "movie",
                            ids = movie.ids,
                            watchedAt = item.lastWatchedAt,
                            plays = item.plays
                        )
                        val neodbMark = findMatchingMark(
                            traktItem,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV,
                            neodbProgressMovies, neodbProgressTV
                        )
                        if (neodbMark == null) {
                            syncItems.add(
                                SyncListItem(
                                    uuid = "watched_movie_${movie.ids.trakt}",
                                    title = movie.title,
                                    year = movie.year,
                                    type = "电影",
                                    status = "已观看",
                                    traktItem = traktItem,
                                    shelfType = "complete",
                                    tmdbId = movie.ids.tmdb,
                                    posterUrl = null
                                )
                            )
                        }
                    }
                }

                watchedShows.forEach { item ->
                    item.show?.let { show ->
                        val traktItem = SyncRepository.TraktItem(
                            title = show.title,
                            year = show.year,
                            type = "show",
                            ids = show.ids,
                            watchedAt = item.lastWatchedAt,
                            plays = item.plays
                        )
                        val neodbMark = findMatchingMark(
                            traktItem,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV,
                            neodbProgressMovies, neodbProgressTV
                        )
                        if (neodbMark == null) {
                            syncItems.add(
                                SyncListItem(
                                    uuid = "watched_show_${show.ids.trakt}",
                                    title = show.title,
                                    year = show.year,
                                    type = "剧集",
                                    status = "已观看 (${item.plays} 集)",
                                    traktItem = traktItem,
                                    shelfType = "complete",
                                    tmdbId = show.ids.tmdb,
                                    posterUrl = null
                                )
                            )
                        }
                    }
                }

                movieWatchlist.forEach { item ->
                    item.movie?.let { movie ->
                        val traktItem = SyncRepository.TraktItem(
                            title = movie.title,
                            year = movie.year,
                            type = "movie",
                            ids = movie.ids
                        )
                        val neodbMark = findMatchingMark(
                            traktItem,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV,
                            neodbProgressMovies, neodbProgressTV
                        )
                        if (neodbMark == null) {
                            syncItems.add(
                                SyncListItem(
                                    uuid = "watchlist_movie_${movie.ids.trakt}",
                                    title = movie.title,
                                    year = movie.year,
                                    type = "电影",
                                    status = "待看",
                                    traktItem = traktItem,
                                    shelfType = "wishlist",
                                    tmdbId = movie.ids.tmdb,
                                    posterUrl = null
                                )
                            )
                        }
                    }
                }

                showWatchlist.forEach { item ->
                    item.show?.let { show ->
                        val traktItem = SyncRepository.TraktItem(
                            title = show.title,
                            year = show.year,
                            type = "show",
                            ids = show.ids
                        )
                        val neodbMark = findMatchingMark(
                            traktItem,
                            neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV,
                            neodbProgressMovies, neodbProgressTV
                        )
                        if (neodbMark == null) {
                            syncItems.add(
                                SyncListItem(
                                    uuid = "watchlist_show_${show.ids.trakt}",
                                    title = show.title,
                                    year = show.year,
                                    type = "剧集",
                                    status = "待看",
                                    traktItem = traktItem,
                                    shelfType = "wishlist",
                                    tmdbId = show.ids.tmdb,
                                    posterUrl = null
                                )
                            )
                        }
                    }
                }

                val withTmdb = fetchTmdbDetails(syncItems)

                try {
                    cacheDao.replaceSyncCache(withTmdb.map { it.toSyncCacheEntity() })
                    AppLogger.debug(TAG, "同步数据已写入缓存", mapOf("count" to withTmdb.size))
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "写入同步缓存失败", e)
                }

                val stats = buildStats(withTmdb)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = withTmdb,
                    hasMoreItems = withTmdb.isNotEmpty(),
                    stats = stats
                )

            } catch (e: Exception) {
                AppLogger.error(TAG, "同步数据加载失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    syncError = e.message ?: "同步失败"
                )
            }
        }
    }

    private fun buildStats(items: List<SyncListItem>): Map<String, Int> = mapOf(
        "待添加" to items.size,
        "电影" to items.count { it.type == "电影" },
        "剧集" to items.count { it.type == "剧集" }
    )

    private suspend fun fetchTmdbDetails(items: List<SyncListItem>): List<SyncListItem> {
        val semaphore = Semaphore(TMDB_CONCURRENCY)
        val deferredList = items.map { item ->
            viewModelScope.async {
                withTimeout(15000) {
                    semaphore.withPermit {
                        val tmdbId = item.tmdbId
                        if (tmdbId != null && tmdbId > 0) {
                            val detail = fetchTmdbDetailCached(tmdbId, item.type == "电影")
                            item.copy(
                                title = detail.chineseTitle?.takeIf { it.isNotBlank() } ?: item.title,
                                posterUrl = detail.posterUrl ?: item.posterUrl
                            )
                        } else {
                            item
                        }
                    }
                }
            }
        }
        return deferredList.awaitAll()
    }

    private suspend fun fetchTmdbDetailCached(tmdbId: Long, isMovie: Boolean): TmdbDetailResult {
        val cachedPoster = try { cacheDao.getPoster(tmdbId) } catch (e: Exception) { null }

        return try {
            val (path, chineseTitle) = if (isMovie) {
                val d = tmdbApi.getMovieDetail(tmdbId)
                Pair(d.posterPath, d.title)
            } else {
                val d = tmdbApi.getTvDetail(tmdbId)
                Pair(d.posterPath, d.name)
            }
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = path)))
            } catch (_: Exception) {
            }
            val posterUrl = path?.let { "https://image.tmdb.org/t/p/w200$it" }
            TmdbDetailResult(posterUrl = posterUrl, chineseTitle = chineseTitle)
        } catch (e: Exception) {
            AppLogger.warn(TAG, "TMDB详情获取失败", e, mapOf("tmdbId" to tmdbId))
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = null)))
            } catch (_: Exception) {
            }
            val fallbackPosterUrl = cachedPoster?.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
            TmdbDetailResult(posterUrl = fallbackPosterUrl, chineseTitle = null)
        }
    }

    fun syncSelected() {
        viewModelScope.launch {
            val selectedItems = _uiState.value.items.filter { it.isSelected }
            if (selectedItems.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(
                items = _uiState.value.items.map { it.copy(isSyncing = it.isSelected) }
            )

            selectedItems.forEachIndexed { index, item ->
                _uiState.value = _uiState.value.copy(
                    syncProgress = SyncProgress(
                        current = index + 1,
                        total = selectedItems.size,
                        currentTitle = item.title
                    )
                )

                val result = syncRepository.addToNeoDB(item.traktItem, item.shelfType)
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            items = _uiState.value.items.map {
                                if (it.uuid == item.uuid) it.copy(isSynced = true, isSyncing = false)
                                else it
                            }
                        )
                    },
                    onFailure = { error ->
                        AppLogger.warn(TAG, "同步单项失败", error, mapOf("title" to item.title))
                        _uiState.value = _uiState.value.copy(
                            items = _uiState.value.items.map {
                                if (it.uuid == item.uuid) it.copy(isSyncing = false, syncError = error.message)
                                else it
                            }
                        )
                    }
                )
            }

            _uiState.value = _uiState.value.copy(syncProgress = null)

            val remaining = _uiState.value.items.filter { !it.isSynced }

            try {
                cacheDao.replaceSyncCache(remaining.map { it.toSyncCacheEntity() })
            } catch (e: Exception) {
                AppLogger.warn(TAG, "更新同步缓存失败", e)
            }

            val stats = buildStats(remaining)
            _uiState.value = _uiState.value.copy(
                stats = stats,
                hasMoreItems = remaining.isNotEmpty()
            )
        }
    }

    private fun findMatchingMark(
        traktItem: SyncRepository.TraktItem,
        completedMovies: List<NeoDBMark>,
        completedTV: List<NeoDBMark>,
        wishlistMovies: List<NeoDBMark>,
        wishlistTV: List<NeoDBMark>,
        progressMovies: List<NeoDBMark> = emptyList(),
        progressTV: List<NeoDBMark> = emptyList()
    ): NeoDBMark? {
        return syncRepository.findMatchingNeoDBMark(
            traktItem, completedMovies, completedTV, wishlistMovies, wishlistTV, progressMovies, progressTV
        )
    }

    data class TmdbDetailResult(
        val posterUrl: String?,
        val chineseTitle: String?
    )
}

data class SyncListItem(
    val uuid: String,
    val title: String,
    val year: Int?,
    val type: String,
    val status: String,
    val traktItem: SyncRepository.TraktItem,
    val shelfType: String = "wishlist",
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val isSelected: Boolean = true,
    val isSynced: Boolean = false,
    val isSyncing: Boolean = false,
    val syncError: String? = null
)

private fun SyncListItem.toSyncCacheEntity() = SyncCacheEntity(
    uuid = uuid,
    title = title,
    year = year,
    type = type,
    status = status,
    shelfType = shelfType,
    tmdbId = tmdbId,
    posterUrl = posterUrl,
    traktId = traktItem.ids.trakt,
    imdbId = traktItem.ids.imdb
)

private fun SyncCacheEntity.toSyncListItem() = SyncListItem(
    uuid = uuid,
    title = title,
    year = year,
    type = type,
    status = status,
    traktItem = SyncRepository.TraktItem(
        title = title,
        year = year,
        type = if (type == "电影") "movie" else "show",
        ids = com.example.traktneosync.data.trakt.TraktIds(
            trakt = traktId,
            imdb = imdbId,
            tmdb = tmdbId
        )
    ),
    shelfType = shelfType,
    tmdbId = tmdbId,
    posterUrl = posterUrl
)

data class SyncUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val items: List<SyncListItem> = emptyList(),
    val filter: SyncFilter = SyncFilter.ALL,
    val hasMoreItems: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncError: String? = null,
    val stats: Map<String, Int> = emptyMap()
) {
    val filteredItems: List<SyncListItem>
        get() = when (filter) {
            SyncFilter.ALL -> items
            SyncFilter.MOVIES -> items.filter { it.type == "电影" }
            SyncFilter.SHOWS -> items.filter { it.type == "剧集" }
            SyncFilter.WATCHED -> items.filter { it.status.startsWith("已观看") }
            SyncFilter.WATCHLIST -> items.filter { it.status == "待看" }
        }

    val selectedCount: Int
        get() = filteredItems.count { it.isSelected && !it.isSynced }

    val allSelected: Boolean
        get() = filteredItems.isNotEmpty() && filteredItems.all { it.isSelected || it.isSynced }
}

data class SyncProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String
)

enum class SyncFilter(val label: String) {
    ALL("全部"),
    MOVIES("电影"),
    SHOWS("剧集"),
    WATCHED("已观看"),
    WATCHLIST("待看")
}
