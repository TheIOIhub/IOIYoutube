package com.ioi.youtube.presentation.player

import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant

sealed interface PlaybackPlan {
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackPlan
    data class Progressive(val url: String) : PlaybackPlan
    data class Hls(val url: String) : PlaybackPlan
}

/**
 * Пріоритет: merged (обрана якість + обрана мова) → progressive (муксований) → HLS.
 * `auto = true` — пункт «Авто (HLS)»: HLS першим, progressive запасним.
 */
fun buildPlaybackPlan(
    stream: VideoStream?,
    video: VideoVariant?,
    audio: AudioTrackOption?,
    auto: Boolean = false,
): PlaybackPlan? {
    if (stream == null) return null
    if (!auto && video != null && audio != null &&
        video.url.isNotBlank() && audio.url.isNotBlank()
    ) {
        return PlaybackPlan.Merged(video.url, audio.url)
    }
    if (auto) {
        stream.hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Hls(it) }
    }
    stream.url.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Progressive(it) }
    stream.hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Hls(it) }
    return null
}
