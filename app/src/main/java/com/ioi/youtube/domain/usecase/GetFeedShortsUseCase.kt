package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFeedShortsUseCase @Inject constructor(
    private val repository: FeedRepository,
) {
    operator fun invoke(count: Int, excludeIds: Set<String>): Flow<UiState<List<VideoSummary>>> =
        repository.getFeedShorts(count, excludeIds)
}
