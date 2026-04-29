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
import kotlinx.coroutines.delay
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
        private const val SYNC_DELAY_MS = 500L
        private val TMDB_ID_REGEX_CACHE = mutableMapOf<Long, Regex>()
        private fun tmdbIdRegex(tmdbId: Long): Regex {
            return TMDB_ID_REGEX_CACHE.getOrPut(tmdbId) {
                Regex("/${tmdbId}(?:/|$|\\D)")
            }
        }
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
            val allItems = mutableListOf<TraktWatchlistItem>()
            var page = 1
            while (true) {
                val response = traktApi.getMovieWatchlist("Bearer $token", page = page)
                if (response.isEmpty()) break
                allItems.addAll(response)
                if (response.size < 100) break
                page++
            }
            allItems
        } catch (e: Exception) {
            AppLogger.error(TAG, "获取Trakt电影待看清单失败", e, mapOf("api" to "getMovieWatchlist"))
            emptyList()
        }
    }

    suspend fun getTraktShowWatchlist(): List<TraktWatchlistItem> = withContext(Dispatchers.IO) {
        if (!traktOAuthManager.ensureValidToken()) return@withContext emptyList()
        val token = authRepository.traktAccessToken.first() ?: return@withContext emptyList()
        return@withContext try {
            val allItems = mutableListOf<TraktWatchlistItem>()
            var page = 1
            while (true) {
                val response = traktApi.getShowWatchlist("Bearer $token", page = page)
                if (response.isEmpty()) break
                allItems.addAll(response)
                if (response.size < 100) break
                page++
            }
            allItems
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
                val response = neoDBApi.getShelf("Bearer $token", shelfType, page, category)
                allMarks.addAll(response.data)

                if (page >= response.pages || response.pages <= 0) break
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
        val traktSource: TraktSource
    )

    enum class TraktSource {
        WATCHED,
        WATCHLIST
    }

    data class TraktItem(
        val title: String,
        val year: Int?,
        val type: String,
        val ids: TraktIds,
        val watchedAt: String? = null,
        val plays: Int = 0
    )

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

                val isInNeoDB = existingMark != null &&
                    isShelfTypeCompatible(existingMark.shelfType, TraktSource.WATCHED)

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = isInNeoDB,
                    traktSource = TraktSource.WATCHED
                ))
            }
        }

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

                val isInNeoDB = existingMark != null &&
                    isShelfTypeCompatible(existingMark.shelfType, TraktSource.WATCHED)

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = isInNeoDB,
                    traktSource = TraktSource.WATCHED
                ))
            }
        }

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

                val isInNeoDB = existingMark != null &&
                    isShelfTypeCompatible(existingMark.shelfType, TraktSource.WATCHLIST)

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = isInNeoDB,
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

                val isInNeoDB = existingMark != null &&
                    isShelfTypeCompatible(existingMark.shelfType, TraktSource.WATCHLIST)

                emit(SyncCheckResult(
                    traktItem = traktItem,
                    neoDBMark = existingMark,
                    isInNeoDB = isInNeoDB,
                    traktSource = TraktSource.WATCHLIST
                ))
            }
        }
    }

    private fun isShelfTypeCompatible(neoDBShelfType: String?, traktSource: TraktSource): Boolean {
        val shelf = neoDBShelfType ?: return false
        return when (traktSource) {
            TraktSource.WATCHED -> shelf == "complete" || shelf == "progress"
            TraktSource.WATCHLIST -> shelf == "wishlist"
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
        val candidateMarks = if (traktItem.type == "movie") {
            completedMovies + wishlistMovies + progressMovies
        } else {
            completedTV + wishlistTV + progressTV
        }

        traktItem.ids.tmdb?.let { tmdbId ->
            val regex = tmdbIdRegex(tmdbId)
            candidateMarks.find { mark ->
                mark.item.externalResources.any { res ->
                    val url = res.url
                    (url.contains("themoviedb.org", ignoreCase = true) || url.contains("tmdb.org", ignoreCase = true)) &&
                            regex.containsMatchIn(url)
                }
            }?.let { return it }
        }

        traktItem.ids.imdb?.let { imdbId ->
            candidateMarks.find { mark ->
                mark.item.externalResources.any { res ->
                    val url = res.url
                    (url.contains("imdb.com", ignoreCase = true) || url.contains("imdb.org", ignoreCase = true)) &&
                            url.contains("/$imdbId", ignoreCase = true)
                }
            }?.let { return it }
        }

        return null
    }

    // ========== 一键添加到 NeoDB ==========

    suspend fun addToNeoDB(
        traktItem: TraktItem,
        shelfType: String,
        rating: Int? = null,
        comment: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!neodbOAuthManager.ensureValidToken()) return@withContext Result.failure(Exception("NeoDB token无效"))
        val token = authRepository.neodbAccessToken.first()
            ?: return@withContext Result.failure(Exception("NeoDB not authenticated"))

        return@withContext try {
            val category = if (traktItem.type == "movie") "movie" else "tv"
            val searchQuery = traktItem.title

            val searchResults = neoDBApi.search(
                "Bearer $token",
                searchQuery,
                category,
                1
            )

            if (searchResults.data.isEmpty()) {
                return@withContext Result.failure(Exception("在 NeoDB 中未找到: ${traktItem.title}"))
            }

            val bestMatch = findBestMatch(searchResults.data, traktItem)
                ?: return@withContext Result.failure(Exception("未找到精确匹配: ${traktItem.title}，请手动搜索添加"))

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

    private fun findBestMatch(entries: List<com.example.traktneosync.data.neodb.NeoDBEntry>, traktItem: TraktItem): com.example.traktneosync.data.neodb.NeoDBEntry? {
        traktItem.ids.tmdb?.let { tmdbId ->
            val regex = tmdbIdRegex(tmdbId)
            entries.firstOrNull { entry ->
                entry.externalResources.any { res ->
                    val url = res.url
                    (url.contains("themoviedb.org", ignoreCase = true) || url.contains("tmdb.org", ignoreCase = true)) &&
                            regex.containsMatchIn(url)
                }
            }?.let { return it }
        }

        traktItem.ids.imdb?.let { imdbId ->
            entries.firstOrNull { entry ->
                entry.externalResources.any { res ->
                    val url = res.url
                    (url.contains("imdb.com", ignoreCase = true) || url.contains("imdb.org", ignoreCase = true)) &&
                            url.contains("/$imdbId", ignoreCase = true)
                }
            }?.let { return it }
        }

        return entries.firstOrNull { entry ->
            entry.displayTitle.equals(traktItem.title, ignoreCase = true) &&
                    (traktItem.year == null || entry.brief.contains(traktItem.year.toString()))
        }
    }

    // ========== 批量同步 ==========

    suspend fun syncAllWatchedToNeoDB(): SyncProgress {
        val results = mutableListOf<SyncResult>()

        checkSyncStatus().collect { check ->
            if (!check.isInNeoDB) {
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
                delay(SYNC_DELAY_MS)
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
