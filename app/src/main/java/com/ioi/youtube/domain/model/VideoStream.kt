package com.ioi.youtube.domain.model

data class VideoVariant(
    val url: String,
    val height: Int,
    val bitrate: Int,
)

data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val url: String,
    val bitrate: Int,
    val original: Boolean,
)

data class VideoStream(
    val url: String,
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,
    val videoOnly: List<VideoVariant> = emptyList(),
    val audioTracks: List<AudioTrackOption> = emptyList(),
)
