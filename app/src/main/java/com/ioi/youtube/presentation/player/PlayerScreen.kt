package com.ioi.youtube.presentation.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.domain.model.chooseAudioTracks
import com.ioi.youtube.domain.model.chooseVideoVariants
import com.ioi.youtube.domain.model.defaultAudio
import com.ioi.youtube.domain.model.defaultVideo
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard
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
            // Вихiд ставить портрет i лишає його: автоматично повертати SENSOR
            // не можна — на телефонi, який тримають боком, вiн одразу поверне
            // ландшафт, i користувач знову опиниться в повному екранi.
            // Вільний поворот повертається при наступному входi на екран вiдео
            // (LaunchedEffect у IOITubeApp).
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
    holderFactory: (Context) -> PlayerHolder = { PlayerHolder(it) },
) {
    when (val videoState = uiState.video) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = videoState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            val details = videoState.data
            val stream = (uiState.stream as? UiState.Success)?.data
            val context = LocalContext.current

            // Holder створюється ДО гілки портрет/ландшафт і не залежить від неї.
            val holder = remember(stream, context) { stream?.let { holderFactory(context) } }
            val videoOptions = remember(stream) { chooseVideoVariants(stream?.videoOnly.orEmpty()) }
            val audioOptions = remember(stream) { chooseAudioTracks(stream?.audioTracks.orEmpty()) }
            val initialVideo = remember(stream) { defaultVideo(videoOptions) }
            val initialAudio = remember(stream) { defaultAudio(audioOptions) }
            var selectedVideo by remember(stream) { mutableStateOf(initialVideo) }
            var selectedAudio by remember(stream) { mutableStateOf(initialAudio) }
            var scale by rememberSaveable(stream?.url) { mutableStateOf(VideoScale.FIT) }

            LaunchedEffect(holder, stream, selectedVideo, selectedAudio) {
                if (holder == null || stream == null) return@LaunchedEffect
                holder.update(
                    stream = stream,
                    video = selectedVideo,
                    audio = selectedAudio,
                    auto = selectedVideo == null && selectedAudio == null,
                )
            }

            DisposableEffect(holder) {
                onDispose { holder?.release() }
            }

            val player: @Composable (Modifier) -> Unit = { playerModifier ->
                VideoPlayer(
                    player = holder?.player,
                    stream = stream,
                    videoOptions = videoOptions,
                    audioOptions = audioOptions,
                    selectedVideo = selectedVideo,
                    selectedAudio = selectedAudio,
                    onSelectAuto = {
                        selectedVideo = null
                        selectedAudio = null
                    },
                    onSelectVideo = { selectedVideo = it },
                    onSelectAudio = { track ->
                        selectedAudio = track
                        // З HLS-режиму вибір мови має одразу перевести на merged:
                        // без video-only плану нема з чим склеювати аудіо.
                        if (selectedVideo == null) selectedVideo = defaultVideo(videoOptions)
                    },
                    scale = scale,
                    onScaleChange = { scale = it },
                    error = holder?.error,
                    onRetry = onRetry,
                    modifier = playerModifier,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }

            if (isFullscreen) {
                player(modifier.fillMaxSize().testTag("player-container"))
            } else {
                Column(modifier = modifier.fillMaxSize()) {
                    player(Modifier.fillMaxWidth().testTag("player-container"))

                    LazyColumn(modifier = Modifier.weight(1f)) {
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
                        item(key = "recommendations-header") {
                            Text(
                                text = "Рекомендації",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            items = details.recommendedVideos,
                            key = { it.id },
                        ) { video ->
                            VideoCard(video = video, onClick = onVideoClick)
                        }
                    }
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
