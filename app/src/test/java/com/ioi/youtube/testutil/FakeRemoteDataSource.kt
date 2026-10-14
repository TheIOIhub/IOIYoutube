package com.ioi.youtube.testutil

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource

class FakeRemoteDataSource : YouTubeRemoteDataSource {

    var feed: List<RawVideoSummary> = emptyList()
    var searchResults: List<RawVideoSummary> = emptyList()
    var details: RawVideoDetails? = null
    var comments: List<RawComment> = emptyList()
    var stream: RawVideoStream? = null

    var feedError: Exception? = null
    var searchError: Exception? = null
    var detailsError: Exception? = null
    var commentsError: Exception? = null
    var streamError: Exception? = null

    var feedCalls = 0
        private set
    var searchCalls = 0
        private set
    var detailsCalls = 0
        private set
    var commentsCalls = 0
        private set
    var streamCalls = 0
        private set

    override suspend fun fetchHomeFeed(): List<RawVideoSummary> {
        feedCalls++
        feedError?.let { throw it }
        return feed
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
}
