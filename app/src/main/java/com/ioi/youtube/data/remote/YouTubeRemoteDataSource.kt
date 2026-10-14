package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary

/**
 * Єдине джерело даних із YouTube. Розриває залежність UI/репозиторіїв від NewPipe,
 * щоб тести використовували фейк (див. Task 2.5).
 * Усі методи блокують потік — викликати лише з Dispatchers.IO.
 */
interface YouTubeRemoteDataSource {
    suspend fun fetchHomeFeed(): List<RawVideoSummary>
    suspend fun search(query: String): List<RawVideoSummary>
    suspend fun fetchVideoDetails(videoId: String): RawVideoDetails
    suspend fun fetchComments(videoId: String): List<RawComment>
    suspend fun fetchVideoStream(videoId: String): RawVideoStream
}
