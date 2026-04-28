package com.example.traktneosync.ui.movies

import android.util.Log
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
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tmdbApi: TmdbApiService,
    private val cacheDao: CacheDao,
) : ViewModel() {

    companion object {
        private const val TAG = "MoviesViewModel"
        private const val TMDB_CONCURRENCY = 5
    }

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState

    init {
        loadMovies()
    }

    fun selectTab(index: Int) {
        if (_uiState.value.selectedTab == index) return
        _uiState.value = _uiState.value.copy(selectedTab = index, errorMessage = null)
        loadMovies()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        loadMovies(force = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadMovies(force: Boolean = false) {
        viewModelScope.launch {
            val status = if (_uiState.value.selectedTab == 0) "watched" else "watchlist"
            val currentItems = _uiState.value.items

            // 如果已有数据且不是强制刷新，直接返回
            if (!force && currentItems.isNotEmpty()) {
                AppLogger.log("MoviesViewModel: tab=$status 已有 ${currentItems.size} 条数据，跳过加载")
                return@launch
            }

            // 1. 先读本地缓存，有数据先展示，减少白屏
            val cached = try {
                cacheDao.getCachedItems("movie", status).map { it.toMovieItem() }
            } catch (e: Exception) {
                AppLogger.log("MoviesViewModel: 读取缓存失败", e)
                emptyList()
            }

            if (cached.isNotEmpty()) {
                _uiState.value = _uiState.value.withItems(_uiState.value.selectedTab, cached, isLoading = true)
                AppLogger.log("MoviesViewModel: 从缓存加载 ${cached.size} 条电影, tab=$status")
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // 2. 请求网络数据
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
                                posterUrl = null,
                                lastWatchedAt = watched.lastWatchedAt
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
                                posterUrl = null,
                                lastWatchedAt = item.listedAt
                            )
                        }
                    }.filterNotNull()
                }
                AppLogger.log("MoviesViewModel: 从Trakt获取 ${rawItems.size} 条电影数据, tab=$status")

                // 按时间降序排序
                val sorted = rawItems.sortedByDescending { item ->
                    parseEpochSecond(item.lastWatchedAt)
                }

                // 并行获取 TMDB 海报与中文标题（带缓存）
                val semaphore = Semaphore(TMDB_CONCURRENCY)
                val deferredList = sorted.map { item ->
                    async {
                        semaphore.withPermit {
                            val detail = item.tmdbId?.let { fetchTmdbDetailCached(it, isMovie = true) }
                            item.copy(
                                title = detail?.chineseTitle?.takeIf { it.isNotBlank() } ?: item.title,
                                posterUrl = detail?.posterUrl
                            )
                        }
                    }
                }
                val result = deferredList.awaitAll()
                val withPoster = result.count { it.posterUrl != null }
                AppLogger.log("MoviesViewModel: 海报加载完成 $withPoster/${result.size}")

                // 写入列表缓存
                try {
                    cacheDao.replaceItems("movie", status, result.map { it.toEntity("movie", status) })
                    AppLogger.log("MoviesViewModel: 已写入缓存 ${result.size} 条")
                } catch (e: Exception) {
                    AppLogger.log("MoviesViewModel: 写入缓存失败", e)
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error loading movies: ${e.message}")
                AppLogger.log("MoviesViewModel: 加载电影列表失败", e)
                // 网络失败且已有缓存时，保持缓存展示并停止 loading
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

    private fun MoviesUiState.withItems(tab: Int, items: List<MovieItem>, isLoading: Boolean): MoviesUiState {
        return if (tab == 0) {
            copy(watchedItems = items, isLoading = isLoading)
        } else {
            copy(watchlistItems = items, isLoading = isLoading)
        }
    }

    private suspend fun fetchTmdbDetailCached(tmdbId: Long, isMovie: Boolean): TmdbDetailResult {
        // 查海报缓存（也用于判断是否需要请求TMDB）
        val cachedPoster = try { cacheDao.getPoster(tmdbId) } catch (e: Exception) { null }

        // 如果只有海报缓存且之前成功获取过（posterPath 有值或明确为null），跳过网络请求
        // 但中文标题需要单独缓存，这里先简化：命中缓存时只返回海报，中文标题走独立缓存或重新请求
        // 实际上为了获取中文标题，即使海报缓存命中也应该请求 TMDB（除非我们也缓存中文标题）
        // 这里改为：始终请求 TMDB 获取中文标题，但海报可用缓存避免重复下载

        return try {
            val (path, chineseTitle) = if (isMovie) {
                val d = tmdbApi.getMovieDetail(tmdbId)
                Pair(d.posterPath, d.title)
            } else {
                val d = tmdbApi.getTvDetail(tmdbId)
                Pair(d.posterPath, d.name)
            }
            // 写入海报缓存（包括 null，避免重复请求无海报条目）
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = path)))
            } catch (e: Exception) {
                AppLogger.log("MoviesViewModel: 写入海报缓存失败 tmdbId=$tmdbId", e)
            }
            val posterUrl = path?.let { "https://image.tmdb.org/t/p/w200$it" }
            AppLogger.log("MoviesViewModel: TMDB中文标题 tmdbId=$tmdbId, title=$chineseTitle")
            TmdbDetailResult(posterUrl = posterUrl, chineseTitle = chineseTitle)
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fetch failed for id=$tmdbId: ${e.message}")
            AppLogger.log("MoviesViewModel: TMDB详情获取失败 tmdbId=$tmdbId, ${e.message}")
            // 写入空缓存，防止反复请求失败条目
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = null)))
            } catch (_: Exception) { }
            // 失败时如果有缓存的海报，返回缓存的海报URL
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

private fun TraktCacheEntity.toMovieItem() = MovieItem(
    title = title,
    year = year,
    plays = plays,
    imdbId = imdbId,
    tmdbId = tmdbId,
    posterUrl = posterUrl,
    lastWatchedAt = lastWatchedAt
)

private fun MovieItem.toEntity(type: String, status: String) = TraktCacheEntity(
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

data class MoviesUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0=已观看, 1=待看
    val watchedItems: List<MovieItem> = emptyList(),
    val watchlistItems: List<MovieItem> = emptyList(),
    val errorMessage: String? = null
) {
    val items: List<MovieItem>
        get() = if (selectedTab == 0) watchedItems else watchlistItems
}

data class MovieItem(
    val title: String,
    val year: Int?,
    val plays: Int = 0,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val posterUrl: String? = null,
    val lastWatchedAt: String? = null
)

private fun parseEpochSecond(isoString: String?): Long {
    return try {
        isoString?.let { Instant.parse(it).epochSecond } ?: 0L
    } catch (e: Exception) {
        0L
    }
}
