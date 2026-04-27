package com.example.traktneosync.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBPaginatedPosts
import com.example.traktneosync.data.neodb.NeoDBPost
import com.example.traktneosync.data.tmdb.TmdbApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val neoDBApi: NeoDBApiService,
    private val tmdbApi: TmdbApiService,
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private var currentPostPage = 1
    private var totalPostPages = 1
    private var itemUuid: String? = null

    fun loadNeoDBReviews(title: String, year: Int?, type: String, imdbId: String?) {
        if (_uiState.value.isLoadingReviews) return
        _uiState.update {
            it.copy(
                isLoadingReviews = true,
                reviews = emptyList(),
                reviewError = null,
                overview = null,
                backdropUrls = emptyList(),
                posterUrls = emptyList(),
            )
        }

        viewModelScope.launch {
            try {
            val token = authRepository.neodbAccessToken.first()
            if (token.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoadingReviews = false, reviewError = "NeoDB 未登录") }
                    return@launch
                }

                // 1. 搜索 NeoDB 条目
                val category = if (type == "电影") "movie" else "tv"
                val query = if (imdbId != null && imdbId.isNotEmpty()) {
                    imdbId
                } else {
                    "$title ${year ?: ""}".trim()
                }

                val searchResult = neoDBApi.search("Bearer $token", query, category, 1)
                if (searchResult.data.isEmpty()) {
                    _uiState.update { it.copy(isLoadingReviews = false, reviewError = "未在 NeoDB 找到该条目") }
                    return@launch
                }

                // 2. 匹配最准确的结果
                val bestMatch = if (imdbId != null && imdbId.isNotEmpty()) {
                    searchResult.data.firstOrNull { entry ->
                        entry.externalResources.any { it.url.contains(imdbId, ignoreCase = true) }
                    } ?: searchResult.data.first()
                } else {
                    searchResult.data.firstOrNull { entry ->
                        entry.displayTitle.equals(title, ignoreCase = true) ||
                                entry.displayTitle.contains(title, ignoreCase = true)
                    } ?: searchResult.data.first()
                }

                itemUuid = bestMatch.uuid

                // 保存 NeoDB 简介作为备选
                _uiState.update { it.copy(overview = bestMatch.brief.takeIf { b -> b.isNotBlank() }) }

                // 3. 加载第一页评论
                loadPostsPage(token, bestMatch.uuid, 1, reset = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading NeoDB reviews: ${e.message}", e)
                _uiState.update { it.copy(isLoadingReviews = false, reviewError = e.message) }
            }
        }
    }

    fun loadTmdbDetails(tmdbId: Long, type: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true, detailsError = null) }
            try {
                val isMovie = type == "电影" || type == "movie"
                val tmdbOverview = if (isMovie) {
                    tmdbApi.getMovieDetail(tmdbId).overview
                } else {
                    tmdbApi.getTvDetail(tmdbId).overview
                }

                val images = if (isMovie) {
                    tmdbApi.getMovieImages(tmdbId)
                } else {
                    tmdbApi.getTvImages(tmdbId)
                }

                val baseUrl = "https://image.tmdb.org/t/p/w500"
                val backdrops = images.backdrops
                    ?.filter { !it.filePath.isNullOrBlank() }
                    ?.sortedByDescending { it.voteAverage * it.voteCount }
                    ?.take(6)
                    ?.map { "$baseUrl${it.filePath}" }
                    ?: emptyList()

                val posters = images.posters
                    ?.filter { !it.filePath.isNullOrBlank() }
                    ?.sortedByDescending { it.voteAverage * it.voteCount }
                    ?.take(6)
                    ?.map { "$baseUrl${it.filePath}" }
                    ?: emptyList()

                _uiState.update { state ->
                    state.copy(
                        overview = tmdbOverview?.takeIf { it.isNotBlank() } ?: state.overview,
                        backdropUrls = backdrops,
                        posterUrls = posters,
                        isLoadingDetails = false,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TMDB details: ${e.message}", e)
                _uiState.update { it.copy(isLoadingDetails = false, detailsError = e.message) }
            }
        }
    }

    fun loadMoreReviews() {
        val uuid = itemUuid ?: return
        if (currentPostPage >= totalPostPages || _uiState.value.isLoadingMore) return

        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first() ?: return@launch
                _uiState.update { it.copy(isLoadingMore = true) }
                loadPostsPage(token, uuid, currentPostPage + 1, reset = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more reviews: ${e.message}", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun loadPostsPage(token: String, uuid: String, page: Int, reset: Boolean) {
        val postsResult = neoDBApi.getItemPosts("Bearer $token", uuid, "comment", page)
        val newReviews = postsResult.data.mapNotNull { post ->
            val account = post.account ?: return@mapNotNull null
            val username = account.displayName?.takeIf { it.isNotBlank() }
                ?: account.username?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val content = post.extNeoDB?.relatedWith
                ?.find { it.type == "Comment" || it.type == "comment" }
                ?.content
                ?: post.content
                ?: ""
            val rating = post.extNeoDB?.relatedWith
                ?.find { it.type == "Rating" || it.type == "rating" }
                ?.value
            ReviewItem(
                username = username,
                avatarUrl = account.avatar?.takeIf { it.isNotBlank() },
                content = content,
                rating = rating,
                date = post.createdAt ?: "",
            )
        }

        _uiState.update { state ->
            val combined = if (reset) newReviews else (state.reviews + newReviews).distinctBy { it.username + it.content + it.date }
            state.copy(
                reviews = combined,
                isLoadingReviews = false,
                isLoadingMore = false,
                hasMoreReviews = page < postsResult.pages,
            )
        }
        currentPostPage = page
        totalPostPages = postsResult.pages
    }
}

data class DetailUiState(
    val reviews: List<ReviewItem> = emptyList(),
    val isLoadingReviews: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreReviews: Boolean = false,
    val reviewError: String? = null,
    // 详情
    val overview: String? = null,
    val backdropUrls: List<String> = emptyList(),
    val posterUrls: List<String> = emptyList(),
    val isLoadingDetails: Boolean = false,
    val detailsError: String? = null,
)

data class ReviewItem(
    val username: String,
    val avatarUrl: String?,
    val content: String,
    val rating: Int?,
    val date: String,
)
