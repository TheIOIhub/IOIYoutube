package com.ioi.youtube.testutil

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.FeedTopic

class FakeRemoteDataSource : YouTubeRemoteDataSource {

    var feed: List<RawVideoSummary> = emptyList()
    val feedByTopic = mutableMapOf<FeedTopic, List<RawVideoSummary>>()
    var searchResults: List<RawVideoSummary> = emptyList()
    var details: RawVideoDetails? = null
    var comments: List<RawComment> = emptyList()
    var stream: RawVideoStream? = null

    var feedError: Exception? = null
    var feedShorts: List<RawVideoSummary> = emptyList()
    var feedShortsError: Exception? = null
    var searchError: Exception? = null
    var detailsError: Exception? = null
    var commentsError: Exception? = null
    var streamError: Exception? = null

    var feedCalls = 0
        private set
    var feedShortsCalls = 0
        private set
    var lastFeedTopic: FeedTopic? = null
        private set
    var lastShortExcludeIds: Set<String> = emptySet()
        private set
    var searchCalls = 0
        private set
    var detailsCalls = 0
        private set
    var commentsCalls = 0
        private set
    var streamCalls = 0
        private set

    override suspend fun fetchFeedPage(topic: FeedTopic): List<RawVideoSummary> {
        feedCalls++
        lastFeedTopic = topic
        feedError?.let { throw it }
        return feedByTopic[topic] ?: feed
    }

    override suspend fun fetchFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): List<RawVideoSummary> {
        feedShortsCalls++
        lastShortExcludeIds = excludeIds
        feedShortsError?.let { throw it }
        return feedShorts.filterNot { it.id in excludeIds }.take(count)
    }

    override suspend fun search(query: String): List<RawVideoSummary> {
        searchCalls++
        searchError?.let { throw it }
        return searchResults
    }

    override suspend fun fetchVideoDetails(videoId: String): RawVideoDetails {
        detailsCalls++
        detailsError?.let { throw it }
        return requireNotNull(details) { "FakeRemoteDataSource.details is not set" }
    }

    override suspend fun fetchComments(videoId: String): List<RawComment> {
        commentsCalls++
        commentsError?.let { throw it }
        return comments
    }

    override suspend fun fetchVideoStream(videoId: String): RawVideoStream {
        streamCalls++
        streamError?.let { throw it }
        return requireNotNull(stream) { "FakeRemoteDataSource.stream is not set" }
    }

    var shorts: List<RawVideoSummary> = emptyList()
    var moreShorts: List<RawVideoSummary> = emptyList()
    var shortsError: Exception? = null
    var moreShortsError: Exception? = null

    var shortsCalls = 0
        private set
    var moreShortsCalls = 0
        private set

    override suspend fun fetchShorts(): List<RawVideoSummary> {
        shortsCalls++
        shortsError?.let { throw it }
        return shorts
    }

    override suspend fun fetchMoreShorts(): List<RawVideoSummary> {
        moreShortsCalls++
        moreShortsError?.let { throw it }
        return moreShorts
    }
}
