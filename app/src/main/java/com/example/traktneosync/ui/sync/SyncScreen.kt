package com.example.traktneosync.ui.sync

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.traktneosync.data.SyncRepository

@Composable
fun SyncScreen(
    viewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(uiState.syncError) {
        uiState.syncError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部同步按钮和状态
        SyncHeader(
            isLoading = uiState.isLoading,
            hasMore = uiState.hasMoreItems,
            onSync = { viewModel.syncNextBatch() },
            onSyncAll = { viewModel.syncAll() },
            isAuthenticated = uiState.isAuthenticated
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 筛选器
        FilterChips(
            selectedFilter = uiState.filter,
            onFilterChange = { viewModel.setFilter(it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 统计信息
        if (uiState.stats.isNotEmpty()) {
            StatsCard(stats = uiState.stats)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 待同步列表
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uiState.filteredItems,
                key = { it.traktItem.ids.trakt }
            ) { item ->
                SyncItemCard(
                    item = item,
                    onAddToNeoDB = { viewModel.addToNeoDB(item) }
                )
            }
        }
        
        // 空状态
        if (!uiState.isLoading && uiState.filteredItems.isEmpty()) {
            EmptyState(isAuthenticated = uiState.isAuthenticated)
        }
    }
}

@Composable
private fun SyncHeader(
    isLoading: Boolean,
    hasMore: Boolean,
    onSync: () -> Unit,
    onSyncAll: () -> Unit,
    isAuthenticated: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Trakt ↔ NeoDB 同步",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isAuthenticated) {
                Text(
                    text = "请先登录 Trakt 和 NeoDB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "同步 Trakt 观看记录到 NeoDB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSync,
                        enabled = isAuthenticated
                    ) {
                        Icon(Icons.Default.Sync, null)
                        Spacer(Modifier.width(4.dp))
                        Text("检查同步")
                    }
                    
                    if (hasMore) {
                        Button(
                            onClick = onSyncAll,
                            enabled = isAuthenticated
                        ) {
                            Text("全部添加")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selectedFilter: SyncFilter,
    onFilterChange: (SyncFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SyncFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun StatsCard(stats: Map<String, Int>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stats.forEach { (label, count) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncItemCard(
    item: SyncListItem,
    onAddToNeoDB: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSynced) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.type} ${if (item.year != null) "(${item.year})" else ""} ${item.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.isSynced) {
                    Text(
                        text = "✓ 已在 NeoDB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (!item.isSynced) {
                FilledIconButton(
                    onClick = onAddToNeoDB
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加到 NeoDB")
                }
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已同步",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isAuthenticated: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isAuthenticated) {
                "所有内容已同步，没有待添加项"
            } else {
                "请先在设置页面登录 Trakt 和 NeoDB"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========== ViewModel 数据类 ==========

enum class SyncFilter(val label: String) {
    ALL("全部"),
    NOT_SYNCED("待添加"),
    SYNCED("已添加"),
    MOVIES("电影"),
    SHOWS("剧集")
}

data class SyncListItem(
    val title: String,
    val year: Int?,
    val type: String, // "电影" or "剧集"
    val status: String, // "已观看" or "待看"
    val isSynced: Boolean,
    val traktItem: SyncRepository.TraktItem
)

data class SyncUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val items: List<SyncListItem> = emptyList(),
    val filter: SyncFilter = SyncFilter.NOT_SYNCED,
    val hasMoreItems: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncError: String? = null,
    val stats: Map<String, Int> = emptyMap()
) {
    val filteredItems: List<SyncListItem>
        get() = when (filter) {
            SyncFilter.ALL -> items
            SyncFilter.NOT_SYNCED -> items.filter { !it.isSynced }
            SyncFilter.SYNCED -> items.filter { it.isSynced }
            SyncFilter.MOVIES -> items.filter { it.type == "电影" }
            SyncFilter.SHOWS -> items.filter { it.type == "剧集" }
        }
}

data class SyncProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String
)
