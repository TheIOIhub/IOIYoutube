package com.ioi.youtube.presentation.shorts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ioi.youtube.presentation.player.PlayerSource
import com.ioi.youtube.presentation.player.playerMediaItem

/** Плеєр одного шортса: цикл, автоплей, тап по вiдео — пауза/вiдтворення. */
@Composable
fun ShortVideoPlayer(
    source: PlayerSource?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var paused by remember(source) { mutableStateOf(false) }
    val playable = source?.takeIf { it.url.isNotBlank() }

    val exoPlayer = remember(playable?.url) {
        playable?.let { video ->
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(playerMediaItem(video))
                playWhenReady = true
                prepare()
            }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    if (playable == null || exoPlayer == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Відео недоступне",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                paused = !paused
                if (paused) exoPlayer.pause() else exoPlayer.play()
            },
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
            }
        },
    )
}
