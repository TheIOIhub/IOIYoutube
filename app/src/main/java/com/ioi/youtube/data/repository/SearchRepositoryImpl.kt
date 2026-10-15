package com.ioi.youtube.data.repository

import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
) : SearchRepository {

    override fun search(query: String): Flow<UiState<SearchResults>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(UiState.Success(SearchResults(query = query, videos = emptyList())))
            return@flow
        }
        emit(UiState.Loading)
        val key = searchKey(trimmed)
        val cached = cachedVideoDao.getBySource(key)
        if (cached.isNotEmpty()) {
            emit(
                UiState.Success(
                    data = SearchResults(trimmed, cached.map { it.toDomain() }),
                    fromCache = true,
                ),
            )
        }
        val fresh = try {
            remote.search(trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_SEARCH, e))
            return@flow
        }
        runCatching {
            cachedVideoDao.replaceSource(
                key,
                fresh.map { it.toDomain().toEntity(key, System.currentTimeMillis()) },
            )
        }
        emit(UiState.Success(SearchResults(trimmed, fresh.map { it.toDomain() })))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_PREFIX = "search:"
        const val ERROR_LOAD_SEARCH = "Не вдалося виконати пошук"

        fun searchKey(query: String) = "$SOURCE_PREFIX$query"
    }
}
