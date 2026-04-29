package com.example.traktneosync.ui.search

import com.example.traktneosync.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBEntry
import com.example.traktneosync.data.neodb.NeoDBMarkRequest
import com.example.traktneosync.data.neodb.extractImdbId
import com.example.traktneosync.data.neodb.extractTmdbId
import com.example.traktneosync.data.trakt.TraktApiService
import com.example.traktneosync.data.trakt.TraktIds
import com.example.traktneosync.data.trakt.TraktSyncMovie
import com.example.traktneosync.data.trakt.TraktSyncRequest
import com.example.traktneosync.data.trakt.TraktSyncShow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val neoDBApi: NeoDBApiService,
    private val authRepository: AuthRepository,
    private val tmdbApi: com.example.traktneosync.data.tmdb.TmdbApiService,
    private val traktApi: TraktApiService
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val TMDB_CONCURRENCY = 5
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onCategoryChange(category: SearchCategory) {
        _uiState.update { it.copy(category = category) }
        if (_uiState.value.query.isNotEmpty()) {
            search()
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true
                )
            }

            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "未登录 NeoDB"
                        )
                    }
                    return@launch
                }

                val category = when (_uiState.value.category) {
                    SearchCategory.ALL -> null
                    SearchCategory.MOVIE -> "movie"
                    SearchCategory.TV -> "tv"
                    SearchCategory.BOOK -> "book"
                    SearchCategory.MUSIC -> "music"
                    SearchCategory.GAME -> "game"
                }

                val result = neoDBApi.search(
                    token = "Bearer $token",
                    query = query,
                    category = category,
                    page = 1
                )

                val resultsWithTmdb = fetchTmdbInfoConcurrently(result.data)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = resultsWithTmdb,
                        error = null
                    )
                }

            } catch (e: Exception) {
                AppLogger.error(TAG, "搜索失败", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "搜索失败"
                    )
                }
            }
        }
    }

    private suspend fun fetchTmdbInfoConcurrently(entries: List<NeoDBEntry>): List<NeoDBEntry> {
        val movieTvEntries = entries.filter { it.category == "movie" || it.category == "tv" }
        val otherEntries = entries.filter { it.category != "movie" && it.category != "tv" }

        if (movieTvEntries.isEmpty()) return entries

        return try {
            coroutineScope {
                val semaphore = kotlinx.coroutines.sync.Semaphore(TMDB_CONCURRENCY)
                val deferredList = movieTvEntries.map { entry ->
                    async {
                        try {
                            semaphore.withPermit {
                                fetchTmdbInfo(entry)
                            }
                        } catch (e: Exception) {
                            AppLogger.warn(TAG, "TMDB信息获取失败", e, mapOf("title" to entry.displayTitle))
                            entry
                        }
                    }
                }
                val updatedEntries = deferredList.awaitAll()
                val updatedMap = updatedEntries.associateBy { it.uuid }
                entries.map { updatedMap[it.uuid] ?: it }
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "批量TMDB信息获取失败", e)
            entries
        }
    }

    fun addToShelf(entry: NeoDBEntry, shelfType: String) {
        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.update { it.copy(error = "未登录 NeoDB") }
                    return@launch
                }

                val request = NeoDBMarkRequest(
                    shelfType = shelfType,
                    visibility = 0
                )

                neoDBApi.addOrUpdateMark(
                    token = "Bearer $token",
                    uuid = entry.uuid,
                    request = request
                )

                _uiState.update {
                    it.copy(addedNeoDBUuids = it.addedNeoDBUuids + entry.uuid)
                }

            } catch (e: Exception) {
                AppLogger.error(TAG, "添加到NeoDB书架失败", e)
                _uiState.update {
                    it.copy(error = "NeoDB添加失败: ${e.message}")
                }
            }
        }
    }

    fun addToTraktWatchlist(entry: NeoDBEntry) {
        viewModelScope.launch {
            try {
                val token = authRepository.traktAccessToken.first()
                if (token == null) {
                    _uiState.update { it.copy(error = "未登录 Trakt") }
                    return@launch
                }

                val ids = buildTraktIds(entry)
                if (!hasValidTraktId(ids)) {
                    _uiState.update { it.copy(error = "缺少有效的IMDB/TMDB ID，无法添加到Trakt") }
                    return@launch
                }

                val request = buildTraktSyncRequest(entry, ids)

                traktApi.addToWatchlist("Bearer $token", request)

                _uiState.update {
                    it.copy(traktWatchlistUuids = it.traktWatchlistUuids + entry.uuid)
                }
                AppLogger.info(TAG, "已添加到Trakt想看", mapOf("title" to entry.displayTitle))
            } catch (e: Exception) {
                AppLogger.error(TAG, "添加到Trakt想看失败", e)
                _uiState.update { it.copy(error = "Trakt想看添加失败: ${e.message}") }
            }
        }
    }

    fun addToTraktHistory(entry: NeoDBEntry) {
        viewModelScope.launch {
            try {
                val token = authRepository.traktAccessToken.first()
                if (token == null) {
                    _uiState.update { it.copy(error = "未登录 Trakt") }
                    return@launch
                }

                val ids = buildTraktIds(entry)
                if (!hasValidTraktId(ids)) {
                    _uiState.update { it.copy(error = "缺少有效的IMDB/TMDB ID，无法添加到Trakt") }
                    return@launch
                }

                val request = buildTraktSyncRequest(entry, ids, watchedAt = java.time.Instant.now().toString())

                traktApi.addToHistory("Bearer $token", request)

                _uiState.update {
                    it.copy(traktWatchedUuids = it.traktWatchedUuids + entry.uuid)
                }
                AppLogger.info(TAG, "已添加到Trakt已看", mapOf("title" to entry.displayTitle))
            } catch (e: Exception) {
                AppLogger.error(TAG, "添加到Trakt已看失败", e)
                _uiState.update { it.copy(error = "Trakt已看添加失败: ${e.message}") }
            }
        }
    }

    private fun buildTraktIds(entry: NeoDBEntry): TraktIds {
        val imdbId = entry.extractImdbId()
        val tmdbId = entry.extractTmdbId()
        return TraktIds(
            trakt = 0,
            imdb = imdbId,
            tmdb = tmdbId
        )
    }

    private fun hasValidTraktId(ids: TraktIds): Boolean {
        return !ids.imdb.isNullOrBlank() || (ids.tmdb != null && ids.tmdb > 0)
    }

    private fun buildTraktSyncRequest(entry: NeoDBEntry, ids: TraktIds, watchedAt: String? = null): TraktSyncRequest {
        return if (entry.category == "movie") {
            TraktSyncRequest(
                movies = listOf(
                    TraktSyncMovie(
                        title = entry.displayTitle,
                        ids = ids,
                        watchedAt = watchedAt
                    )
                )
            )
        } else {
            TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        title = entry.displayTitle,
                        ids = ids,
                        watchedAt = watchedAt
                    )
                )
            )
        }
    }

    fun openRatingDialog(entry: NeoDBEntry) {
        _uiState.update {
            it.copy(
                showRatingDialog = true,
                ratingTargetEntry = entry,
                ratingValue = 5,
                ratingComment = "",
                shareToMastodon = true,
                ratingError = null,
                ratingShelfType = "complete"
            )
        }
    }

    fun dismissRatingDialog() {
        _uiState.update {
            it.copy(
                showRatingDialog = false,
                ratingTargetEntry = null,
                ratingError = null
            )
        }
    }

    fun setRatingValue(value: Int) {
        _uiState.update { it.copy(ratingValue = value) }
    }

    fun setRatingComment(comment: String) {
        _uiState.update { it.copy(ratingComment = comment) }
    }

    fun setShareToMastodon(share: Boolean) {
        _uiState.update { it.copy(shareToMastodon = share) }
    }

    fun setRatingShelfType(shelfType: String) {
        _uiState.update { it.copy(ratingShelfType = shelfType) }
    }

    fun submitRating() {
        val entry = _uiState.value.ratingTargetEntry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingRating = true, ratingError = null) }
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.update {
                        it.copy(
                            isSubmittingRating = false,
                            ratingError = "未登录 NeoDB"
                        )
                    }
                    return@launch
                }

                val visibility = if (_uiState.value.shareToMastodon) 0 else 2
                val request = NeoDBMarkRequest(
                    shelfType = _uiState.value.ratingShelfType,
                    visibility = visibility,
                    ratingGrade = _uiState.value.ratingValue,
                    commentText = _uiState.value.ratingComment.takeIf { it.isNotBlank() }
                )

                neoDBApi.addOrUpdateMark(
                    token = "Bearer $token",
                    uuid = entry.uuid,
                    request = request
                )

                _uiState.update {
                    it.copy(
                        isSubmittingRating = false,
                        showRatingDialog = false,
                        ratingTargetEntry = null,
                        addedNeoDBUuids = it.addedNeoDBUuids + entry.uuid
                    )
                }
            } catch (e: Exception) {
                AppLogger.error(TAG, "提交评分失败", e)
                _uiState.update {
                    it.copy(
                        isSubmittingRating = false,
                        ratingError = e.message ?: "提交失败"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun fetchTmdbInfo(entry: NeoDBEntry, year: Int? = null): NeoDBEntry {
        return try {
            val searchResult = if (entry.category == "movie") {
                tmdbApi.searchMovie(query = entry.displayTitle, year = year)
            } else {
                tmdbApi.searchTv(query = entry.displayTitle)
            }

            val firstResult = searchResult.results.firstOrNull()
            if (firstResult != null) {
                val tmdbId = firstResult.id
                val externalIds = if (entry.category == "movie") {
                    tmdbApi.getMovieExternalIds(tmdbId)
                } else {
                    tmdbApi.getTvExternalIds(tmdbId)
                }

                entry.copy(
                    tmdbRating = firstResult.voteAverage,
                    imdbId = externalIds.imdbId,
                    tmdbId = tmdbId
                )
            } else {
                entry
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "TMDB信息获取失败", e, mapOf("title" to entry.displayTitle))
            entry
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val isLoading: Boolean = false,
    val results: List<NeoDBEntry> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
    val addedNeoDBUuids: Set<String> = emptySet(),
    val traktWatchlistUuids: Set<String> = emptySet(),
    val traktWatchedUuids: Set<String> = emptySet(),
    val showRatingDialog: Boolean = false,
    val ratingTargetEntry: NeoDBEntry? = null,
    val ratingValue: Int = 5,
    val ratingComment: String = "",
    val shareToMastodon: Boolean = true,
    val isSubmittingRating: Boolean = false,
    val ratingError: String? = null,
    val ratingShelfType: String = "complete",
)

enum class SearchCategory(val label: String) {
    ALL("全部"),
    MOVIE("电影"),
    TV("剧集"),
    BOOK("书籍"),
    MUSIC("音乐"),
    GAME("游戏")
}
