package com.ioi.youtube.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.SearchVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    searchVideos: SearchVideosUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<UiState<SearchResults>> = refreshTrigger
        .flatMapLatest {
            query
                .debounce(300)
                .flatMapLatest { current ->
                    if (current.isBlank()) {
                        flowOf(UiState.Success(SearchResults(current, emptyList())))
                    } else {
                        searchVideos(current)
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery.trim()
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
    }
}
