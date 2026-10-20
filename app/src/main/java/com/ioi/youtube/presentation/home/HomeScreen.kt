package com.ioi.youtube.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard

@Composable
fun HomeRoute(
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onShortClick = onShortClick,
        onTopicSelected = viewModel::selectTopic,
        onSortSelected = viewModel::selectSort,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    onTopicSelected: (FeedTopic) -> Unit,
    onSortSelected: (FeedSort) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopicChips(
            selectedTopic = uiState.topic,
            sort = uiState.sort,
            onTopicSelected = onTopicSelected,
            onSortSelected = onSortSelected,
        )

        when {
            uiState.error != null && uiState.items.isEmpty() -> ErrorBox(
                message = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            uiState.loading && uiState.items.isEmpty() -> LoadingBox()

            uiState.items.isEmpty() && !uiState.loading -> Text(
                text = "Тут поки що порожньо",
                modifier = Modifier.fillMaxSize().padding(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> FeedList(
                uiState = uiState,
                onVideoClick = onVideoClick,
                onShortClick = onShortClick,
                onLoadMore = onLoadMore,
                onRefresh = onRefresh,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    uiState: HomeUiState,
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, uiState.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (lastVisible >= uiState.items.lastIndex - 4) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = uiState.loading && uiState.items.isNotEmpty(),
        onRefresh = onRefresh,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (uiState.fromCache) {
                item(key = "offline-banner") { OfflineBanner() }
            }
            items(
                items = uiState.items,
                key = { item ->
                    when (item) {
                        is FeedItem.Video -> item.video.id
                        is FeedItem.ShortsBlock -> item.id
                    }
                },
            ) { item ->
                when (item) {
                    is FeedItem.Video -> VideoCard(video = item.video, onClick = onVideoClick)
                    is FeedItem.ShortsBlock -> if (item.shorts.isNotEmpty()) {
                        ShortsBlockRow(block = item, onShortClick = onShortClick)
                    }
                }
            }
            item(key = "footer") {
                FeedFooter(uiState = uiState, onLoadMore = onLoadMore)
            }
        }
    }
}

@Composable
private fun FeedFooter(
    uiState: HomeUiState,
    onLoadMore: () -> Unit,
) {
    when {
        uiState.loadingMore -> Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.loadMoreError != null -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = uiState.loadMoreError,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onLoadMore) { Text("Повторити") }
        }

        uiState.exhausted -> Text(
            text = "Більше немає",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TopicChips(
    selectedTopic: FeedTopic,
    sort: FeedSort,
    onTopicSelected: (FeedTopic) -> Unit,
    onSortSelected: (FeedSort) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("topic-chips"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = FeedTopic.entries, key = { it.id }) { topic ->
            FilterChip(
                selected = topic == selectedTopic,
                onClick = { onTopicSelected(topic) },
                label = { Text(topic.label) },
            )
        }
        item(key = "sort") {
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                FilterChip(
                    selected = sort != FeedSort.RELEVANCE,
                    onClick = { menuOpen = true },
                    label = { Text(sort.label) },
                    leadingIcon = {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                    },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    FeedSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortSelected(option)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsBlockRow(
    block: FeedItem.ShortsBlock,
    onShortClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SmartDisplay,
                contentDescription = null,
                tint = Color(0xFFFF0033),
            )
            Text(
                text = "YouTube Shorts",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.shorts.forEach { short ->
                ShortCard(
                    short = short,
                    onClick = onShortClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ShortCard(
    short: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(short.id) },
    ) {
        AsyncImage(
            model = short.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        ) {
            Text(
                text = short.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${short.uploaderName} · ${Formatters.formatViews(short.viewCount)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
