package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun search(query: String): Flow<UiState<SearchResults>>
}
