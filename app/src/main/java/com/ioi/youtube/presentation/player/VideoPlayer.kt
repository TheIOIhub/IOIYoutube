package com.ioi.youtube.presentation.player

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.domain.model.formatVideoLabel

/**
 * Показує готовий плеєр. `ExoPlayer` створює й володіє ним [PlayerHolder];
 * тут лише PlayerView, меню та індикація помилки.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoPlayer(
    player: ExoPlayer?,
    stream: VideoStream?,
    videoOptions: List<VideoVariant>,
    audioOptions: List<AudioTrackOption>,
    selectedVideo: VideoVariant?,
    selectedAudio: AudioTrackOption?,
    onSelectAuto: () -> Unit,
    onSelectVideo: (VideoVariant) -> Unit,
    onSelectAudio: (AudioTrackOption) -> Unit,
    scale: VideoScale,
    onScaleChange: (VideoScale) -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    error: String? = null,
    onRetry: () -> Unit = {},
    unavailableText: String = "Відео недоступне",
) {
    val auto = selectedVideo == null && selectedAudio == null
    val exoPlayer = player
    val videoStream = stream
    if (exoPlayer == null || videoStream == null ||
        buildPlaybackPlan(videoStream, selectedVideo, selectedAudio, auto) == null
    ) {
        UnavailableBox(
            modifier = modifier,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            text = unavailableText,
        )
        return
    }

    var qualityMenuOpen by remember(videoStream) { mutableStateOf(false) }
    var audioMenuOpen by remember(videoStream) { mutableStateOf(false) }
    var hlsOptions by remember(videoStream) { mutableStateOf(emptyList<QualityOption>()) }

    val context = LocalContext.current
    val onScaleChangeState = rememberUpdatedState(onScaleChange)
    val multiTouchState = remember { MultiTouchState() }
    val scaleDetector = remember(context) {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                private var totalScale = 1f
                private var target: VideoScale? = null

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    totalScale = 1f
                    target = null
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    totalScale *= detector.scaleFactor
                    val next = scaleAfterPinch(totalScale)
                    if (next != null && next != target) {
                        target = next
                        onScaleChangeState.value(next)
                    }
                    return true
                }
            },
        )
    }

    DisposableEffect(exoPlayer, videoStream) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                hlsOptions = chooseQualityOptions(tracks.videoTrackInfos())
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        modifier = modifier.then(
            if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = exoPlayer
                    useController = true
                    resizeMode = resizeModeFor(scale)
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeModeFor(scale)
                view.setOnTouchListener { _, event ->
                    scaleDetector.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            multiTouchState.consuming = false
                            false
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            multiTouchState.consuming = true
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val consume = multiTouchState.consuming
                            multiTouchState.consuming = false
                            consume
                        }

                        else -> multiTouchState.consuming || event.pointerCount > 1
                    }
                }
            },
            onRelease = { view ->
                view.setOnTouchListener(null)
                view.player = null
            },
        )

        if (error != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text("Спробувати знову", color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (audioOptions.size > 1) {
                Box {
                    IconButton(
                        onClick = {
                            qualityMenuOpen = false
                            audioMenuOpen = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = "Аудіодоріжка",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = audioMenuOpen,
                        onDismissRequest = { audioMenuOpen = false },
                    ) {
                        audioOptions.forEach { track ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (track == selectedAudio) "✓ ${track.label}" else track.label,
                                    )
                                },
                                onClick = {
                                    onSelectAudio(track)
                                    audioMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = {
                        audioMenuOpen = false
                        qualityMenuOpen = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.HighQuality,
                        contentDescription = "Якість",
                        tint = Color.White,
                    )
                }
                DropdownMenu(
                    expanded = qualityMenuOpen,
                    onDismissRequest = { qualityMenuOpen = false },
                ) {
                    if (videoStream.hlsUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Авто (HLS)") },
                            onClick = {
                                onSelectAuto()
                                qualityMenuOpen = false
                            },
                        )
                    }
                    videoOptions.forEach { variant ->
                        DropdownMenuItem(
                            text = { Text(formatVideoLabel(variant.height, variant.bitrate)) },
                            onClick = {
                                onSelectVideo(variant)
                                qualityMenuOpen = false
                            },
                        )
                    }
                    if (auto && hlsOptions.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Авто") },
                            onClick = {
                                exoPlayer.trackSelectionParameters =
                                    qualityParameters(exoPlayer, null)
                                qualityMenuOpen = false
                            },
                        )
                        hlsOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatQualityLabel(option.height, option.bitrate)) },
                                onClick = {
                                    exoPlayer.trackSelectionParameters =
                                        qualityParameters(exoPlayer, option)
                                    qualityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    onScaleChange(
                        if (scale == VideoScale.FIT) VideoScale.FILL else VideoScale.FIT,
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = if (scale == VideoScale.FIT) {
                        "Заповнити екран"
                    } else {
                        "Вписати відео"
                    },
                    tint = Color.White,
                )
            }

            if (onToggleFullscreen != null) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Вийти з повного екрана" else "На весь екран",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Тримає стан перехоплення мультитачу між перекомпозиціями. */
private class MultiTouchState {
    var consuming: Boolean = false
}

@Composable
private fun UnavailableBox(
    modifier: Modifier,
    isFullscreen: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    text: String,
) {
    Box(
        modifier = modifier.then(
            if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        if (onToggleFullscreen != null) {
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onToggleFullscreen,
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "Вийти з повного екрана" else "На весь екран",
                    tint = Color.White,
                )
            }
        }
    }
}
