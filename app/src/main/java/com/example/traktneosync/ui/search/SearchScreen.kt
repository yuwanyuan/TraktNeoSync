package com.example.traktneosync.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.traktneosync.data.neodb.NeoDBEntry

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

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
                    error = uiState.error!!,
                    onRetry = { viewModel.search() }
                )
            }
            uiState.results.isNotEmpty() -> {
                SearchResultsList(
                    results = uiState.results,
                    addedUuids = uiState.addedUuids,
                    onAdd = { entry ->
                        viewModel.addToShelf(entry, uiState.shelfType)
                    }
                )
            }
            uiState.hasSearched -> {
                EmptySearchResults()
            }
        }
    }
}

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
    addedUuids: Set<String>,
    onAdd: (NeoDBEntry) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { entry ->
            val isAdded = entry.uuid in addedUuids
            SearchResultCard(
                entry = entry,
                isAdded = isAdded,
                onAdd = { onAdd(entry) }
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    entry: NeoDBEntry,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.category} ${if (entry.rating != null) "★ ${entry.rating}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.brief.isNotEmpty()) {
                    Text(
                        text = entry.brief.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAdded) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "已添加",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledIconButton(
                    onClick = onAdd
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加到书架")
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
