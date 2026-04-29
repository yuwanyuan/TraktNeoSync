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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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

    private val syncMutex = Mutex()
    private val checkMutex = Mutex()

    init {
        viewModelScope.launch {
            combine(
                authRepository.traktAccessToken,
                authRepository.neodbAccessToken
            ) { traktToken, neodbToken ->
                traktToken != null && neodbToken != null
            }.collect { isAuth ->
                _uiState.update { it.copy(isAuthenticated = isAuth) }
            }
        }
    }

    fun setFilter(filter: SyncFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun clearError() {
        _uiState.update { it.copy(syncError = null) }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true) }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                items = it.items.map { item -> item.copy(isSelected = false) }
            )
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val filteredItems = state.filteredItems
            val allSelected = filteredItems.isNotEmpty() && filteredItems.all { it.isSelected || it.isSynced }
            val filteredUuids = filteredItems.map { it.uuid }.toSet()
            state.copy(
                items = state.items.map { item ->
                    if (item.uuid in filteredUuids && !item.isSynced) {
                        item.copy(isSelected = !allSelected)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun toggleSelect(itemUuid: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.uuid == itemUuid) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun syncSingle(item: SyncListItem) {
        viewModelScope.launch {
            if (!syncMutex.tryLock()) {
                _uiState.update { it.copy(syncError = "已有同步操作进行中") }
                return@launch
            }
            try {
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map {
                            if (it.uuid == item.uuid) it.copy(isSyncing = true) else it
                        }
                    )
                }

                val result = syncRepository.addToNeoDB(item.traktItem, item.shelfType)
                result.fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(
                                items = state.items.map {
                                    if (it.uuid == item.uuid) it.copy(isSynced = true, isSyncing = false, syncError = null) else it
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { state ->
                            state.copy(
                                items = state.items.map {
                                    if (it.uuid == item.uuid) it.copy(isSyncing = false, syncError = error.message) else it
                                }
                            )
                        }
                    }
                )

                updateCacheAndStats()
            } finally {
                syncMutex.unlock()
            }
        }
    }

    fun checkSync(force: Boolean = false) {
        viewModelScope.launch {
            if (!checkMutex.tryLock()) return@launch
            try {
                _uiState.update { it.copy(isLoading = true) }

                if (!force) {
                    val cached = try { cacheDao.getSyncCache() } catch (e: Exception) { emptyList() }
                    if (cached.isNotEmpty()) {
                        val cachedItems = cached.map { it.toSyncListItem() }
                        val stats = buildStats(cachedItems)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = cachedItems,
                                hasMoreItems = cachedItems.isNotEmpty(),
                                stats = stats
                            )
                        }
                        AppLogger.debug(TAG, "从缓存加载同步数据", mapOf("count" to cached.size))
                        return@launch
                    }
                }

                try {
                    val (watchedMovies, watchedShows, movieWatchlist, showWatchlist,
                        neodbCompletedMovies, neodbCompletedTV, neodbWishlistMovies, neodbWishlistTV,
                        neodbProgressMovies, neodbProgressTV) = coroutineScope {
                        val d1 = async { syncRepository.getTraktWatchedMovies() }
                        val d2 = async { syncRepository.getTraktWatchedShows() }
                        val d3 = async { syncRepository.getTraktMovieWatchlist() }
                        val d4 = async { syncRepository.getTraktShowWatchlist() }
                        val d5 = async { syncRepository.getNeoDBCompletedMovies() }
                        val d6 = async { syncRepository.getNeoDBCompletedTV() }
                        val d7 = async { syncRepository.getNeoDBWishlistMovies() }
                        val d8 = async { syncRepository.getNeoDBWishlistTV() }
                        val d9 = async { syncRepository.getNeoDBProgressMovies() }
                        val d10 = async { syncRepository.getNeoDBProgressTV() }
                        Decade(d1.await(), d2.await(), d3.await(), d4.await(),
                            d5.await(), d6.await(), d7.await(), d8.await(), d9.await(), d10.await())
                    }

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

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = withTmdb,
                            hasMoreItems = withTmdb.isNotEmpty(),
                            stats = stats
                        )
                    }

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.error(TAG, "同步数据加载失败", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            syncError = e.message ?: "同步失败"
                        )
                    }
                }
            } finally {
                checkMutex.unlock()
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
                try {
                    semaphore.withPermit {
                        withTimeout(15000) {
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
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "TMDB详情获取失败，使用原始数据", e, mapOf("title" to item.title))
                    item
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
            if (cachedPoster == null) {
                try {
                    cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = null)))
                } catch (_: Exception) {
                }
            }
            val fallbackPosterUrl = cachedPoster?.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
            TmdbDetailResult(posterUrl = fallbackPosterUrl, chineseTitle = null)
        }
    }

    fun syncSelected() {
        viewModelScope.launch {
            val selectedItems = _uiState.value.items.filter { it.isSelected && !it.isSynced }
            if (selectedItems.isEmpty()) return@launch
            syncItems(selectedItems)
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            val allItems = _uiState.value.items.filter { !it.isSynced }
            if (allItems.isEmpty()) return@launch
            syncItems(allItems)
        }
    }

    private suspend fun syncItems(items: List<SyncListItem>) {
        if (!syncMutex.tryLock()) {
            _uiState.update { it.copy(syncError = "已有同步操作进行中") }
            return
        }

        try {
            val uuids = items.map { it.uuid }.toSet()
            _uiState.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.uuid in uuids) it.copy(isSyncing = true) else it
                    }
                )
            }

            items.forEachIndexed { index, item ->
                _uiState.update { state ->
                    state.copy(
                        syncProgress = SyncProgress(
                            current = index + 1,
                            total = items.size,
                            currentTitle = item.title
                        )
                    )
                }

                try {
                    val result = syncRepository.addToNeoDB(item.traktItem, item.shelfType)
                    result.fold(
                        onSuccess = {
                            _uiState.update { state ->
                                state.copy(
                                    items = state.items.map {
                                        if (it.uuid == item.uuid) it.copy(isSynced = true, isSyncing = false, syncError = null)
                                        else it
                                    }
                                )
                            }
                        },
                        onFailure = { error ->
                            AppLogger.warn(TAG, "同步单项失败", error, mapOf("title" to item.title))
                            _uiState.update { state ->
                                state.copy(
                                    items = state.items.map {
                                        if (it.uuid == item.uuid) it.copy(isSyncing = false, syncError = error.message)
                                        else it
                                    }
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.warn(TAG, "同步单项异常", e, mapOf("title" to item.title))
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.uuid == item.uuid) it.copy(isSyncing = false, syncError = e.message)
                                else it
                            }
                        )
                    }
                }
            }

            _uiState.update { it.copy(syncProgress = null) }

            updateCacheAndStats()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun updateCacheAndStats() {
        val remaining = _uiState.value.items.filter { !it.isSynced }
        try {
            cacheDao.replaceSyncCache(remaining.map { it.toSyncCacheEntity() })
        } catch (e: Exception) {
            AppLogger.warn(TAG, "更新同步缓存失败", e)
        }
        val stats = buildStats(remaining)
        _uiState.update {
            it.copy(
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

    private data class Decade<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(
        val v1: T1, val v2: T2, val v3: T3, val v4: T4, val v5: T5,
        val v6: T6, val v7: T7, val v8: T8, val v9: T9, val v10: T10
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
    val isSelected: Boolean = false,
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
    val stats: Map<String, Int> = emptyMap(),
    val isSelectionMode: Boolean = false
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
