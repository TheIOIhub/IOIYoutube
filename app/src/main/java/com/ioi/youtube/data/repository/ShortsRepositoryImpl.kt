package com.ioi.youtube.data.repository

import android.util.Log
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.ShortsRepository
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
 * Стрiчка Shorts не кешується (як коментарi): манифести й вкладки живуть хвилини,
 * а свiжий список завжди рiзний.
 */
@Singleton
class ShortsRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
) : ShortsRepository {

    override fun getShorts(): Flow<UiState<List<VideoSummary>>> = shortsFlow { remote.fetchShorts() }

    override fun getMoreShorts(): Flow<UiState<List<VideoSummary>>> =
        shortsFlow { remote.fetchMoreShorts() }

    private fun shortsFlow(
        fetch: suspend () -> List<RawVideoSummary>,
    ): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val shorts = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "shorts request failed", e)
            emit(UiState.Error(ERROR_LOAD_SHORTS, e))
            return@flow
        }
        emit(UiState.Success(shorts.map { it.toDomain() }))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val ERROR_LOAD_SHORTS = "Не вдалося завантажити шортси"
    }
}
