package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.FeedTopic

/**
 * Єдине джерело даних із YouTube. Розриває залежність UI/репозиторіїв від NewPipe,
 * щоб тести використовували фейк (див. Task 2.5).
 * Усі методи блокують потік — викликати лише з Dispatchers.IO.
 */
interface YouTubeRemoteDataSource {
    /** Одна сторінка стрічки для теми (для `ALL` — суміш двох тем). */
    suspend fun fetchFeedPage(topic: FeedTopic): List<RawVideoSummary>

    /** До `count` шортсів, максимум один з каналу, без `excludeIds`. */
    suspend fun fetchFeedShorts(count: Int, excludeIds: Set<String>): List<RawVideoSummary>

    suspend fun search(query: String): List<RawVideoSummary>
    suspend fun fetchVideoDetails(videoId: String): RawVideoDetails
    suspend fun fetchComments(videoId: String): List<RawComment>
    suspend fun fetchVideoStream(videoId: String): RawVideoStream

    /**
     * Стрiчка Shorts: у YouTube немає анонiмної стрiчки шортсiв, тому збираємо її
     * з вкладок Shorts каналiв, якi трапились у стрiчцi «Головна».
     */
    suspend fun fetchShorts(): List<RawVideoSummary>

    /** Ще шортси для «гортати далi»: канали з випадкової теми. */
    suspend fun fetchMoreShorts(): List<RawVideoSummary>
}
