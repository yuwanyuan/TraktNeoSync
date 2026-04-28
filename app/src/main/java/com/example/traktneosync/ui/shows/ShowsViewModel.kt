package com.example.traktneosync.ui.shows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.SyncRepository
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.cache.PosterCacheEntity
import com.example.traktneosync.data.cache.TraktCacheEntity
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tmdbApi: TmdbApiService,
    private val cacheDao: CacheDao,
) : ViewModel() {

    companion object {
        private const val TAG = "ShowsViewModel"
        private const val TMDB_CONCURRENCY = 5
    }

    private val _uiState = MutableStateFlow(ShowsUiState())
    val uiState: StateFlow<ShowsUiState> = _uiState

    init {
        loadShows()
    }

    fun selectTab(index: Int) {
        if (_uiState.value.selectedTab == index) return
        _uiState.value = _uiState.value.copy(selectedTab = index, errorMessage = null)
        loadShows()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        loadShows(force = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadShows(force: Boolean = false) {
        viewModelScope.launch {
            val status = if (_uiState.value.selectedTab == 0) "watched" else "watchlist"
            val currentItems = _uiState.value.items

            // 如果已有数据且不是强制刷新，直接返回
            if (!force && currentItems.isNotEmpty()) {
                AppLogger.debug(TAG, "跳过加载: 已有数据", mapOf("tab" to status, "count" to currentItems.size))
                return@launch
            }

            // 1. 先读本地缓存
            val cached = try {
                cacheDao.getCachedItems("show", status).map { it.toShowItem() }
            } catch (e: Exception) {
                AppLogger.error(TAG, "读取缓存失败", e)
                emptyList()
            }

            if (cached.isNotEmpty()) {
                _uiState.value = _uiState.value.withItems(_uiState.value.selectedTab, cached, isLoading = true)
                AppLogger.debug(TAG, "从缓存加载剧集", mapOf("count" to cached.size, "tab" to status))
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // 2. 网络请求
            val items = try {
                val rawItems = if (_uiState.value.selectedTab == 0) {
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
                AppLogger.info(TAG, "从Trakt获取剧集数据", mapOf("count" to rawItems.size, "tab" to status))

                // 按时间降序排序
                val sorted = rawItems.sortedByDescending { item ->
                    try {
                        item.lastWatchedAt?.let { Instant.parse(it).epochSecond } ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }

                // 并行获取 TMDB 海报与中文标题（带缓存）
                val semaphore = Semaphore(TMDB_CONCURRENCY)
                val deferredList = sorted.map { item ->
                    async {
                        withTimeout(15000) {
                            semaphore.withPermit {
                                val detail = item.tmdbId?.let { fetchTmdbDetailCached(it) }
                                item.copy(
                                    title = detail?.chineseTitle?.takeIf { it.isNotBlank() } ?: item.title,
                                    posterUrl = detail?.posterUrl
                                )
                            }
                        }
                    }
                }
                val result = deferredList.awaitAll()
                val withPoster = result.count { it.posterUrl != null }
                AppLogger.debug(TAG, "海报加载完成", mapOf("withPoster" to withPoster, "total" to result.size))

                // 写入缓存
                try {
                    cacheDao.replaceItems("show", status, result.map { it.toEntity("show", status) })
                    AppLogger.debug(TAG, "已写入缓存", mapOf("count" to result.size))
                } catch (e: Exception) {
                    AppLogger.error(TAG, "写入缓存失败", e)
                }

                result
            } catch (e: Exception) {
                AppLogger.error(TAG, "加载剧集列表失败", e)
                if (cached.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "加载失败: ${e.localizedMessage ?: "网络错误"}"
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.withItems(
                    _uiState.value.selectedTab,
                    emptyList(),
                    isLoading = false
                ).copy(errorMessage = "加载失败: ${e.localizedMessage ?: "网络错误"}")
                return@launch
            }

            _uiState.value = _uiState.value.withItems(
                _uiState.value.selectedTab,
                items,
                isLoading = false
            ).copy(errorMessage = null)
        }
    }

    private fun ShowsUiState.withItems(tab: Int, items: List<ShowItem>, isLoading: Boolean): ShowsUiState {
        return if (tab == 0) {
            copy(watchedItems = items, isLoading = isLoading)
        } else {
            copy(watchlistItems = items, isLoading = isLoading)
        }
    }

    private suspend fun fetchTmdbDetailCached(tmdbId: Long): TmdbDetailResult {
        val cachedPoster = try { cacheDao.getPoster(tmdbId) } catch (e: Exception) { null }

        return try {
            val detail = tmdbApi.getTvDetail(tmdbId)
            val path = detail.posterPath
            val chineseTitle = detail.name
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = path)))
            } catch (e: Exception) {
                AppLogger.warn(TAG, "写入海报缓存失败", e, mapOf("tmdbId" to tmdbId))
            }
            val posterUrl = path?.let { "https://image.tmdb.org/t/p/w200$it" }
            AppLogger.debug(TAG, "TMDB中文标题", mapOf("tmdbId" to tmdbId, "title" to chineseTitle))
            TmdbDetailResult(posterUrl = posterUrl, chineseTitle = chineseTitle)
        } catch (e: Exception) {
            AppLogger.warn(TAG, "TMDB详情获取失败", e, mapOf("tmdbId" to tmdbId))
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = null)))
            } catch (e: Exception) { AppLogger.warn(TAG, "写入空缓存失败", e, mapOf("tmdbId" to tmdbId)) }
            val fallbackPosterUrl = cachedPoster?.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
            TmdbDetailResult(posterUrl = fallbackPosterUrl, chineseTitle = null)
        }
    }

    data class TmdbDetailResult(
        val posterUrl: String?,
        val chineseTitle: String?
    )
}

// ========== 数据转换 ==========

private fun TraktCacheEntity.toShowItem() = ShowItem(
    title = title,
    year = year,
    plays = plays,
    imdbId = imdbId,
    tmdbId = tmdbId,
    posterUrl = posterUrl,
    lastWatchedAt = lastWatchedAt
)

private fun ShowItem.toEntity(type: String, status: String) = TraktCacheEntity(
    id = "${type}_${status}_${tmdbId ?: imdbId ?: (title + year).hashCode()}",
    title = title,
    year = year,
    type = type,
    status = status,
    plays = plays,
    imdbId = imdbId,
    tmdbId = tmdbId,
    posterUrl = posterUrl,
    lastWatchedAt = lastWatchedAt,
    cachedAt = System.currentTimeMillis()
)

data class ShowsUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val watchedItems: List<ShowItem> = emptyList(),
    val watchlistItems: List<ShowItem> = emptyList(),
    val errorMessage: String? = null
) {
    val items: List<ShowItem>
        get() = if (selectedTab == 0) watchedItems else watchlistItems
}

data class ShowItem(
    val title: String,
    val year: Int?,
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val lastWatchedAt: String? = null
)
