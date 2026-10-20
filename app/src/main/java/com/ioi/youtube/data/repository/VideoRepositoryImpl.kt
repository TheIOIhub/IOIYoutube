package com.ioi.youtube.data.repository

import android.util.Log
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.mapper.toDetailsEntity
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Тег для logcat: фiльтр `adb logcat -s IOITube`. */
private const val TAG = "IOITube"

/**
 * Offline-first: спочатку кеш, потiм свiжi данi (спека §7).
 * Якщо мережа недоступна i кеш порожнiй — Error, iнакше показуємо кеш.
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
    private val cachedVideoDetailsDao: CachedVideoDetailsDao,
) : VideoRepository {

    override fun getHomeFeed(): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val cached = cachedVideoDao.getBySource(SOURCE_HOME)
        if (cached.isNotEmpty()) {
            emit(UiState.Success(cached.map { it.toDomain() }, fromCache = true))
        }
        val fresh = try {
            // Тимчасово, до Task 4: стара стрiчка = перша сторiнка теми «Усi».
            remote.fetchFeedPage(FeedTopic.ALL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "home feed request failed", e)
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_FEED, e))
            return@flow
        }
        val domain = fresh.map { it.toDomain() }
        runCatching {
            cachedVideoDao.replaceSource(
                SOURCE_HOME,
                fresh.map { it.toDomain().toEntity(SOURCE_HOME, System.currentTimeMillis()) },
            )
        }
        emit(UiState.Success(domain))
    }.flowOn(Dispatchers.IO)

    override fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>> = flow {
        emit(UiState.Loading)
        val cached = cachedVideoDetailsDao.getById(videoId)
        if (cached != null) {
            emit(UiState.Success(cached.toDomain(), fromCache = true))
        }
        val fresh = try {
            remote.fetchVideoDetails(videoId).toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "video details request failed", e)
            if (cached == null) emit(UiState.Error(ERROR_LOAD_DETAILS, e))
            return@flow
        }
        runCatching {
            cachedVideoDetailsDao.upsert(fresh.toDetailsEntity(System.currentTimeMillis()))
        }
        emit(UiState.Success(fresh))
    }.flowOn(Dispatchers.IO)

    override fun getVideoStream(videoId: String): Flow<UiState<VideoStream>> = flow {
        emit(UiState.Loading)
        try {
            emit(UiState.Success(remote.fetchVideoStream(videoId).toDomain()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "video stream request failed", e)
            emit(UiState.Error(ERROR_LOAD_STREAM, e))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_HOME = "home"
        const val ERROR_LOAD_FEED = "Не вдалося завантажити стрічку"
        const val ERROR_LOAD_DETAILS = "Не вдалося завантажити відео"
        const val ERROR_LOAD_STREAM = "Не вдалося отримати відеопотік"
    }
}
