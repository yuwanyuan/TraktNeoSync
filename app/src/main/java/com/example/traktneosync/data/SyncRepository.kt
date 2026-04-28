package com.example.traktneosync.data

import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBMark
import com.example.traktneosync.data.neodb.NeoDBMarkRequest
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.trakt.TraktApiService
import com.example.traktneosync.data.trakt.TraktIds
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.data.trakt.TraktWatchedItem
import com.example.traktneosync.data.trakt.TraktWatchlistItem
import com.example.traktneosync.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val traktApi: TraktApiService,
    private val neoDBApi: NeoDBApiService,
    private val authRepository: AuthRepository,
    private val traktOAuthManager: TraktOAuthManager,
    private val neodbOAuthManager: NeoDBOAuthManager
) {
    companion object {
        private const val TAG = "SyncRepository"
    }

    // ========== Trakt 数据获取 ==========
    
    suspend fun getTraktWatchedMovies(): List<TraktWatchedItem> = withContext(Dispatchers.IO) {
        if (!traktOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.traktAccessToken.first() ?: return@withContext emptyList()
        return@withContext try {
            traktApi.getWatchedMovies("Bearer $token")
        } catch (e: Exception) {
            AppLogger.error(TAG, "获取Trakt已观看电影失败", e, mapOf("api" to "getWatchedMovies"))
            emptyList()
        }
    }
    
    suspend fun getTraktWatchedShows(): List<TraktWatchedItem> = withContext(Dispatchers.IO) {
        if (!traktOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.traktAccessToken.first() ?: return@withContext emptyList()
        return@withContext try {
            traktApi.getWatchedShows("Bearer $token")
        } catch (e: Exception) {
            AppLogger.error(TAG, "获取Trakt已观看剧集失败", e, mapOf("api" to "getWatchedShows"))
            emptyList()
        }
    }
    
    suspend fun getTraktMovieWatchlist(): List<TraktWatchlistItem> = withContext(Dispatchers.IO) {
        if (!traktOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.traktAccessToken.first() ?: return@withContext emptyList()
        return@withContext try {
            traktApi.getMovieWatchlist("Bearer $token")
        } catch (e: Exception) {
            AppLogger.error(TAG, "获取Trakt电影待看清单失败", e, mapOf("api" to "getMovieWatchlist"))
            emptyList()
        }
    }
    
    suspend fun getTraktShowWatchlist(): List<TraktWatchlistItem> = withContext(Dispatchers.IO) {
        if (!traktOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.traktAccessToken.first() ?: return@withContext emptyList()
        return@withContext try {
            traktApi.getShowWatchlist("Bearer $token")
        } catch (e: Exception) {
            AppLogger.error(TAG, "获取Trakt剧集待看清单失败", e, mapOf("api" to "getShowWatchlist"))
            emptyList()
        }
    }
    
    // ========== NeoDB 数据获取 ==========
    
    suspend fun getNeoDBCompletedMovies(): List<NeoDBMark> = getNeoDBShelf("complete", "movie")
    suspend fun getNeoDBCompletedTV(): List<NeoDBMark> = getNeoDBShelf("complete", "tv")
    suspend fun getNeoDBWishlistMovies(): List<NeoDBMark> = getNeoDBShelf("wishlist", "movie")
    suspend fun getNeoDBWishlistTV(): List<NeoDBMark> = getNeoDBShelf("wishlist", "tv")
    suspend fun getNeoDBProgressMovies(): List<NeoDBMark> = getNeoDBShelf("progress", "movie")
    suspend fun getNeoDBProgressTV(): List<NeoDBMark> = getNeoDBShelf("progress", "tv")
    
    private suspend fun getNeoDBShelf(shelfType: String, category: String? = null): List<NeoDBMark> = withContext(Dispatchers.IO) {
        if (!neodbOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.neodbAccessToken.first() ?: return@withContext emptyList()
        val allMarks = mutableListOf<NeoDBMark>()
        var page = 1
        
        while (true) {
            try {
                val response = neoDBApi.getShelf("Bearer $token", shelfType, page)
                val filtered = if (category != null) {
                    response.data.filter { it.item.category == category }
                } else {
                    response.data
                }
                allMarks.addAll(filtered)
                
                if (page >= response.pages) break
                page++
            } catch (e: Exception) {
                AppLogger.error(TAG, "获取NeoDB书架失败", e, mapOf("shelf" to shelfType, "page" to page, "category" to (category ?: "all")))
                break
            }
        }
        
        return@withContext allMarks
    }
    
    // ========== 同步检查 ==========

    data class SyncCheckResult(
        val traktItem: TraktItem,
        val neoDBMark: NeoDBMark?,
        val isInNeoDB: Boolean,
        val traktSource: TraktSource // 区分来源：已观看或待看
    )

    enum class TraktSource {
        WATCHED,  // 已观看 → 应同步到 complete
        WATCHLIST // 待看清单 → 应同步到 wishlist
    }

    data class TraktItem(
        val title: String,
        val year: Int?,
        val type: String, // "movie" or "show"
        val ids: TraktIds,
        val watchedAt: String? = null,
        val plays: Int = 0
    )
    
    // 检查 Trakt 观看记录是否已在 NeoDB 标记
    fun checkSyncStatus(): Flow<SyncCheckResult> = flow {
        val traktWatchedMovies = getTraktWatchedMovies()
        val traktWatchedShows = getTraktWatchedShows()
        val traktMovieWatchlist = getTraktMovieWatchlist()
        val traktShowWatchlist = getTraktShowWatchlist()

        val neoDBCompletedMovies = getNeoDBCompletedMovies()
        val neoDBCompletedTV = getNeoDBCompletedTV()
        val neoDBWishlistMovies = getNeoDBWishlistMovies()
        val neoDBWishlistTV = getNeoDBWishlistTV()
        val neoDBProgressMovies = getNeoDBProgressMovies()
        val neoDBProgressTV = getNeoDBProgressTV()

        // 转换 Trakt 已观看电影
        traktWatchedMovies.forEach { watched ->
            watched.movie?.let { movie ->
                val traktItem = TraktItem(
                    title = movie.title,
                    year = movie.year,
                    type = "movie",
                    ids = movie.ids,
                    watchedAt = watched.lastWatchedAt,
                    plays = watched.plays
                )

                val existingMark = findMatchingNeoDBMark(
                    traktItem,
                    neoDBCompletedMovies,
                    neoDBCompletedTV,
                    neoDBWishlistMovies,
                    neoDBWishlistTV,
                    neoDBProgressMovies,
                    neoDBProgressTV
                )

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = existingMark != null,
                    traktSource = TraktSource.WATCHED
                ))
            }
        }

        // 转换 Trakt 已观看剧集
        traktWatchedShows.forEach { watched ->
            watched.show?.let { show ->
                val traktItem = TraktItem(
                    title = show.title,
                    year = show.year,
                    type = "show",
                    ids = show.ids,
                    watchedAt = watched.lastWatchedAt,
                    plays = watched.plays
                )

                val existingMark = findMatchingNeoDBMark(
                    traktItem,
                    neoDBCompletedMovies,
                    neoDBCompletedTV,
                    neoDBWishlistMovies,
                    neoDBWishlistTV,
                    neoDBProgressMovies,
                    neoDBProgressTV
                )

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = existingMark != null,
                    traktSource = TraktSource.WATCHED
                ))
            }
        }

        // 转换 Trakt 待看清单
        traktMovieWatchlist.forEach { item ->
            item.movie?.let { movie ->
                val traktItem = TraktItem(
                    title = movie.title,
                    year = movie.year,
                    type = "movie",
                    ids = movie.ids
                )

                val existingMark = findMatchingNeoDBMark(
                    traktItem,
                    neoDBCompletedMovies,
                    neoDBCompletedTV,
                    neoDBWishlistMovies,
                    neoDBWishlistTV,
                    neoDBProgressMovies,
                    neoDBProgressTV
                )

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = existingMark != null,
                    traktSource = TraktSource.WATCHLIST
                ))
            }
        }

        traktShowWatchlist.forEach { item ->
            item.show?.let { show ->
                val traktItem = TraktItem(
                    title = show.title,
                    year = show.year,
                    type = "show",
                    ids = show.ids
                )

                val existingMark = findMatchingNeoDBMark(
                    traktItem,
                    neoDBCompletedMovies,
                    neoDBCompletedTV,
                    neoDBWishlistMovies,
                    neoDBWishlistTV,
                    neoDBProgressMovies,
                    neoDBProgressTV
                )

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = existingMark != null,
                    traktSource = TraktSource.WATCHLIST
                ))
            }
        }
    }
    
    fun findMatchingNeoDBMark(
        traktItem: TraktItem,
        completedMovies: List<NeoDBMark>,
        completedTV: List<NeoDBMark>,
        wishlistMovies: List<NeoDBMark>,
        wishlistTV: List<NeoDBMark>,
        progressMovies: List<NeoDBMark> = emptyList(),
        progressTV: List<NeoDBMark> = emptyList()
    ): NeoDBMark? {
        val allNeoDBMarks = completedMovies + completedTV + wishlistMovies + wishlistTV + progressMovies + progressTV

        // 优先用 TMDB ID 匹配（最稳定）
        traktItem.ids.tmdb?.let { tmdbId ->
            allNeoDBMarks.find { mark ->
                mark.item.externalResources.any { it.url.contains(tmdbId.toString()) }
            }?.let { return it }
        }

        // 次选用 IMDB ID 匹配
        traktItem.ids.imdb?.let { imdbId ->
            allNeoDBMarks.find { mark ->
                mark.item.externalResources.any { it.url.contains(imdbId, ignoreCase = true) }
            }?.let { return it }
        }

        // 最后尝试标题 + 年份匹配
        return allNeoDBMarks.find { mark ->
            val titleMatch = mark.item.displayTitle.equals(traktItem.title, ignoreCase = true) ||
                    mark.item.displayTitle.contains(traktItem.title, ignoreCase = true) ||
                    traktItem.title.contains(mark.item.displayTitle, ignoreCase = true)

            titleMatch && (traktItem.year == null || mark.item.brief.contains(traktItem.year.toString()))
        }
    }
    
    // ========== 一键添加到 NeoDB ==========
    
    suspend fun addToNeoDB(
        traktItem: TraktItem,
        shelfType: String, // "wishlist", "progress", "complete"
        rating: Int? = null,
        comment: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!neodbOAuthManager.ensureValidToken()) return@withContext Result.failure(Exception("NeoDB token无效"))
        val token = authRepository.neodbAccessToken.first()
            ?: return@withContext Result.failure(Exception("NeoDB not authenticated"))
        
        return@withContext try {
            // 1. 先在 NeoDB 搜索该条目
            val searchResults = neoDBApi.search(
                "Bearer $token",
                "${traktItem.title} ${traktItem.year ?: ""}".trim(),
                if (traktItem.type == "movie") "movie" else "tv",
                1
            )
            
            if (searchResults.data.isEmpty()) {
                return@withContext Result.failure(Exception("Item not found in NeoDB"))
            }
            
            // 2. 找到最匹配的条目
            val bestMatch = searchResults.data.firstOrNull { entry ->
                // 尝试匹配 IMDB/TMDB ID
                traktItem.ids.imdb?.let { imdbId ->
                    if (entry.externalResources.any { it.url.contains(imdbId, ignoreCase = true) }) {
                        return@firstOrNull true
                    }
                }
                traktItem.ids.tmdb?.let { tmdbId ->
                    if (entry.externalResources.any { it.url.contains(tmdbId.toString()) }) {
                        return@firstOrNull true
                    }
                }
                
                // 标题匹配
                entry.displayTitle.equals(traktItem.title, ignoreCase = true) ||
                        entry.displayTitle.contains(traktItem.title, ignoreCase = true)
            } ?: searchResults.data.first()
            
            // 3. 添加到书架
            val markRequest = NeoDBMarkRequest(
                shelfType = shelfType,
                ratingGrade = rating,
                commentText = comment
            )
            
            neoDBApi.addOrUpdateMark("Bearer $token", bestMatch.uuid, markRequest)
            
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.error(TAG, "添加到NeoDB失败", e, mapOf("title" to traktItem.title, "type" to traktItem.type, "shelf" to shelfType))
            Result.failure(e)
        }
    }
    
    // ========== 批量同步 ==========

    suspend fun syncAllWatchedToNeoDB(): SyncProgress {
        val results = mutableListOf<SyncResult>()

        checkSyncStatus().collect { check ->
            if (!check.isInNeoDB) {
                // 根据来源选择正确的书架类型
                val shelfType = when (check.traktSource) {
                    TraktSource.WATCHED -> "complete"
                    TraktSource.WATCHLIST -> "wishlist"
                }
                val result = addToNeoDB(check.traktItem, shelfType)
                results.add(SyncResult(
                    traktItem = check.traktItem,
                    success = result.isSuccess,
                    error = result.exceptionOrNull()?.message
                ))
            }
        }

        return SyncProgress(
            total = results.size,
            success = results.count { it.success },
            failed = results.count { !it.success },
            results = results
        )
    }
    
    data class SyncProgress(
        val total: Int,
        val success: Int,
        val failed: Int,
        val results: List<SyncResult>
    )
    
    data class SyncResult(
        val traktItem: TraktItem,
        val success: Boolean,
        val error: String?
    )
}
