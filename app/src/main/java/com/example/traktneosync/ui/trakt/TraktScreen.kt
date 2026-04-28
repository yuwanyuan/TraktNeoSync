package com.example.traktneosync.ui.trakt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.traktneosync.ui.movies.MovieItem
import com.example.traktneosync.ui.movies.MoviesScreen
import com.example.traktneosync.ui.shows.ShowItem
import com.example.traktneosync.ui.shows.ShowsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TraktScreen(
    snackbarHostState: SnackbarHostState? = null,
    onNavigateToMovieDetail: (MovieItem) -> Unit = {},
    onNavigateToShowDetail: (ShowItem) -> Unit = {}
) {
    // 0 = 电影, 1 = 剧集
    var selectedTypeIndex by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // 同步按钮状态与 Pager 页面
    LaunchedEffect(selectedTypeIndex) {
        if (pagerState.currentPage != selectedTypeIndex) {
            pagerState.animateScrollToPage(selectedTypeIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTypeIndex) {
            selectedTypeIndex = pagerState.currentPage
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // 顶部电影/剧集切换按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedTypeIndex == 0) {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("电影")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedTypeIndex = 0
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("电影")
                }
            }

            if (selectedTypeIndex == 1) {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("剧集")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedTypeIndex = 1
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("剧集")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> MoviesScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigateToDetail = onNavigateToMovieDetail
                )
                else -> ShowsScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigateToDetail = onNavigateToShowDetail
                )
            }
        }
    }
}
