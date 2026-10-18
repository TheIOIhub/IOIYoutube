package com.ioi.youtube.data.repository

import com.ioi.youtube.data.mapper.toDomain
import android.util.Log
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.CommentRepository
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
 * Коментарі не кешуються — завжди мережа (спека §5.4 кешує стрічку та метадані, не коментарі).
 */
@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
) : CommentRepository {

    override fun getComments(videoId: String): Flow<UiState<List<Comment>>> = flow {
        emit(UiState.Loading)
        val comments = try {
            remote.fetchComments(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "comments request failed", e)
            emit(UiState.Error(ERROR_LOAD_COMMENTS, e))
            return@flow
        }
        emit(UiState.Success(comments.map { it.toDomain() }))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val ERROR_LOAD_COMMENTS = "Не вдалося завантажити коментарі"
    }
}
