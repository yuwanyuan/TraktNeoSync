package com.example.traktneosync.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBMark
import com.example.traktneosync.data.neodb.NeoDBMarkRequest
import com.example.traktneosync.data.neodb.NeoDBPaginatedPosts
import com.example.traktneosync.data.neodb.NeoDBPost
import com.example.traktneosync.data.tmdb.TmdbApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val neoDBApi: NeoDBApiService,
    private val tmdbApi: TmdbApiService,
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
        private val BR_REGEX = Regex("<br\\s*/?>")
        private val P_OPEN_REGEX = Regex("<p>")
        private val P_CLOSE_REGEX = Regex("</p>")
        private val TAG_REGEX = Regex("<[^>]+>")
    }

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private var currentPostPage = 1
    private var totalPostPages = 1
    private var itemUuid: String? = null

    fun loadNeoDBReviews(title: String, year: Int?, type: String, imdbId: String?, tmdbId: Long?) {
        if (_uiState.value.isLoadingReviews) return
        _uiState.update {
            it.copy(
                isLoadingReviews = true,
                reviews = emptyList(),
                reviewError = null,
                overview = null,
                backdropUrls = emptyList(),
                posterUrls = emptyList(),
                imdbRating = null,
                imdbVoteCount = null,
                neoDBRating = null,
                neoDBRatingCount = 0,
            )
        }

        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoadingReviews = false, reviewError = "NeoDB 未登录") }
                return@launch
                }

                var searchTitle = title
                var searchYear = year
                val isMovie = type == "电影" || type == "movie"
                val category = if (isMovie) "movie" else "tv"

                // 如果有 tmdbId，先尝试获取中文译名和准确年份
                if (tmdbId != null && tmdbId > 0) {
                    try {
                        val altTitles = if (isMovie) {
                            tmdbApi.getMovieAlternativeTitles(tmdbId)
                        } else {
                            tmdbApi.getTvAlternativeTitles(tmdbId)
                        }
                        // 优先找中国大陆/台湾/香港中文标题
                        val cnTitle = altTitles.titles?.firstOrNull { it.iso31661 == "CN" }?.title
                            ?: altTitles.results?.firstOrNull { it.iso31661 == "CN" }?.title
                            ?: altTitles.titles?.firstOrNull { it.iso31661 == "TW" }?.title
                            ?: altTitles.results?.firstOrNull { it.iso31661 == "TW" }?.title
                            ?: altTitles.titles?.firstOrNull { it.iso31661 == "HK" }?.title
                            ?: altTitles.results?.firstOrNull { it.iso31661 == "HK" }?.title

                        if (!cnTitle.isNullOrBlank()) {
                            searchTitle = cnTitle
                            Log.d(TAG, "Using TMDB CN title for NeoDB search: $searchTitle")
                        }

                        // 同时获取 TMDB 的准确年份
                        if (isMovie) {
                            val detail = tmdbApi.getMovieDetail(tmdbId)
                            if (detail.releaseDate.isNotBlank()) {
                                searchYear = detail.releaseDate.take(4).toIntOrNull()
                            }
                        } else {
                            val detail = tmdbApi.getTvDetail(tmdbId)
                            if (detail.firstAirDate.isNotBlank()) {
                                searchYear = detail.firstAirDate.take(4).toIntOrNull()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get TMDB alternative title: ${e.message}")
                    }
                }

                // 1. 搜索 NeoDB 条目
                val query = if (imdbId != null && imdbId.isNotEmpty()) {
                    imdbId
                } else {
                    "$searchTitle ${searchYear ?: ""}".trim()
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
                        entry.displayTitle.equals(searchTitle, ignoreCase = true) ||
                                entry.displayTitle.contains(searchTitle, ignoreCase = true)
                    } ?: searchResult.data.first()
                }

                itemUuid = bestMatch.uuid

                // 保存 NeoDB 简介和评分
                _uiState.update {
                    it.copy(
                        overview = bestMatch.brief.takeIf { b -> b.isNotBlank() },
                        neoDBRating = bestMatch.rating,
                        neoDBRatingCount = bestMatch.ratingCount
                    )
                }

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
                val (tmdbOverview, voteAverage, voteCount) = if (isMovie) {
                    val d = tmdbApi.getMovieDetail(tmdbId)
                    Triple(d.overview, d.voteAverage, d.voteCount)
                } else {
                    val d = tmdbApi.getTvDetail(tmdbId)
                    Triple(d.overview, d.voteAverage, d.voteCount)
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
                        imdbRating = voteAverage,
                        imdbVoteCount = voteCount,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TMDB details: ${e.message}", e)
                _uiState.update { it.copy(isLoadingDetails = false, detailsError = e.message) }
            }
        }
    }

    fun selectImage(url: String) {
        _uiState.update { it.copy(selectedImageUrl = url) }
    }

    fun dismissImageViewer() {
        _uiState.update { it.copy(selectedImageUrl = null) }
    }

    // ========== 评分对话框 ==========

    fun openRatingDialog() {
        _uiState.update { it.copy(showRatingDialog = true, ratingSubmitError = null) }
        loadCurrentMark()
    }

    fun dismissRatingDialog() {
        _uiState.update { it.copy(showRatingDialog = false, ratingSubmitError = null) }
    }

    private fun loadCurrentMark() {
        val uuid = itemUuid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMarkStatus = true) }
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoadingMarkStatus = false) }
                    return@launch
                }
                val mark = neoDBApi.getItemMark("Bearer $token", uuid)
                _uiState.update { it.copy(currentMark = mark, isLoadingMarkStatus = false) }
            } catch (e: Exception) {
                Log.d(TAG, "No existing mark found: ${e.message}")
                _uiState.update { it.copy(currentMark = null, isLoadingMarkStatus = false) }
            }
        }
    }

    fun submitRating(ratingGrade: Int, comment: String, shareToMastodon: Boolean) {
        val uuid = itemUuid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingRating = true, ratingSubmitError = null) }
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token.isNullOrEmpty()) {
                    _uiState.update { it.copy(isSubmittingRating = false, ratingSubmitError = "NeoDB 未登录") }
                    return@launch
                }

                val currentMark = _uiState.value.currentMark
                val shelfType = currentMark?.shelfType?.takeIf { it.isNotBlank() } ?: "complete"
                val visibility = if (shareToMastodon) 0 else 2 // 0=公开, 2=私密

                val request = NeoDBMarkRequest(
                    shelfType = shelfType,
                    visibility = visibility,
                    ratingGrade = ratingGrade,
                    commentText = comment.takeIf { it.isNotBlank() }
                )

                neoDBApi.addOrUpdateMark("Bearer $token", uuid, request)

                _uiState.update { it.copy(isSubmittingRating = false, showRatingDialog = false) }
                // 刷新评论和评分数据
                refreshNeoDBData()
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting rating: ${e.message}", e)
                _uiState.update { it.copy(isSubmittingRating = false, ratingSubmitError = e.message) }
            }
        }
    }

    private fun refreshNeoDBData() {
        val uuid = itemUuid ?: return
        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first() ?: return@launch
                // 重新加载评论第一页
                loadPostsPage(token, uuid, 1, reset = true)
                // 评分数据会在用户下次进入详情页时重新加载
                // 此处仅刷新评论即可
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing NeoDB data: ${e.message}", e)
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
            val rawContent = post.extNeoDB?.relatedWith
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
                content = sanitizeContent(rawContent),
                rating = rating,
                date = post.createdAt ?: "",
                relativeDate = formatPostDate(post.createdAt),
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

    private fun sanitizeContent(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(BR_REGEX, "\n")
            .replace(P_OPEN_REGEX, "")
            .replace(P_CLOSE_REGEX, "\n\n")
            .replace(TAG_REGEX, "")
            .trim()
    }

    private fun formatPostDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            val now = Instant.now()
            val duration = Duration.between(instant, now)
            when {
                duration.toDays() >= 365 -> "${duration.toDays() / 365}年前"
                duration.toDays() >= 30 -> "${duration.toDays() / 30}个月前"
                duration.toDays() >= 7 -> "${duration.toDays() / 7}周前"
                duration.toDays() >= 1 -> "${duration.toDays()}天前"
                duration.toHours() >= 1 -> "${duration.toHours()}小时前"
                duration.toMinutes() >= 1 -> "${duration.toMinutes()}分钟前"
                else -> "刚刚"
            }
        } catch (e: Exception) {
            isoString
        }
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
    // 评分
    val imdbRating: Float? = null,
    val imdbVoteCount: Int? = null,
    val neoDBRating: Float? = null,
    val neoDBRatingCount: Int = 0,
    // 图片查看器
    val selectedImageUrl: String? = null,
    // 评分对话框
    val showRatingDialog: Boolean = false,
    val isLoadingMarkStatus: Boolean = false,
    val currentMark: NeoDBMark? = null,
    val isSubmittingRating: Boolean = false,
    val ratingSubmitError: String? = null,
)

data class ReviewItem(
    val username: String,
    val avatarUrl: String?,
    val content: String,
    val rating: Int?,
    val date: String,
    val relativeDate: String = "",
)
