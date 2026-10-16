package com.ioi.youtube.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard

@Composable
fun HomeRoute(
    onVideoClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
    )
}

@Composable
fun HomeScreen(
    uiState: UiState<List<VideoSummary>>,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = uiState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            if (uiState.data.isEmpty()) {
                EmptyBox(text = "Тут поки що порожньо", modifier = modifier)
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    if (uiState.fromCache) {
                        item(key = "offline-banner") { OfflineBanner() }
                    }
                    items(items = uiState.data, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = onVideoClick)
                    }
                }
            }
        }
    }
}
