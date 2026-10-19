package com.ioi.youtube.presentation.player

import com.ioi.youtube.domain.model.VideoStream

/** Що саме грає плеєр: HLS-манифест (усі якості) або progressive-потік. */
data class PlayerSource(
    val url: String,
    val isHls: Boolean,
    val fallbackUrl: String?,
)

/**
 * Воліємо HLS: у ньому всі якості й адаптивне перемикання.
 * Progressive тримаємо як запасний варіант на випадок, коли HLS не запуститься.
 */
fun VideoStream?.toPlayerSource(): PlayerSource? {
    val stream = this ?: return null
    val hls = stream.hlsUrl?.takeIf { it.isNotBlank() }
    val progressive = stream.url.takeIf { it.isNotBlank() }
    return when {
        hls != null -> PlayerSource(url = hls, isHls = true, fallbackUrl = progressive)
        progressive != null -> PlayerSource(url = progressive, isHls = false, fallbackUrl = null)
        else -> null
    }
}
