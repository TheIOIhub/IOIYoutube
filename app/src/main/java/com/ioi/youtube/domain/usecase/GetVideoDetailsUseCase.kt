package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideoDetailsUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    operator fun invoke(videoId: String): Flow<UiState<VideoDetails>> =
        videoRepository.getVideoDetails(videoId)
}
