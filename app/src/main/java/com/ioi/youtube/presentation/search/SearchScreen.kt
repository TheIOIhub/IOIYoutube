package com.ioi.youtube.presentation.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard

@Composable
fun SearchRoute(
    onVideoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: UiState<SearchResults>,
    onQueryChange: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        refreshing = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Пошук відео") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRetry()
            },
        ) {
            when (uiState) {
                is UiState.Loading -> LoadingBox()

                is UiState.Error -> ErrorBox(message = uiState.message, onRetry = onRetry)

                is UiState.Success -> {
                    if (uiState.data.videos.isEmpty()) {
                        EmptyBox(text = "Нічого не знайдено")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (uiState.fromCache) {
                                item(key = "offline-banner") { OfflineBanner() }
                            }
                            items(items = uiState.data.videos, key = { it.id }) { video ->
                                VideoCard(video = video, onClick = onVideoClick)
                            }
                        }
                    }
                }
            }
        }
    }
}
