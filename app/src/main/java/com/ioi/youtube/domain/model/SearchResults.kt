package com.ioi.youtube.domain.model

data class SearchResults(
    val query: String,
    val videos: List<VideoSummary>,
)
