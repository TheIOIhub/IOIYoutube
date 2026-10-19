package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.ShortsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMoreShortsUseCase @Inject constructor(
    private val shortsRepository: ShortsRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> = shortsRepository.getMoreShorts()
}
