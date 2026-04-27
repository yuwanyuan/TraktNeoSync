package com.example.traktneosync.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBEntry
import com.example.traktneosync.data.neodb.NeoDBMarkRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val neoDBApi: NeoDBApiService,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun onCategoryChange(category: SearchCategory) {
        _uiState.value = _uiState.value.copy(category = category)
        if (_uiState.value.query.isNotEmpty()) {
            search()
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                hasSearched = true
            )

            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未登录 NeoDB"
                    )
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

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    results = result.data,
                    error = null
                )

            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "搜索失败"
                )
            }
        }
    }

    fun addToShelf(entry: NeoDBEntry, shelfType: String) {
        viewModelScope.launch {
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.value = _uiState.value.copy(error = "未登录 NeoDB")
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

                // 标记为已添加
                _uiState.value = _uiState.value.copy(
                    addedUuids = _uiState.value.addedUuids + entry.uuid
                )

            } catch (e: Exception) {
                Log.e(TAG, "Add to shelf error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "添加失败: ${e.message}"
                )
            }
        }
    }

    // ========== 评分对话框 ==========

    fun openRatingDialog(entry: NeoDBEntry) {
        _uiState.value = _uiState.value.copy(
            showRatingDialog = true,
            ratingTargetEntry = entry,
            ratingValue = 5,
            ratingComment = "",
            shareToMastodon = true,
            ratingError = null
        )
    }

    fun dismissRatingDialog() {
        _uiState.value = _uiState.value.copy(
            showRatingDialog = false,
            ratingTargetEntry = null,
            ratingError = null
        )
    }

    fun setRatingValue(value: Int) {
        _uiState.value = _uiState.value.copy(ratingValue = value)
    }

    fun setRatingComment(comment: String) {
        _uiState.value = _uiState.value.copy(ratingComment = comment)
    }

    fun setShareToMastodon(share: Boolean) {
        _uiState.value = _uiState.value.copy(shareToMastodon = share)
    }

    fun submitRating() {
        val entry = _uiState.value.ratingTargetEntry ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingRating = true, ratingError = null)
            try {
                val token = authRepository.neodbAccessToken.first()
                if (token == null) {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingRating = false,
                        ratingError = "未登录 NeoDB"
                    )
                    return@launch
                }

                val visibility = if (_uiState.value.shareToMastodon) 0 else 2
                val request = NeoDBMarkRequest(
                    shelfType = "complete",
                    visibility = visibility,
                    ratingGrade = _uiState.value.ratingValue,
                    commentText = _uiState.value.ratingComment.takeIf { it.isNotBlank() }
                )

                neoDBApi.addOrUpdateMark(
                    token = "Bearer $token",
                    uuid = entry.uuid,
                    request = request
                )

                _uiState.value = _uiState.value.copy(
                    isSubmittingRating = false,
                    showRatingDialog = false,
                    ratingTargetEntry = null,
                    addedUuids = _uiState.value.addedUuids + entry.uuid
                )
            } catch (e: Exception) {
                Log.e(TAG, "Submit rating error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isSubmittingRating = false,
                    ratingError = e.message ?: "提交失败"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SearchUiState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val isLoading: Boolean = false,
    val results: List<NeoDBEntry> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
    val shelfType: String = "wishlist", // 默认添加到想看
    val addedUuids: Set<String> = emptySet(),
    // 评分对话框
    val showRatingDialog: Boolean = false,
    val ratingTargetEntry: NeoDBEntry? = null,
    val ratingValue: Int = 5,
    val ratingComment: String = "",
    val shareToMastodon: Boolean = true,
    val isSubmittingRating: Boolean = false,
    val ratingError: String? = null,
)

enum class SearchCategory(val label: String) {
    ALL("全部"),
    MOVIE("电影"),
    TV("剧集"),
    BOOK("书籍"),
    MUSIC("音乐"),
    GAME("游戏")
}
