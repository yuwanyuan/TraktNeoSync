package com.example.traktneosync.ui.shows

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
    viewModel: ShowsViewModel = hiltViewModel(),
    onNavigateToDetail: (ShowItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // 显示错误提示
    if (uiState.errorMessage != null) {
        val error = uiState.errorMessage
        LaunchedEffect(error) {
            snackbarHostState?.showSnackbar(error ?: "未知错误")
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Trakt 剧集",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 标签页切换
        TabRow(selectedTabIndex = uiState.selectedTab) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("已观看") }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text("待看") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.items.isEmpty() -> {
                EmptyState()
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items) { item ->
                        ShowCard(
                            item = item,
                            onClick = { onNavigateToDetail(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowCard(
    item: ShowItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
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
            // 封面图片
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(width = 60.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { savePosterImage(context, item.posterUrl) }
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                // 占位图
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "剧集",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 文字信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.year != null) {
                    Text(
                        text = "${item.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.plays > 0) {
                    Text(
                        text = "已观看 ${item.plays} 集",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 观看时间
            val timeText = formatWatchedAt(item.lastWatchedAt)
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun formatWatchedAt(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(isoString)
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
            .format(instant)
        val relative = when {
            duration.toDays() >= 365 -> "${duration.toDays() / 365}年前"
            duration.toDays() >= 30 -> "${duration.toDays() / 30}个月前"
            duration.toDays() >= 7 -> "${duration.toDays() / 7}周前"
            duration.toDays() >= 1 -> "${duration.toDays()}天前"
            duration.toHours() >= 1 -> "${duration.toHours()}小时前"
            duration.toMinutes() >= 1 -> "${duration.toMinutes()}分钟前"
            else -> "刚刚"
        }
        "$relative\n$dateStr"
    } catch (e: Exception) {
        ""
    }
}

private fun savePosterImage(context: Context, imageUrl: String) {
    try {
        val fileName = "TraktNeoSync_poster_${System.currentTimeMillis()}.jpg"
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("保存海报")
            .setDescription("正在下载海报...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无内容",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}