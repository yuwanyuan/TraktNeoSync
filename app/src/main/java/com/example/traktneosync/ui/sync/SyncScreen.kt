package com.example.traktneosync.ui.sync

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
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
        SyncHeader(
            isLoading = uiState.isLoading,
            syncProgress = uiState.syncProgress,
            onCheck = { viewModel.checkSync() },
            onForceCheck = { viewModel.checkSync(force = true) },
            onSyncSelected = { viewModel.syncSelected() },
            isAuthenticated = uiState.isAuthenticated,
            selectedCount = uiState.selectedCount,
            hasItems = uiState.items.isNotEmpty()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.items.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChips(
                    selectedFilter = uiState.filter,
                    onFilterChange = { viewModel.setFilter(it) },
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = { viewModel.toggleSelectAll() }) {
                    Text(
                        if (uiState.allSelected) "取消全选" else "全选",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.stats.isNotEmpty()) {
            StatsCard(stats = uiState.stats)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (uiState.filteredItems.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.filteredItems,
                    key = { it.uuid }
                ) { item ->
                    SyncItemCard(
                        item = item,
                        onToggleSelect = { viewModel.toggleSelect(item.uuid) }
                    )
                }
            }
        } else if (!uiState.isLoading) {
            EmptyState(
                isAuthenticated = uiState.isAuthenticated,
                hasChecked = uiState.stats.isNotEmpty()
            )
        }
    }
}

@Composable
private fun SyncHeader(
    isLoading: Boolean,
    syncProgress: SyncProgress?,
    onCheck: () -> Unit,
    onForceCheck: () -> Unit,
    onSyncSelected: () -> Unit,
    isAuthenticated: Boolean,
    selectedCount: Int,
    hasItems: Boolean
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
                text = "Trakt → NeoDB 同步",
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
                    text = "检测 Trakt 中 NeoDB 没有的条目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在检测差异...",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (syncProgress != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "正在同步: ${syncProgress.currentTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = syncProgress.current.toFloat() / syncProgress.total,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${syncProgress.current} / ${syncProgress.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasItems) {
                        OutlinedButton(
                            onClick = onForceCheck,
                            enabled = isAuthenticated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重新检测")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCheck,
                            enabled = isAuthenticated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("检测差异")
                        }
                    }

                    if (hasItems) {
                        Button(
                            onClick = onSyncSelected,
                            enabled = isAuthenticated && selectedCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("同步选中 ($selectedCount)")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    selectedFilter: SyncFilter,
    onFilterChange: (SyncFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SyncFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
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
                .padding(12.dp),
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
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isSynced && !item.isSyncing) { onToggleSelect() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isSynced -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                item.isSyncing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                item.isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = item.isSelected || item.isSynced,
                onCheckedChange = if (!item.isSynced && !item.isSyncing) { { onToggleSelect() } } else null,
                modifier = Modifier.size(24.dp),
                enabled = !item.isSynced && !item.isSyncing
            )

            val posterUrl = item.posterUrl
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.title,
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
                        text = item.type.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                    text = "${item.type} ${if (item.year != null) "(${item.year})" else ""} · ${item.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    item.isSynced -> {
                        Text(
                            text = "✓ 已同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item.isSyncing -> {
                        Text(
                            text = "同步中...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    item.syncError != null -> {
                        Text(
                            text = "✗ ${item.syncError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isAuthenticated: Boolean, hasChecked: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                !isAuthenticated -> "请先在设置页面登录 Trakt 和 NeoDB"
                hasChecked -> "所有内容已同步，没有待添加项"
                else -> "点击「检测差异」开始检查"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
