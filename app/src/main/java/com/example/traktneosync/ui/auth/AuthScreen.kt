package com.example.traktneosync.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Trakt 认证卡片
            item {
                AuthCard(
                    title = "Trakt",
                    icon = Icons.Default.PlayArrow,
                    isConnected = uiState.traktConnected,
                    username = uiState.traktUsername,
                    isLoading = false,
                    error = null,
                    onConnect = { viewModel.connectTrakt() },
                    onDisconnect = { viewModel.disconnectTrakt() },
                    onClearError = {}
                )
            }

            // NeoDB 认证卡片
            item {
                AuthCard(
                    title = "NeoDB",
                    icon = Icons.Default.AccountCircle,
                    isConnected = uiState.neodbConnected,
                    username = uiState.neodbUsername,
                    isLoading = uiState.neodbLoading,
                    error = uiState.neodbError,
                    onConnect = { viewModel.connectNeoDB() },
                    onDisconnect = { viewModel.disconnectNeoDB() },
                    onClearError = { viewModel.clearNeoDBError() }
                )
            }

            // TMDB API Key 配置
            item {
                TmdbKeyCard(
                    currentKey = uiState.tmdbApiKey,
                    keyTesting = uiState.tmdbKeyTesting,
                    keyValid = uiState.tmdbKeyValid,
                    onSave = { viewModel.saveTmdbApiKey(it) },
                    onTest = { viewModel.testTmdbApiKey(it) }
                )
            }

            // 使用说明
            item {
                TipCard()
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TmdbKeyCard(
    currentKey: String,
    keyTesting: Boolean,
    keyValid: Boolean?,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var isEditing by remember { mutableStateOf(currentKey.isEmpty()) }

    val statusColor = when {
        keyValid == true -> MaterialTheme.colorScheme.primary
        keyValid == false -> MaterialTheme.colorScheme.error
        currentKey.isNotEmpty() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val indicatorColor = when {
        keyValid == true -> MaterialTheme.colorScheme.primary
        keyValid == false -> MaterialTheme.colorScheme.error
        currentKey.isNotEmpty() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧状态指示条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 头部：图标 + 标题 + 状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "TMDB",
                        modifier = Modifier.size(36.dp),
                        tint = statusColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TMDB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val statusText = when {
                            keyTesting -> "检测中..."
                            keyValid == true -> "API Key 有效"
                            keyValid == false -> "API Key 无效"
                            currentKey.isNotEmpty() -> "API Key 已配置"
                            else -> "未配置（海报无法加载）"
                        }
                        StatusBadge(text = statusText, color = statusColor)
                    }
                }

                // 已配置的 Key 展示（掩码）
                if (currentKey.isNotEmpty() && !isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = maskKey(currentKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { isEditing = true }) {
                            Text("修改")
                        }
                    }
                }

                // 输入框
                if (isEditing || currentKey.isEmpty()) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("TMDB API Key") },
                        placeholder = { Text("输入你的 TMDB API Key...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTest(keyInput) },
                        enabled = !keyTesting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (keyTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("检测中")
                        } else {
                            Text("检测可用性")
                        }
                    }
                    Button(
                        onClick = {
                            onSave(keyInput)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
                if (currentKey.isNotEmpty() && isEditing) {
                    OutlinedButton(
                        onClick = {
                            keyInput = ""
                            onSave("")
                            isEditing = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清除")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isConnected: Boolean,
    username: String?,
    isLoading: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearError: () -> Unit
) {
    val indicatorColor = if (isConnected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧状态指示条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            modifier = Modifier.size(36.dp),
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val statusText = if (isConnected) "已登录" else "未登录"
                            val statusColor = if (isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            StatusBadge(text = statusText, color = statusColor)
                        }
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (isConnected) {
                        TextButton(onClick = onDisconnect) {
                            Text("断开")
                        }
                    } else {
                        Button(onClick = onConnect) {
                            Text("登录")
                        }
                    }
                }

                // 用户名展示
                if (isConnected && !username.isNullOrBlank()) {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 错误提示
                if (error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                            .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearError) {
                                Text(
                                    "知道了",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun TipCard() {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "使用说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "1. 登录 Trakt 和 NeoDB 账号\n" +
                        "2. 配置 TMDB API Key（可选，用于加载海报）\n" +
                        "3. 在「同步」页面检查并同步观影记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun maskKey(key: String): String {
    if (key.length <= 12) return "*".repeat(key.length)
    return key.take(4) + " **** **** " + key.takeLast(4)
}
