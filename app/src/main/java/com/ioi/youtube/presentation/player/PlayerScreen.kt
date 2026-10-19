package com.ioi.youtube.presentation.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.util.findActivity

@Composable
fun PlayerRoute(
    onVideoClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isFullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isFullscreen, activity) {
        val current = activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(current.window, current.window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        // Вихiд iз повного екрана ставить портрет: iнакше на телефонi, який
        // тримають боком, SENSOR одразу поверне ландшафт i вихід не спрацює.
        // Щойно портрет застосувався — знову вмикаємо вільний поворот.
        if (!isFullscreen &&
            current.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ) {
            current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    DisposableEffect(activity) {
        val current = activity
        onDispose {
            if (current != null) {
                WindowInsetsControllerCompat(current.window, current.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    PlayerScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
        isFullscreen = isFullscreen,
        onToggleFullscreen = {
            activity?.requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        },
    )
}

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val source = (uiState.stream as? UiState.Success)?.data.toPlayerSource()

    when (val videoState = uiState.video) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = videoState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> if (isFullscreen) {
            // Ландшафт = повний екран: лише вiдео, без заголовка, коментарiв i рекомендацiй.
            VideoPlayer(
                source = source,
                modifier = modifier.fillMaxSize(),
                isFullscreen = true,
                onToggleFullscreen = onToggleFullscreen,
            )
        } else {
            val details = videoState.data
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "player") {
                    VideoPlayer(
                        source = source,
                        onToggleFullscreen = onToggleFullscreen,
                    )
                }

                item(key = "stream-error") {
                    StreamErrorRow(state = uiState.stream, onRetry = onRetry)
                }

                item(key = "meta") {
                    VideoMetaSection(
                        title = details.title,
                        uploaderName = details.uploaderName,
                        metaLine = buildMetaLine(details),
                        description = details.description,
                        fromCache = videoState.fromCache,
                    )
                }

                item(key = "comments") {
                    CommentsSection(state = uiState.comments, onRetry = onRetry)
                }

                item(key = "recommendations") {
                    RecommendationsSection(
                        videos = details.recommendedVideos,
                        onVideoClick = onVideoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoMetaSection(
    title: String,
    uploaderName: String,
    metaLine: String,
    description: String,
    fromCache: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(12.dp)) {
        if (fromCache) {
            OfflineBanner()
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = metaLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = uploaderName, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = description.ifBlank { "Без опису" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun StreamErrorRow(
    state: UiState<VideoStream>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is UiState.Error) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text("Спробувати знову")
        }
    }
}

private fun buildMetaLine(details: VideoDetails): String = listOfNotNull(
    Formatters.formatViews(details.viewCount),
    Formatters.formatCount(details.likeCount).let { "$it лайків" },
    details.uploadDate,
).joinToString(" · ")
