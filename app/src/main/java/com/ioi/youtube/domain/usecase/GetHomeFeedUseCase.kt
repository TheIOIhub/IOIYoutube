package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHomeFeedUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> =
        videoRepository.getHomeFeed()
}
