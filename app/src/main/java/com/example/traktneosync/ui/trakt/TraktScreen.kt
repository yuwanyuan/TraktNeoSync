package com.example.traktneosync.ui.trakt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.traktneosync.ui.movies.MovieItem
import com.example.traktneosync.ui.movies.MoviesScreen
import com.example.traktneosync.ui.shows.ShowItem
import com.example.traktneosync.ui.shows.ShowsScreen

@Composable
fun TraktScreen(
    onNavigateToMovieDetail: (MovieItem) -> Unit = {},
    onNavigateToShowDetail: (ShowItem) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf("movie") } // "movie" or "show"

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
            if (selectedType == "movie") {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("电影")
                }
                OutlinedButton(
                    onClick = { selectedType = "show" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("剧集")
                }
            } else {
                OutlinedButton(
                    onClick = { selectedType = "movie" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("电影")
                }
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("剧集")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 内容区域
        when (selectedType) {
            "movie" -> MoviesScreen(onNavigateToDetail = onNavigateToMovieDetail)
            else -> ShowsScreen(onNavigateToDetail = onNavigateToShowDetail)
        }
    }
}
