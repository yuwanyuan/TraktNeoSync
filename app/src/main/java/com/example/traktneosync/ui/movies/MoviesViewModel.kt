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
        _uiState.value = _uiState.value.copy(selectedTab = index)
        loadMovies()
    }

    fun refresh() {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            val status = if (_uiState.value.selectedTab == 0) "watched" else "watchlist"

            // 1. 先读本地缓存，有数据先展示，减少白屏
            val cached = try {
                cacheDao.getCachedItems("movie", status).map { it.toMovieItem() }
            } catch (e: Exception) {
                AppLogger.log("MoviesViewModel: 读取缓存失败", e)
                emptyList()
            }

            if (cached.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(items = cached, isLoading = true)
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

                // 并行获取 TMDB 海报（带缓存）
                val semaphore = Semaphore(TMDB_CONCURRENCY)
                val deferredList = sorted.map { item ->
                    async {
                        semaphore.withPermit {
                            val posterUrl = item.tmdbId?.let { fetchTmdbPosterCached(it, isMovie = true) }
                            item.copy(posterUrl = posterUrl)
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
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = items
            )
        }
    }

    private suspend fun fetchTmdbPosterCached(tmdbId: Long, isMovie: Boolean): String? {
        // 查海报缓存
        val cachedPoster = try { cacheDao.getPoster(tmdbId) } catch (e: Exception) { null }
        if (cachedPoster != null) {
            return cachedPoster.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
        }

        // 未命中，请求 TMDB
        return try {
            val path = if (isMovie) {
                tmdbApi.getMovieDetail(tmdbId).posterPath
            } else {
                tmdbApi.getTvDetail(tmdbId).posterPath
            }
            // 写入海报缓存（包括 null，避免重复请求无海报条目）
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = path)))
            } catch (e: Exception) {
                AppLogger.log("MoviesViewModel: 写入海报缓存失败 tmdbId=$tmdbId", e)
            }
            path?.let { "https://image.tmdb.org/t/p/w200$it" }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fetch failed for id=$tmdbId: ${e.message}")
            AppLogger.log("MoviesViewModel: TMDB海报获取失败 tmdbId=$tmdbId, ${e.message}")
            // 写入空缓存，防止反复请求失败条目
            try {
                cacheDao.insertPosters(listOf(PosterCacheEntity(tmdbId = tmdbId, posterPath = null)))
            } catch (_: Exception) { }
            null
        }
    }
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
    val items: List<MovieItem> = emptyList()
)

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
