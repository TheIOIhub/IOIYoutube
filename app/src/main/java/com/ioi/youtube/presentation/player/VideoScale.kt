package com.ioi.youtube.presentation.player

import androidx.media3.ui.AspectRatioFrameLayout

enum class VideoScale { FIT, FILL }

internal fun resizeModeFor(scale: VideoScale): Int = when (scale) {
    VideoScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
