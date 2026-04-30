package com.example.traktneosync.data

import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBMark
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.trakt.TraktApiService
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.data.trakt.TraktWatchedItem
import com.example.traktneosync.data.trakt.TraktWatchlistItem
import com.example.traktneosync.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
}
