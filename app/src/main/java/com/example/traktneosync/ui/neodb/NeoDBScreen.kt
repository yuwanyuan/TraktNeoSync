package com.example.traktneosync.ui.neodb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.traktneosync.data.neodb.NeoDBEntry
import com.example.traktneosync.data.neodb.NeoDBMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoDBScreen(
    viewModel: NeoDBViewModel = hiltViewModel(),
    onNavigateToSync: () -> Unit = {},
    onNavigateToDetail: (NeoDBMark) -> Unit = {},
    onNavigateToEntry: (NeoDBEntry) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialLoad()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollableTabRow(
                selectedTabIndex = when (uiState.selectedTab) {
                    NeoDBTab.DISCOVER -> 0
                    is NeoDBTab.ShelfTab -> 1 + NeoDBShelf.entries.indexOf((uiState.selectedTab as NeoDBTab.ShelfTab).shelf)
                },
                modifier = Modifier.weight(1f),
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = uiState.selectedTab == NeoDBTab.DISCOVER,
                    onClick = { viewModel.selectTab(NeoDBTab.DISCOVER) },
                    text = { Text("发现") }
                )
                NeoDBShelf.entries.forEach { shelf ->
                    Tab(
                        selected = uiState.selectedTab == NeoDBTab.ShelfTab(shelf),
                        onClick = { viewModel.selectTab(NeoDBTab.ShelfTab(shelf)) },
                        text = { Text(shelf.label) }
                    )
                }
            }

            IconButton(onClick = onNavigateToSync, enabled = uiState.isAuthenticated) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "同步",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            !uiState.isAuthenticated && uiState.selectedTab != NeoDBTab.DISCOVER -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请先在设置页面登录 NeoDB",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error ?: "加载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            }
            uiState.selectedTab == NeoDBTab.DISCOVER -> {
                DiscoverContent(
                    movies = uiState.trendingMovies,
                    tv = uiState.trendingTV,
                    onEntryClick = onNavigateToEntry
                )
            }
            uiState.selectedTab is NeoDBTab.ShelfTab -> {
                val shelf = (uiState.selectedTab as NeoDBTab.ShelfTab).shelf
                if (uiState.marks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${shelf.label}列表为空",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.marks.withIndex().toList(),
                            key = { (index, mark) -> mark.uuid.ifBlank { "neodb_mark_$index" } }
                        ) { (_, mark) ->
                            NeoDBMarkCard(
                                mark = mark,
                                onClick = { onNavigateToDetail(mark) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverContent(
    movies: List<NeoDBEntry>,
    tv: List<NeoDBEntry>,
    onEntryClick: (NeoDBEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (movies.isNotEmpty()) {
            item {
                Text(
                    text = "热门电影",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(movies, key = { it.uuid }) { entry ->
                        TrendingCard(
                            entry = entry,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                }
            }
        }

        if (tv.isNotEmpty()) {
            item {
                Text(
                    text = "热门剧集",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tv, key = { it.uuid }) { entry ->
                        TrendingCard(
                            entry = entry,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                }
            }
        }

        if (movies.isEmpty() && tv.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无热门内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingCard(
    entry: NeoDBEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            if (entry.coverImageUrl != null) {
                AsyncImage(
                    model = entry.coverImageUrl,
                    contentDescription = entry.displayTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.category.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NeoDBMarkCard(
    mark: NeoDBMark,
    onClick: () -> Unit
) {
    val entry = mark.item
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
                        .size(width = 50.dp, height = 70.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 70.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.category.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mark.ratingGrade != null) {
                    Text(
                        text = "★ ${mark.ratingGrade}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!mark.commentText.isNullOrBlank()) {
                    Text(
                        text = mark.commentText.take(60),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
