package com.ioi.youtube.data.repository

import android.util.Log
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IOITube"

/**
 * Кешуємо лише першу сторінку «Усі + Релевантність»: це єдиний режим,
 * який показується без мережі. Для теми/сортування кеш не читається й не пишеться.
 */
@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
) : FeedRepository {

    override fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> = flow {
        emit(UiState.Loading)
        val usesCache = topic == FeedTopic.ALL && sort == FeedSort.RELEVANCE
        val cached = if (usesCache) cachedVideoDao.getBySource(SOURCE_HOME) else emptyList()
        if (cached.isNotEmpty()) {
            emit(
                UiState.Success(
                    FeedPage(sortVideos(cached.map { it.toDomain() }, sort)),
                    fromCache = true,
                ),
            )
        }
        val fresh = try {
            remote.fetchFeedPage(topic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "feed page request failed", e)
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_FEED, e))
            return@flow
        }
        val sorted = sortVideos(fresh.map { it.toDomain() }, sort)
        if (usesCache) {
            runCatching {
                cachedVideoDao.replaceSource(
                    SOURCE_HOME,
                    sorted.map { it.toEntity(SOURCE_HOME, System.currentTimeMillis()) },
                )
            }.onFailure { Log.w(TAG, "feed cache write failed", it) }
        }
        emit(UiState.Success(FeedPage(sorted)))
    }.flowOn(Dispatchers.IO)

    override fun getFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val shorts = try {
            remote.fetchFeedShorts(count, excludeIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "feed shorts request failed", e)
            emit(UiState.Error(ERROR_LOAD_FEED_SHORTS, e))
            return@flow
        }
        emit(UiState.Success(shorts.map { it.toDomain() }))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_HOME = "home"
        const val ERROR_LOAD_FEED = "Не вдалося завантажити стрічку"
        const val ERROR_LOAD_FEED_SHORTS = "Не вдалося завантажити шортси"
    }
}

/** Сортування на клієнті: серверне сортування YouTube у 0.26.5 недоступне. */
internal fun sortVideos(videos: List<VideoSummary>, sort: FeedSort): List<VideoSummary> =
    when (sort) {
        FeedSort.RELEVANCE -> videos
        FeedSort.NEWEST -> videos.sortedByDescending { it.uploadedAt ?: Long.MIN_VALUE }
        FeedSort.POPULAR -> videos.sortedByDescending { it.viewCount }
    }
