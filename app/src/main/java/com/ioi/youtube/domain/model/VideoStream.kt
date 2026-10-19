package com.ioi.youtube.domain.model

data class VideoStream(
    val url: String,
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,
)
