package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchVideosUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    operator fun invoke(query: String): Flow<UiState<SearchResults>> =
        searchRepository.search(query)
}
