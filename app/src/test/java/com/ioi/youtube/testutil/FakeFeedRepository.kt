package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeFeedRepository : FeedRepository {

    private val pages = ArrayDeque<UiState<FeedPage>>()
    private val shortsPages = ArrayDeque<UiState<List<VideoSummary>>>()

    var feedCalls = 0
        private set
    var shortsCalls = 0
        private set
    var lastTopic: FeedTopic? = null
        private set
    var lastSort: FeedSort? = null
        private set
    var lastShortsCount = 0
        private set
    var lastShortExcludeIds: Set<String> = emptySet()
        private set

    fun enqueuePage(state: UiState<FeedPage>) {
        pages.addLast(state)
    }

    fun enqueueShorts(state: UiState<List<VideoSummary>>) {
        shortsPages.addLast(state)
    }

    override fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> {
        feedCalls++
        lastTopic = topic
        lastSort = sort
        val state = if (pages.isEmpty()) {
            UiState.Success(FeedPage(emptyList(), exhausted = true))
        } else {
            pages.removeFirst()
        }
        return flowOf(state)
    }

    override fun getFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): Flow<UiState<List<VideoSummary>>> {
        shortsCalls++
        lastShortsCount = count
        lastShortExcludeIds = excludeIds
        val state = if (shortsPages.isEmpty()) UiState.Success(emptyList()) else shortsPages.removeFirst()
        return flowOf(state)
    }
}
