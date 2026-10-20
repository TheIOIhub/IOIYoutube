package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>>
    fun getVideoStream(videoId: String): Flow<UiState<VideoStream>>
}
