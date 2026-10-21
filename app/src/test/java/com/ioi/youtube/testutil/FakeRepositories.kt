package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.CommentRepository
import com.ioi.youtube.domain.repository.ShortsRepository
import com.ioi.youtube.domain.repository.SearchRepository
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVideoRepository : VideoRepository {

    private val details = mutableMapOf<String, MutableStateFlow<UiState<VideoDetails>>>()
    private val streams = mutableMapOf<String, MutableStateFlow<UiState<VideoStream>>>()

    var detailsCalls = 0
        private set
    var streamCalls = 0
        private set

    fun setDetails(videoId: String, state: UiState<VideoDetails>) {
        details.getOrPut(videoId) { MutableStateFlow(state) }.value = state
    }

    fun setStream(videoId: String, state: UiState<VideoStream>) {
        streams.getOrPut(videoId) { MutableStateFlow(state) }.value = state
    }

    override fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>> {
        detailsCalls++
        return details.getOrPut(videoId) { MutableStateFlow(UiState.Loading) }
    }

    override fun getVideoStream(videoId: String): Flow<UiState<VideoStream>> {
        streamCalls++
        return streams.getOrPut(videoId) { MutableStateFlow(UiState.Loading) }
    }
}

class FakeSearchRepository : SearchRepository {

    private val results = MutableStateFlow<UiState<SearchResults>>(UiState.Loading)

    var searchCalls = 0
        private set
    var lastQuery: String? = null
        private set

    fun setResults(state: UiState<SearchResults>) {
        results.value = state
    }

    override fun search(query: String): Flow<UiState<SearchResults>> {
        searchCalls++
        lastQuery = query
        return results
    }
}

class FakeCommentRepository : CommentRepository {

    private val comments = MutableStateFlow<UiState<List<Comment>>>(UiState.Loading)

    var commentsCalls = 0
        private set

    fun setComments(state: UiState<List<Comment>>) {
        comments.value = state
    }

    override fun getComments(videoId: String): Flow<UiState<List<Comment>>> {
        commentsCalls++
        return comments
    }
}

class FakeShortsRepository : ShortsRepository {

    private val shorts = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    private val moreShorts = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)

    /** Перекриває [moreShorts], коли тесту потрiбен холодний потiк iз кiлькома емiсiями. */
    var moreShortsFlow: Flow<UiState<List<VideoSummary>>>? = null

    var shortsCalls = 0
        private set
    var moreShortsCalls = 0
        private set

    fun setShorts(state: UiState<List<VideoSummary>>) {
        shorts.value = state
    }

    fun setMoreShorts(state: UiState<List<VideoSummary>>) {
        moreShorts.value = state
    }

    override fun getShorts(): Flow<UiState<List<VideoSummary>>> {
        shortsCalls++
        return shorts
    }

    override fun getMoreShorts(): Flow<UiState<List<VideoSummary>>> {
        moreShortsCalls++
        return moreShortsFlow ?: moreShorts
    }
}
