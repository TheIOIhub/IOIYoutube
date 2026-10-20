package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFeedPageUseCase @Inject constructor(
    private val repository: FeedRepository,
) {
    operator fun invoke(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> =
        repository.getFeedPage(topic, sort)
}
