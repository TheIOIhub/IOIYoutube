package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getHomeFeed(): Flow<UiState<List<VideoSummary>>>
    fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>>
    fun getVideoStream(videoId: String): Flow<UiState<VideoStream>>
}
