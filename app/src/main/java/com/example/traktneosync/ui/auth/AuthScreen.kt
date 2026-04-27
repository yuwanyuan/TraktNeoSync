package com.example.traktneosync.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Trakt 认证卡片
        AuthCard(
            title = "Trakt",
            icon = Icons.Default.AccountCircle,
            isConnected = uiState.traktConnected,
            username = uiState.traktUsername,
            isLoading = false,
            error = null,
            onConnect = { viewModel.connectTrakt() },
            onDisconnect = { viewModel.disconnectTrakt() },
            onClearError = {}
        )

        // NeoDB 认证卡片
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

        Spacer(modifier = Modifier.weight(1f))

        // 使用说明
        OutlinedCard {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. 先登录 Trakt 和 NeoDB 两个账号\n" +
                            "2. 在「同步」页面点击检查同步\n" +
                            "3. 查看未同步的条目并一键添加",
                    style = MaterialTheme.typography.bodyMedium
                )
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                        modifier = Modifier.size(40.dp),
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (isConnected) {
                                "已登录: $username"
                            } else {
                                "未登录"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                } else if (isConnected) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("断开")
                    }
                } else {
                    Button(onClick = onConnect) {
                        Text("登录")
                    }
                }
            }

            // 错误提示
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
