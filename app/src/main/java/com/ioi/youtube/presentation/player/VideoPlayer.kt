package com.ioi.youtube.presentation.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ioi.youtube.presentation.util.findActivity
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val onScaleChangeState = rememberUpdatedState(onScaleChange)
    val touchState = remember { PlayerTouchState() }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    val entryProgress = remember { Animatable(1f) }

    LaunchedEffect(gestureHint, gestureActive) {
        if (gestureHint != null && !gestureActive) {
            delay(700)
            gestureHint = null
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            entryProgress.snapTo(0f)
            entryProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        } else {
            entryProgress.snapTo(1f)
        }
    }

    // Після виходу з повного екрана повертаємо яскравість, яка була до нього.
    DisposableEffect(isFullscreen, activity) {
        val window = activity?.window
        val restore = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            if (isFullscreen && window != null) {
                val attrs = window.attributes
                attrs.screenBrightness = restore
                window.attributes = attrs
            }
        }
    }

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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = 0.9f + 0.1f * entryProgress.value
                    scaleX = zoom
                    scaleY = zoom
                },
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
                    val pinching = event.pointerCount > 1 || touchState.mode == GestureMode.PINCH
                    if (event.pointerCount > 1) {
                        touchState.mode = GestureMode.PINCH
                        touchState.consuming = true
                        gestureActive = false
                    }
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touchState.consuming = false
                            touchState.mode = GestureMode.NONE
                            touchState.target = null
                            touchState.downX = event.x
                            touchState.downY = event.y
                            touchState.downTime = event.eventTime
                            touchState.moved = false
                            touchState.lastPercent = -1
                            gestureActive = false
                            false
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            touchState.mode = GestureMode.PINCH
                            touchState.consuming = true
                            gestureActive = false
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!isFullscreen) {
                                touchState.consuming || pinching
                            } else if (pinching) {
                                touchState.consuming = true
                                true
                            } else {
                                val dx = event.x - touchState.downX
                                val dy = event.y - touchState.downY
                                if (!touchState.moved &&
                                    (abs(dx) > touchSlop || abs(dy) > touchSlop)
                                ) {
                                    touchState.moved = true
                                }
                                if (touchState.mode == GestureMode.NONE &&
                                    touchState.moved &&
                                    abs(dy) > abs(dx)
                                ) {
                                    touchState.mode = GestureMode.VERTICAL
                                    touchState.target = if (touchState.downX < view.width / 2f) {
                                        GestureTarget.BRIGHTNESS
                                    } else {
                                        GestureTarget.VOLUME
                                    }
                                    touchState.startLevel = when (touchState.target) {
                                        GestureTarget.VOLUME -> currentVolume(audioManager)
                                        else -> currentBrightness(context, activity)
                                    }
                                    touchState.lastPercent = -1
                                    gestureActive = true
                                }
                                val target = touchState.target
                                if (touchState.mode == GestureMode.VERTICAL && target != null) {
                                    val level = adjustedLevel(
                                        touchState.startLevel,
                                        dy,
                                        view.height.toFloat(),
                                    )
                                    val shownLevel = if (target == GestureTarget.BRIGHTNESS) {
                                        level.coerceAtLeast(MIN_BRIGHTNESS)
                                    } else {
                                        level
                                    }
                                    val percent = levelPercent(shownLevel)
                                    if (percent != touchState.lastPercent) {
                                        touchState.lastPercent = percent
                                        when (target) {
                                            GestureTarget.VOLUME -> applyVolume(audioManager, level)
                                            GestureTarget.BRIGHTNESS -> applyBrightness(activity, level)
                                        }
                                        gestureHint = when (target) {
                                            GestureTarget.VOLUME -> "Гучність $percent%"
                                            GestureTarget.BRIGHTNESS -> "Яскравість $percent%"
                                        }
                                    }
                                    gestureActive = true
                                    touchState.consuming = true
                                    true
                                } else {
                                    false
                                }
                            }
                        }

                        MotionEvent.ACTION_POINTER_UP -> touchState.consuming || pinching

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val wasTap = isFullscreen &&
                                event.actionMasked == MotionEvent.ACTION_UP &&
                                touchState.mode == GestureMode.NONE &&
                                !touchState.moved &&
                                event.eventTime - touchState.downTime < TAP_TIMEOUT_MS
                            val consumed = touchState.consuming
                            touchState.consuming = false
                            touchState.mode = GestureMode.NONE
                            touchState.target = null
                            gestureActive = false
                            if (wasTap) {
                                when (tapZone(event.x, view.width.toFloat())) {
                                    TapZone.LEFT -> {
                                        seekRelativeTo(exoPlayer, -SEEK_STEP_MS)
                                        gestureHint = "−5 с"
                                    }

                                    TapZone.RIGHT -> {
                                        seekRelativeTo(exoPlayer, SEEK_STEP_MS)
                                        gestureHint = "+5 с"
                                    }

                                    TapZone.CENTER -> {
                                        if (view.isControllerFullyVisible) {
                                            view.hideController()
                                        } else {
                                            view.showController()
                                        }
                                    }
                                }
                                true
                            } else {
                                consumed
                            }
                        }

                        else -> false
                    }
                }
            },
            onRelease = { view ->
                view.setOnTouchListener(null)
                view.player = null
            },
        )

        // Затемнення, яке плавно зникає разом із zoom під час входу в повний екран.
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - entryProgress.value) * 0.5f }
                    .background(Color.Black),
            )
        }

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

        gestureHint?.let { hint ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = hint,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
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

private const val TAP_TIMEOUT_MS = 300L

private enum class GestureMode { NONE, VERTICAL, PINCH }

private enum class GestureTarget { VOLUME, BRIGHTNESS }

/** Тримає стан жесту між перекомпозиціями та подіями MotionEvent. */
private class PlayerTouchState {
    var consuming: Boolean = false
    var mode: GestureMode = GestureMode.NONE
    var target: GestureTarget? = null
    var downX: Float = 0f
    var downY: Float = 0f
    var downTime: Long = 0L
    var moved: Boolean = false
    var startLevel: Float = 0f
    var lastPercent: Int = -1
}

private fun seekRelativeTo(player: ExoPlayer, deltaMs: Long) {
    player.seekTo(seekTarget(player.currentPosition, deltaMs, player.duration))
}

private fun currentVolume(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun applyVolume(audioManager: AudioManager, level: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (level.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
    if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != target) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}

private fun currentBrightness(context: Context, activity: Activity?): Float {
    val override = activity?.window?.attributes?.screenBrightness ?: -1f
    if (override >= 0f) return override.coerceIn(MIN_BRIGHTNESS, 1f)
    val system = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return (system / 255f).coerceIn(MIN_BRIGHTNESS, 1f)
}

private fun applyBrightness(activity: Activity?, level: Float) {
    val window = activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = level.coerceIn(MIN_BRIGHTNESS, 1f)
    window.attributes = attrs
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
