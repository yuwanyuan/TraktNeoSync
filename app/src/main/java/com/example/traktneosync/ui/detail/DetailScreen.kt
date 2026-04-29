package com.example.traktneosync.ui.detail

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    title: String,
    year: Int?,
    type: String,
    posterUrl: String?,
    imdbId: String?,
    tmdbId: Long?,
    plays: Int? = null,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(title, year, type, imdbId, tmdbId) {
        viewModel.loadNeoDBReviews(title, year, type, imdbId, tmdbId)
        if (tmdbId != null && tmdbId > 0) {
            viewModel.loadTmdbDetails(tmdbId, type)
        }
    }

    val selectedUrl = uiState.selectedImageUrl
    if (selectedUrl != null) {
        Dialog(
            onDismissRequest = { viewModel.dismissImageViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = selectedUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { viewModel.dismissImageViewer() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                TextButton(
                    onClick = { saveImage(context, selectedUrl) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text("保存", color = Color.White)
                }
            }
        }
    }

    if (uiState.showRatingDialog) {
        var ratingValue by remember(uiState.currentMark) {
            mutableFloatStateOf(
                (uiState.currentMark?.ratingGrade ?: 5).toFloat().coerceIn(1f, 10f)
            )
        }
        var commentText by remember(uiState.currentMark) {
            mutableStateOf(uiState.currentMark?.commentText ?: "")
        }
        var shareToMastodon by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { viewModel.dismissRatingDialog() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "评分",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.isLoadingMarkStatus) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Column {
                            Text(
                                text = "${ratingValue.toInt()} 分",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Slider(
                                value = ratingValue,
                                onValueChange = { ratingValue = it },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            label = { Text("评论（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "同步到长毛象",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "发布到 Fediverse 时间线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = shareToMastodon,
                                onCheckedChange = { shareToMastodon = it }
                            )
                        }
                    }

                    val ratingError = uiState.ratingSubmitError
                    if (ratingError != null) {
                        Text(
                            text = ratingError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.dismissRatingDialog() },
                            enabled = !uiState.isSubmittingRating
                        ) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.submitRating(
                                    ratingGrade = ratingValue.toInt(),
                                    comment = commentText,
                                    shareToMastodon = shareToMastodon
                                )
                            },
                            enabled = !uiState.isSubmittingRating && !uiState.isLoadingMarkStatus
                        ) {
                            if (uiState.isSubmittingRating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("提交")
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 25.dp else 0.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.35f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 120.dp, height = 180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (posterUrl != null) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = type.take(1).uppercase(),
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (posterUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            if (year != null) {
                                Text(
                                    text = "$year",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (posterUrl != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AssistChip(
                                onClick = { },
                                label = { Text(type) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    labelColor = Color.White
                                )
                            )
                            if (plays != null && plays > 0) {
                                Text(
                                    text = "已观看 $plays ${if (type == "剧集") "集" else "次"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (posterUrl != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (uiState.imdbRating != null || uiState.neoDBRating != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.imdbRating != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "%.1f".format(uiState.imdbRating),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "IMDB${uiState.imdbVoteCount?.let { " · ${it}人评" } ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (uiState.imdbRating != null && uiState.neoDBRating != null) {
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                                if (uiState.neoDBRating != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable {
                                            viewModel.openRatingDialog()
                                        }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "%.1f".format(uiState.neoDBRating),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "NeoDB${if (uiState.neoDBRatingCount > 0) " · ${uiState.neoDBRatingCount}人评" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (imdbId != null) {
                                InfoRow("IMDB ID", imdbId)
                            }
                            if (tmdbId != null) {
                                InfoRow("TMDB ID", tmdbId.toString())
                            }
                        }
                    }
                }

                val overview = uiState.overview
                if (!overview.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "简介",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                val allImages = (uiState.backdropUrls + uiState.posterUrls).filter { it.isNotBlank() }.distinct()
                if (allImages.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "相关图片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = allImages,
                                    key = { it.takeLast(30) }
                                ) { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(280.dp)
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.selectImage(url) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoadingDetails) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                item {
                    Text(
                        text = "NeoDB 评论",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when {
                    uiState.isLoadingReviews && uiState.reviews.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    uiState.reviewError != null && uiState.reviews.isEmpty() -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = uiState.reviewError ?: "加载失败",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    uiState.reviews.isEmpty() -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                )
                            ) {
                                Text(
                                    text = "暂无评论",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        items(
                            count = uiState.reviews.size,
                            key = { index -> "${index}_" + uiState.reviews[index].username + uiState.reviews[index].date }
                        ) { index ->
                            ReviewCard(review = uiState.reviews[index])
                        }
                    }
                }

                if (uiState.hasMoreReviews || uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                OutlinedButton(onClick = { viewModel.loadMoreReviews() }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (review.avatarUrl != null) {
                        AsyncImage(
                            model = review.avatarUrl,
                            contentDescription = review.username,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = review.username.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Column {
                        Text(
                            text = review.username,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (review.relativeDate.isNotBlank()) {
                            Text(
                                text = review.relativeDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (review.rating != null) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${review.rating}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )
                }
            }

            if (review.content.isNotBlank()) {
                Text(
                    text = review.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 40.dp)
                )
            } else if (review.rating != null) {
                Text(
                    text = "（仅评分，无评论内容）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun saveImage(context: Context, imageUrl: String) {
    try {
        val fileName = "TraktNeoSync_${System.currentTimeMillis()}.jpg"
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("保存图片")
            .setDescription("正在下载图片...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
