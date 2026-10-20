package com.ioi.youtube.presentation.shorts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.usecase.GetMoreShortsUseCase
import com.ioi.youtube.domain.usecase.GetShortsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsUiState(
    val shorts: UiState<List<VideoSummary>> = UiState.Loading,
    val streams: Map<String, UiState<VideoStream>> = emptyMap(),
    val loadingMore: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShortsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getShorts: GetShortsUseCase,
    private val getMoreShorts: GetMoreShortsUseCase,
    private val getVideoStream: GetVideoStreamUseCase,
) : ViewModel() {

    private val requestedVideoId: String? =
        savedStateHandle.get<String>(Routes.ARG_VIDEO_ID)?.takeIf { it.isNotBlank() }

    private val refreshTrigger = MutableStateFlow(0)
    /**
     * Id шортсів поточної стрiчки: набiр замiняється кожен раз, коли
     * [GetShortsUseCase] повертає новi данi (напр. після `retry()`).
     *
     * Замiняємо, а не додаємо: дедуплікація в [loadMore] порiвнює «more»-сторiнку
     * з поточною стрiчкою, а не з усім, що колись вантажилося.
     */
    private val knownIds: MutableSet<String> = mutableSetOf()

    /** Єдине джерело iстини стану екрана; споживається UI напряму. */
    val uiState = MutableStateFlow(ShortsUiState())

    val state: StateFlow<ShortsUiState> = uiState

    init {
        viewModelScope.launch {
            refreshTrigger.flatMapLatest { getShorts() }.collect { state ->
                if (state is UiState.Success) {
                    knownIds.clear()
                    knownIds.addAll(state.data.map { it.id })
                }
                uiState.value = ShortsUiState(
                    shorts = when (state) {
                        is UiState.Success -> UiState.Success(
                            withRequestedFirst(state.data),
                            state.fromCache,
                        )

                        else -> state
                    },
                )
            }
        }
    }

    /** Передане зі стрічки відео стає першою сторінкою пейджера. */
    private fun withRequestedFirst(shorts: List<VideoSummary>): List<VideoSummary> {
        val id = requestedVideoId ?: return shorts
        val existing = shorts.firstOrNull { it.id == id }
        val rest = shorts.filterNot { it.id == id }
        val first = existing ?: VideoSummary(
            id = id,
            title = "",
            uploaderName = "",
            thumbnailUrl = "",
            durationSeconds = 0,
            viewCount = 0,
            uploadDate = null,
        )
        return listOf(first) + rest
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
        uiState.update { it.copy(shorts = UiState.Loading) }
    }

    /** Лiниво вантажить потiк шортса; повторний виклик для того ж id нiчого не робить. */
    fun loadStream(videoId: String) {
        if (videoId.isBlank() || uiState.value.streams.containsKey(videoId)) return
        uiState.update { it.copy(streams = uiState.value.streams + (videoId to UiState.Loading)) }
        viewModelScope.launch {
            getVideoStream(videoId).collect { state ->
                uiState.update { it.copy(streams = uiState.value.streams + (videoId to state)) }
            }
        }
    }

    fun retryStream(videoId: String) {
        uiState.update { it.copy(streams = uiState.value.streams - videoId) }
        loadStream(videoId)
    }

    /**
     * Новi шортси з iншої теми; помилка «гортання далi» не руйнує поточну стрiчку.
     *
     * [take] обмежує передплату першим елемiнтом: так збирателю гарантовано вiдбудеться
     * (як отримано данi, так i якщо потiк завершився), а iнакше корутина бiла б зависла на
     * каналi [ShortsRepository.getMoreShorts()] i ніколи не скинула б [ShortsUiState.loadingMore].
     */
    fun loadMore() {
        if (uiState.value.loadingMore) return
        uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            getMoreShorts()
                .take(1)
                .collect { state ->
                    if (state is UiState.Success) {
                        val fresh = state.data.filterNot { it.id in knownIds }
                        if (fresh.isNotEmpty()) {
                            knownIds.addAll(fresh.map { it.id })
                            uiState.update { current ->
                                val existing = (current.shorts as? UiState.Success)?.data.orEmpty()
                                current.copy(shorts = UiState.Success(existing + fresh))
                            }
                        }
                    }
                    uiState.update { it.copy(loadingMore = false) }
                }
        }
    }
}
