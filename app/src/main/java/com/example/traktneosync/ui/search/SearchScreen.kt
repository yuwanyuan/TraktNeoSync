package com.example.traktneosync.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.traktneosync.data.neodb.NeoDBEntry
import com.example.traktneosync.data.neodb.extractImdbId
import com.example.traktneosync.data.neodb.extractTmdbId

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToDetail: (NeoDBEntry) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // 评分对话框
    if (uiState.showRatingDialog) {
        val entry = uiState.ratingTargetEntry
        if (entry != null) {
            RatingDialog(
                entryTitle = entry.displayTitle,
                ratingValue = uiState.ratingValue,
                ratingComment = uiState.ratingComment,
                shareToMastodon = uiState.shareToMastodon,
                isSubmitting = uiState.isSubmittingRating,
                error = uiState.ratingError,
                shelfType = uiState.ratingShelfType,
                onRatingChange = { viewModel.setRatingValue(it) },
                onCommentChange = { viewModel.setRatingComment(it) },
                onShareToMastodonChange = { viewModel.setShareToMastodon(it) },
                onShelfTypeChange = { viewModel.setRatingShelfType(it) },
                onDismiss = { viewModel.dismissRatingDialog() },
                onSubmit = { viewModel.submitRating() }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 搜索框
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索电影或剧集") },
            placeholder = { Text("输入标题...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.search()
                }
            ),
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Default.Search, null)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 分类筛选
        CategoryFilter(
            selectedCategory = uiState.category,
            onCategoryChange = { viewModel.onCategoryChange(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 搜索结果
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                ErrorState(
                    error = uiState.error ?: "未知错误",
                    onRetry = { viewModel.search() }
                )
            }
            uiState.results.isNotEmpty() -> {
                SearchResultsList(
                    results = uiState.results,
                    addedNeoDBUuids = uiState.addedNeoDBUuids,
                    traktWatchlistUuids = uiState.traktWatchlistUuids,
                    traktWatchedUuids = uiState.traktWatchedUuids,
                    onAddToShelf = { entry, shelfType ->
                        viewModel.addToShelf(entry, shelfType)
                    },
                    onRate = { entry ->
                        viewModel.openRatingDialog(entry)
                    },
                    onAddTraktWatchlist = { entry ->
                        viewModel.addToTraktWatchlist(entry)
                    },
                    onAddTraktHistory = { entry ->
                        viewModel.addToTraktHistory(entry)
                    },
                    onNavigateToDetail = onNavigateToDetail
                )
            }
            uiState.hasSearched -> {
                EmptySearchResults()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilter(
    selectedCategory: SearchCategory,
    onCategoryChange: (SearchCategory) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategoryChange(category) },
                label = { Text(category.label) }
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<NeoDBEntry>,
    addedNeoDBUuids: Set<String>,
    traktWatchlistUuids: Set<String>,
    traktWatchedUuids: Set<String>,
    onAddToShelf: (NeoDBEntry, String) -> Unit,
    onRate: (NeoDBEntry) -> Unit,
    onAddTraktWatchlist: (NeoDBEntry) -> Unit,
    onAddTraktHistory: (NeoDBEntry) -> Unit,
    onNavigateToDetail: (NeoDBEntry) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.uuid }) { entry ->
            SearchResultCard(
                entry = entry,
                isNeoDBAdded = entry.uuid in addedNeoDBUuids,
                isTraktWatchlist = entry.uuid in traktWatchlistUuids,
                isTraktWatched = entry.uuid in traktWatchedUuids,
                onAddToShelf = { shelfType -> onAddToShelf(entry, shelfType) },
                onRate = { onRate(entry) },
                onAddTraktWatchlist = { onAddTraktWatchlist(entry) },
                onAddTraktHistory = { onAddTraktHistory(entry) },
                onClick = { onNavigateToDetail(entry) }
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    entry: NeoDBEntry,
    isNeoDBAdded: Boolean,
    isTraktWatchlist: Boolean,
    isTraktWatched: Boolean,
    onAddToShelf: (String) -> Unit,
    onRate: () -> Unit,
    onAddTraktWatchlist: () -> Unit,
    onAddTraktHistory: () -> Unit,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isMovieOrTv = entry.category == "movie" || entry.category == "tv"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (entry.coverImageUrl != null) {
                AsyncImage(
                    model = entry.coverImageUrl,
                    contentDescription = entry.displayTitle,
                    modifier = Modifier
                        .size(width = 60.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.category.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val ratingText = when {
                    entry.tmdbRating != null && entry.tmdbRating > 0 -> "TMDB ★ ${String.format("%.1f", entry.tmdbRating)}"
                    entry.rating != null && entry.rating > 0 -> "NeoDB ★ ${entry.rating}"
                    else -> entry.category
                }
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.imdbId != null || entry.tmdbId != null) {
                    val idText = buildString {
                        if (entry.imdbId != null) append("IMDb: ${entry.imdbId}")
                        if (entry.imdbId != null && entry.tmdbId != null) append(" | ")
                        if (entry.tmdbId != null) append("TMDB: ${entry.tmdbId}")
                    }
                    Text(
                        text = idText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isNeoDBAdded) {
                        AssistChip(
                            onClick = { },
                            label = { Text("NeoDB ✓", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    if (isTraktWatched) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Trakt 已看", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    } else if (isTraktWatchlist) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Trakt 想看", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "操作"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isNeoDBAdded) "NeoDB ✓ 已添加" else "添加到想看 (NeoDB)") },
                        onClick = {
                            menuExpanded = false
                            if (!isNeoDBAdded) onAddToShelf("wishlist")
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        enabled = !isNeoDBAdded
                    )
                    DropdownMenuItem(
                        text = { Text(if (isNeoDBAdded) "NeoDB ✓ 已添加" else "添加到在看 (NeoDB)") },
                        onClick = {
                            menuExpanded = false
                            if (!isNeoDBAdded) onAddToShelf("progress")
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        enabled = !isNeoDBAdded
                    )
                    DropdownMenuItem(
                        text = { Text("评分 (NeoDB)") },
                        onClick = {
                            menuExpanded = false
                            onRate()
                        },
                        leadingIcon = { Icon(Icons.Default.Star, null) }
                    )

                    if (isMovieOrTv) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text(if (isTraktWatchlist) "Trakt ✓ 已在想看" else "标记想看 (Trakt)") },
                            onClick = {
                                menuExpanded = false
                                if (!isTraktWatchlist) onAddTraktWatchlist()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            enabled = !isTraktWatchlist && !isTraktWatched
                        )
                        DropdownMenuItem(
                            text = { Text(if (isTraktWatched) "Trakt ✓ 已看" else "标记已看 (Trakt)") },
                            onClick = {
                                menuExpanded = false
                                if (!isTraktWatched) onAddTraktHistory()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            enabled = !isTraktWatched
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "搜索失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未找到相关条目",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingDialog(
    entryTitle: String,
    ratingValue: Int,
    ratingComment: String,
    shareToMastodon: Boolean,
    isSubmitting: Boolean,
    error: String?,
    shelfType: String,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onShareToMastodonChange: (Boolean) -> Unit,
    onShelfTypeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "评分",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entryTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("想看" to "wishlist", "在看" to "progress", "已看" to "complete").forEach { (label, value) ->
                        FilterChip(
                            selected = shelfType == value,
                            onClick = { onShelfTypeChange(value) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column {
                    Text(
                        text = "$ratingValue 分",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = ratingValue.toFloat(),
                        onValueChange = { onRatingChange(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedTextField(
                    value = ratingComment,
                    onValueChange = onCommentChange,
                    label = { Text("评论（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "同步到长毛象",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "发布到 Fediverse 时间线",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = shareToMastodon,
                        onCheckedChange = onShareToMastodonChange
                    )
                }

                val ratingErr = error
                if (ratingErr != null) {
                    Text(
                        text = ratingErr,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSubmit,
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("提交")
                        }
                    }
                }
            }
        }
    }
}
