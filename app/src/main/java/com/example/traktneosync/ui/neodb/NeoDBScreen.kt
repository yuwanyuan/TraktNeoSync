package com.example.traktneosync.ui.neodb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.traktneosync.data.neodb.NeoDBMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoDBScreen(
    viewModel: NeoDBViewModel = hiltViewModel(),
    onNavigateToSync: () -> Unit = {},
    onNavigateToDetail: (NeoDBMark) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialLoad()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部栏：同步按钮在左上方
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onNavigateToSync,
                enabled = uiState.isAuthenticated
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("同步")
            }

            if (uiState.isAuthenticated) {
                Text(
                    text = "${uiState.selectedShelf.label} (${uiState.marks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 书架分类 Tab
        TabRow(
            selectedTabIndex = NeoDBShelf.entries.indexOf(uiState.selectedShelf)
        ) {
            NeoDBShelf.entries.forEach { shelf ->
                Tab(
                    selected = uiState.selectedShelf == shelf,
                    onClick = { viewModel.selectShelf(shelf) },
                    text = { Text(shelf.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 内容区域
        when {
            !uiState.isAuthenticated -> {
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
                        text = "加载失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.error ?: "未知错误",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            }
            uiState.marks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${uiState.selectedShelf.label}列表为空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
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
            // 封面图
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

            // 信息
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
