package com.example.traktneosync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.neodb.NeoDBEntry
import com.example.traktneosync.data.neodb.extractImdbId
import com.example.traktneosync.data.neodb.extractTmdbId
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.ui.detail.DetailScreen
import com.example.traktneosync.ui.neodb.NeoDBScreen
import com.example.traktneosync.ui.settings.SettingsScreen
import com.example.traktneosync.ui.search.SearchScreen
import com.example.traktneosync.ui.trakt.TraktScreen
import com.example.traktneosync.ui.theme.TraktNeoSyncTheme
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject lateinit var traktOAuthManager: TraktOAuthManager
    @Inject lateinit var neodbOAuthManager: NeoDBOAuthManager
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 设置全局异常处理器
        AppLogger.setupGlobalExceptionHandler(this)

        // 检查上次崩溃记录
        val lastCrash = (application as? TraktNeoSyncApp)?.getLastCrash()

        setContent {
            TraktNeoSyncTheme {
                var showErrorScreen by rememberSaveable { mutableStateOf(lastCrash != null) }
                var errorMessage by rememberSaveable { mutableStateOf(lastCrash ?: "") }
                var showDiagnostic by rememberSaveable { mutableStateOf(false) }
                var hiltInjectionOk by rememberSaveable { mutableStateOf(false) }
                var hiltError by rememberSaveable { mutableStateOf<String?>(null) }

                // 检查 Hilt 注入状态
                try {
                    val traktOk = ::traktOAuthManager.isInitialized
                    val neodbOk = ::neodbOAuthManager.isInitialized
                    hiltInjectionOk = traktOk && neodbOk
                    if (!hiltInjectionOk) {
                        hiltError = "TraktOAuthManager: $traktOk, NeoDBOAuthManager: $neodbOk"
                    }
                } catch (e: Exception) {
                    hiltError = e.message
                }

                if (showErrorScreen) {
                    // 崩溃诊断界面 - 红色背景，绝不白屏
                    CrashDiagnosticScreen(
                        errorMessage = errorMessage,
                        hiltError = hiltError,
                        onDismiss = {
                            (application as? TraktNeoSyncApp)?.clearLastCrash()
                            showErrorScreen = false
                        }
                    )
                } else if (showDiagnostic) {
                    // 诊断模式 - 显示系统信息
                    DiagnosticScreen(
                        hiltInjectionOk = hiltInjectionOk,
                        hiltError = hiltError,
                        onDismiss = { showDiagnostic = false }
                    )
                } else {
                    // 正常 UI
                    val darkThemeMode by authRepository.darkTheme
                        .collectAsState(initial = "system")
                    TraktNeoSyncApp(
                        darkThemeMode = darkThemeMode ?: "system",
                        onShowDiagnostic = { showDiagnostic = true }
                    )
                }
            }
        }

        // 处理首次启动带 deep link 的情况
        // 延迟处理，确保 Hilt 注入完成
        intent?.data?.let { uri ->
            lifecycleScope.launch {
                // 等待 Hilt 注入完成
                var attempts = 0
                while ((!::traktOAuthManager.isInitialized || !::neodbOAuthManager.isInitialized) && attempts < 50) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
                handleDeepLink(uri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        AppLogger.info(TAG, "处理Deep Link", mapOf("uri" to uri.toString(), "host" to (uri.host ?: "null")))

        if (!::traktOAuthManager.isInitialized || !::neodbOAuthManager.isInitialized) {
            AppLogger.warn(TAG, "OAuth管理器未初始化，跳过Deep Link")
            Toast.makeText(this, "OAuth管理器未初始化，请重启应用", Toast.LENGTH_LONG).show()
            return
        }

        when (uri.host) {
            "trakt" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val code = uri.getQueryParameter("code")
                    if (code.isNullOrEmpty()) {
                        AppLogger.error(TAG, "Trakt回调缺少code参数", null, mapOf("uri" to uri.toString()))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Trakt授权失败：缺少授权码", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    AppLogger.info(TAG, "开始处理Trakt OAuth回调", mapOf("codePrefix" to code.take(4)))
                    val success = try {
                        traktOAuthManager.handleCallback(uri)
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "Trakt OAuth回调处理异常", e)
                        false
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@MainActivity, "Trakt 登录成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Trakt 登录失败，请重试", Toast.LENGTH_LONG).show()
                        }
                    }
                    AppLogger.info(TAG, "Trakt OAuth回调完成", mapOf("success" to success))
                }
            }
            "neodb" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val code = uri.getQueryParameter("code")
                    if (code.isNullOrEmpty()) {
                        AppLogger.error(TAG, "NeoDB回调缺少code参数", null, mapOf("uri" to uri.toString()))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "NeoDB授权失败：缺少授权码", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    AppLogger.info(TAG, "开始处理NeoDB OAuth回调", mapOf("codePrefix" to code.take(4)))
                    val success = try {
                        neodbOAuthManager.handleCallback(uri)
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "NeoDB OAuth回调处理异常", e)
                        false
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@MainActivity, "NeoDB 登录成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "NeoDB 登录失败，请重试", Toast.LENGTH_LONG).show()
                        }
                    }
                    AppLogger.info(TAG, "NeoDB OAuth回调完成", mapOf("success" to success))
                }
            }
            else -> {
                AppLogger.warn(TAG, "未知的Deep Link host [host=${uri.host ?: "null"}]")
            }
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Trakt : BottomNavItem("trakt", "Trakt", Icons.Default.VideoLibrary)
    object Search : BottomNavItem("search", "搜索", Icons.Default.Search)
    object NeoDB : BottomNavItem("neodb", "NeoDB", Icons.Default.Bookmark)
    object Settings : BottomNavItem("settings", "设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraktNeoSyncApp(
    modifier: Modifier = Modifier,
    darkThemeMode: String = "system",
    onShowDiagnostic: () -> Unit = {}
) {
    TraktNeoSyncTheme(darkThemeMode = darkThemeMode) {
        val navController = rememberNavController()
        val navItems = listOf(
            BottomNavItem.Trakt,
            BottomNavItem.NeoDB,
            BottomNavItem.Search,
            BottomNavItem.Settings
        )
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        // Trakt 页面电影/剧集切换状态，提升到 MainActivity 层级共享
        var traktTypeIndex by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Trakt ↔ NeoDB") },
                    actions = {
                        IconButton(onClick = onShowDiagnostic) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = "诊断",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                if (item.route == BottomNavItem.Trakt.route) {
                                    // 点击 Trakt 底栏：切换电影/剧集
                                    traktTypeIndex = if (traktTypeIndex == 0) 1 else 0
                                }
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Trakt.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(BottomNavItem.Trakt.route) {
                    TraktScreen(
                        selectedTypeIndex = traktTypeIndex,
                        onSelectedTypeChange = { traktTypeIndex = it },
                        snackbarHostState = snackbarHostState,
                        onNavigateToMovieDetail = { movie ->
                            val encodedTitle = java.net.URLEncoder.encode(movie.title, "UTF-8")
                            val encodedPoster = java.net.URLEncoder.encode(movie.posterUrl ?: "", "UTF-8")
                            val imdbId = movie.imdbId?.takeIf { it.isNotBlank() } ?: "_null_"
                            navController.navigate(
                                "detail/movie/$encodedTitle/${movie.year ?: 0}/$imdbId/${movie.tmdbId ?: 0}/${movie.plays}?posterUrl=$encodedPoster"
                            )
                        },
                        onNavigateToShowDetail = { show ->
                            val encodedTitle = java.net.URLEncoder.encode(show.title, "UTF-8")
                            val encodedPoster = java.net.URLEncoder.encode(show.posterUrl ?: "", "UTF-8")
                            val imdbId = show.imdbId?.takeIf { it.isNotBlank() } ?: "_null_"
                            navController.navigate(
                                "detail/show/$encodedTitle/${show.year ?: 0}/$imdbId/${show.tmdbId ?: 0}/${show.plays}?posterUrl=$encodedPoster"
                            )
                        }
                    )
                }
                composable(BottomNavItem.NeoDB.route) {
                    NeoDBScreen(
                        onNavigateToDetail = { mark ->
                            val entry = mark.item
                            val encodedTitle = java.net.URLEncoder.encode(entry.displayTitle, "UTF-8")
                            val encodedPoster = java.net.URLEncoder.encode(entry.coverImageUrl ?: "", "UTF-8")
                            val type = when (entry.category) {
                                "movie" -> "movie"
                                "tv" -> "show"
                                else -> "movie"
                            }
                            val imdbId = entry.extractImdbId() ?: "_null_"
                            val tmdbId = entry.extractTmdbId() ?: 0
                            navController.navigate(
                                "detail/$type/$encodedTitle/0/$imdbId/$tmdbId/0?posterUrl=$encodedPoster"
                            )
                        },
                        onNavigateToEntry = { entry ->
                            val encodedTitle = java.net.URLEncoder.encode(entry.displayTitle, "UTF-8")
                            val encodedPoster = java.net.URLEncoder.encode(entry.coverImageUrl ?: "", "UTF-8")
                            val type = when (entry.category) {
                                "movie" -> "movie"
                                "tv" -> "show"
                                else -> "movie"
                            }
                            val imdbId = entry.extractImdbId() ?: "_null_"
                            val tmdbId = entry.extractTmdbId() ?: 0
                            navController.navigate(
                                "detail/$type/$encodedTitle/0/$imdbId/$tmdbId/0?posterUrl=$encodedPoster"
                            )
                        }
                    )
                }
                composable(BottomNavItem.Search.route) {
                    SearchScreen(
                        onNavigateToDetail = { entry ->
                            val encodedTitle = java.net.URLEncoder.encode(entry.displayTitle, "UTF-8")
                            val encodedPoster = java.net.URLEncoder.encode(entry.coverImageUrl ?: "", "UTF-8")
                            val type = if (entry.category == "movie") "movie" else "show"
                            val imdbId = entry.extractImdbId() ?: "_null_"
                            val tmdbId = entry.extractTmdbId() ?: 0
                            navController.navigate(
                                "detail/$type/$encodedTitle/0/$imdbId/$tmdbId/0?posterUrl=$encodedPoster"
                            )
                        }
                    )
                }
                composable(BottomNavItem.Settings.route) {
                    SettingsScreen()
                }
                composable("detail/{type}/{title}/{year}/{imdbId}/{tmdbId}/{plays}?posterUrl={posterUrl}") { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: "movie"
                    val title = backStackEntry.arguments?.getString("title") ?: ""
                    val year = backStackEntry.arguments?.getString("year")?.toIntOrNull()?.takeIf { it > 0 }
                    val imdbId = backStackEntry.arguments?.getString("imdbId")?.takeIf {
                        it.isNotBlank() && it != "_null_" && it != "null"
                    }
                    val tmdbId = backStackEntry.arguments?.getString("tmdbId")?.toLongOrNull()?.takeIf { it > 0 }
                    val rawPosterUrl = backStackEntry.arguments?.getString("posterUrl")?.takeIf {
                        it.isNotBlank()
                    }
                    val posterUrl = rawPosterUrl?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    val plays = backStackEntry.arguments?.getString("plays")?.toIntOrNull()?.takeIf { it > 0 }
                    val decodedType = if (type == "movie" || type == "sync") "电影" else "剧集"

                    DetailScreen(
                        title = java.net.URLDecoder.decode(title, "UTF-8"),
                        year = year,
                        type = decodedType,
                        posterUrl = posterUrl,
                        imdbId = imdbId,
                        tmdbId = tmdbId,
                        plays = plays,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun CrashDiagnosticScreen(
    errorMessage: String,
    hiltError: String?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFFEBEE) // 浅红色背景
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "⚠️ 应用崩溃诊断",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFB71C1C)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (hiltError != null) {
                Text(
                    text = "Hilt 注入状态: ❌ 失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFD32F2F)
                )
                Text(
                    text = hiltError,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD32F2F)
                )
            } else {
                Text(
                    text = "Hilt 注入状态: ✅ 成功",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "崩溃堆栈:",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB71C1C)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = errorMessage.take(2000),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD32F2F),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("崩溃日志", errorMessage)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("复制崩溃日志")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0)
                )
            ) {
                Text("尝试恢复UI")
            }
        }
    }
}

@Composable
fun DiagnosticScreen(
    hiltInjectionOk: Boolean,
    hiltError: String?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var logContent by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFE3F2FD) // 浅蓝色背景
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "🔧 诊断模式",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF1565C0)
            )

            Spacer(modifier = Modifier.height(16.dp))

            DiagnosticRow("应用版本", BuildConfig.VERSION_NAME)
            DiagnosticRow("版本号", BuildConfig.VERSION_CODE.toString())
            DiagnosticRow("Android API", Build.VERSION.SDK_INT.toString())
            DiagnosticRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
            DiagnosticRow("Hilt 注入", if (hiltInjectionOk) "✅ 成功" else "❌ 失败")
            if (hiltError != null) {
                DiagnosticRow("Hilt 错误", hiltError)
            }
            DiagnosticRow("Trakt Client ID", if (BuildConfig.TRAKT_CLIENT_ID.isNotEmpty()) "已设置 (${BuildConfig.TRAKT_CLIENT_ID.take(8)}...)" else "❌ 未设置")
            DiagnosticRow("NeoDB Client ID", if (BuildConfig.NEODB_CLIENT_ID.isNotEmpty()) "已设置 (${BuildConfig.NEODB_CLIENT_ID.take(8)}...)" else "❌ 未设置")
            DiagnosticRow("Build Type", BuildConfig.BUILD_TYPE)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        logContent = AppLogger.getLogContent(context)
                        showLogs = !showLogs
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6A1B9A)
                    )
                ) {
                    Text(if (showLogs) "隐藏日志" else "查看日志")
                }
                OutlinedButton(
                    onClick = {
                        AppLogger.clearLog(context)
                        logContent = ""
                    }
                ) {
                    Text("清空日志")
                }
            }

            if (showLogs && logContent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "日志内容:",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF1565C0)
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("日志", logContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("复制日志")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = logContent,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF263238),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0)
                )
            ) {
                Text("返回正常UI")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF78909C)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF263238)
        )
    }
}
