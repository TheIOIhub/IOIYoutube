package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>>

    fun getFeedShorts(count: Int, excludeIds: Set<String>): Flow<UiState<List<VideoSummary>>>
}
