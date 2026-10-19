package com.ioi.youtube.presentation.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.player.toPlayerSource

@Composable
fun ShortsRoute(
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShortsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onLoadStream = viewModel::loadStream,
        onRetryStream = viewModel::retryStream,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun ShortsScreen(
    uiState: ShortsUiState,
    onRetry: () -> Unit,
    onLoadStream: (String) -> Unit,
    onRetryStream: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val feed = uiState.shorts) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = feed.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            if (feed.data.isEmpty()) {
                EmptyBox(text = "Тут поки що порожньо", modifier = modifier)
            } else {
                val pagerState = rememberPagerState(pageCount = { feed.data.size })

                // Потiк потрiбен лише видимiй сторiнцi та наступнiй (щоб свайп був без паузи).
                LaunchedEffect(pagerState.currentPage, feed.data.size) {
                    val current = feed.data.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
                    onLoadStream(current.id)
                    feed.data.getOrNull(pagerState.currentPage + 1)?.let { onLoadStream(it.id) }
                    if (pagerState.currentPage >= feed.data.size - 2) onLoadMore()
                }

                VerticalPager(
                    state = pagerState,
                    modifier = modifier.fillMaxSize(),
                ) { page ->
                    val video = feed.data[page]
                    ShortPage(
                        video = video,
                        stream = uiState.streams[video.id] ?: UiState.Loading,
                        isActive = page == pagerState.currentPage,
                        onRetry = { onRetryStream(video.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortPage(
    video: VideoSummary,
    stream: UiState<VideoStream>,
    isActive: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isActive) {
            when (stream) {
                is UiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )

                is UiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stream.message, color = Color.White)
                    TextButton(onClick = onRetry) {
                        Text("Спробувати знову")
                    }
                }

                is UiState.Success -> ShortVideoPlayer(source = stream.data.toPlayerSource())
            }
        } else {
            // Сусiднi сторiнки не тримають плеєр — лише прев'ю.
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ShortOverlay(video = video, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ShortOverlay(video: VideoSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(16.dp),
    ) {
        Text(
            text = "@${video.uploaderName}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = video.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Formatters.formatViews(video.viewCount),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
