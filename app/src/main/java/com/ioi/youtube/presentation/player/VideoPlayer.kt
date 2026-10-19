package com.ioi.youtube.presentation.player

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Тег для logcat: фiльтр `adb logcat -s IOITube`. */
private const val TAG = "IOITube"

@Composable
fun VideoPlayer(
    source: PlayerSource?,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    unavailableText: String = "Відео недоступне",
) {
    if (source == null || source.url.isBlank()) {
        Box(
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = unavailableText, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var usingFallback by remember(source) { mutableStateOf(false) }
    var videoTracks by remember(source) { mutableStateOf(emptyList<VideoTrackInfo>()) }
    var selected by remember(source) { mutableStateOf<QualityOption?>(null) }
    var menuOpen by remember(source) { mutableStateOf(false) }
    var playerError by remember(source) { mutableStateOf<PlaybackException?>(null) }

    val exoPlayer = remember(source.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(playerMediaItem(source))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer, source) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                videoTracks = tracks.videoTrackInfos()
            }

            override fun onPlayerError(error: PlaybackException) {
                val fallback = source.fallbackUrl
                if (!source.isHls || usingFallback || fallback == null || fallback.isBlank()) {
                    playerError = error
                    Log.e(TAG, "player error", error)
                    return
                }
                Log.e(TAG, "HLS playback failed, falling back to progressive", error)
                usingFallback = true
                videoTracks = emptyList()
                selected = null
                exoPlayer.setMediaItem(playerMediaItem(PlayerSource(fallback, isHls = false, fallbackUrl = null)))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    val options = chooseQualityOptions(videoTracks)
    val showQuality = source.isHls && !usingFallback && playerError == null && options.isNotEmpty()

    Box(
        modifier = modifier.then(
            if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
        ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showQuality) {
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = Color.White)
                        Text(
                            text = selected?.let { "${it.height}p" } ?: "Авто",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Авто") },
                            onClick = {
                                exoPlayer.trackSelectionParameters = qualityParameters(exoPlayer, null)
                                selected = null
                                menuOpen = false
                            },
                        )
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatQualityLabel(option.height, option.bitrate)) },
                                onClick = {
                                    exoPlayer.trackSelectionParameters =
                                        qualityParameters(exoPlayer, option)
                                    selected = option
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
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
