package com.example.traktneosync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.ui.auth.AuthScreen
import com.example.traktneosync.ui.movies.MoviesScreen
import com.example.traktneosync.ui.search.SearchScreen
import com.example.traktneosync.ui.shows.ShowsScreen
import com.example.traktneosync.ui.sync.SyncScreen
import com.example.traktneosync.ui.theme.TraktNeoSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject lateinit var traktOAuthManager: TraktOAuthManager
    @Inject lateinit var neodbOAuthManager: NeoDBOAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TraktNeoSyncTheme {
                TraktNeoSyncApp()
            }
        }
        // 处理首次启动带 deep link 的情况
        handleDeepLink(intent?.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        Log.d(TAG, "Handling deep link: $uri")

        when (uri.host) {
            "trakt" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val success = traktOAuthManager.handleCallback(uri)
                    Log.d(TAG, "Trakt OAuth callback: success=$success")
                }
            }
            "neodb" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val success = neodbOAuthManager.handleCallback(uri)
                    Log.d(TAG, "NeoDB OAuth callback: success=$success")
                }
            }
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Movies : BottomNavItem("movies", "电影", Icons.Default.Movie)
    object Shows : BottomNavItem("shows", "剧集", Icons.Default.Tv)
    object Sync : BottomNavItem("sync", "同步", Icons.Default.Sync)
    object Search : BottomNavItem("search", "搜索", Icons.Default.Search)
    object Account : BottomNavItem("account", "账号", Icons.Default.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraktNeoSyncApp(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navItems = listOf(
        BottomNavItem.Movies,
        BottomNavItem.Shows,
        BottomNavItem.Sync,
        BottomNavItem.Search,
        BottomNavItem.Account
    )
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Trakt ↔ NeoDB") }
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Sync.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Movies.route) {
                MoviesScreen()
            }
            composable(BottomNavItem.Shows.route) {
                ShowsScreen()
            }
            composable(BottomNavItem.Sync.route) {
                SyncScreen()
            }
            composable(BottomNavItem.Search.route) {
                SearchScreen()
            }
            composable(BottomNavItem.Account.route) {
                AuthScreen()
            }
        }
    }
}
