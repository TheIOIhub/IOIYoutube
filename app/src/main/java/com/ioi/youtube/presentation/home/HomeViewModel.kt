package com.ioi.youtube.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.usecase.GetFeedPageUseCase
import com.ioi.youtube.domain.usecase.GetFeedShortsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val topic: FeedTopic = FeedTopic.ALL,
    val sort: FeedSort = FeedSort.RELEVANCE,
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val exhausted: Boolean = false,
    val fromCache: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getFeedPage: GetFeedPageUseCase,
    private val getFeedShorts: GetFeedShortsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val shownVideoIds = mutableSetOf<String>()
    private val shownShortIds = mutableSetOf<String>()
    private var videosSinceBlock = 0
    private var loadJob: Job? = null

    init {
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun selectTopic(topic: FeedTopic) {
        if (topic == _uiState.value.topic) return
        _uiState.update { it.copy(topic = topic) }
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun selectSort(sort: FeedSort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun retry() = loadFirstPage(clear = true, freshOnly = false)

    /** Pull-to-refresh: список не очищаємо, кеш не показуємо. */
    fun refresh() = loadFirstPage(clear = false, freshOnly = true)

    private fun loadFirstPage(clear: Boolean, freshOnly: Boolean) {
        loadJob?.cancel()
        shownVideoIds.clear()
        shownShortIds.clear()
        videosSinceBlock = 0
        _uiState.update {
            it.copy(
                items = if (clear) emptyList() else it.items,
                loading = true,
                loadingMore = false,
                error = null,
                loadMoreError = null,
                exhausted = false,
                fromCache = false,
            )
        }
        val topic = _uiState.value.topic
        val sort = _uiState.value.sort
        loadJob = viewModelScope.launch {
            when (val state = awaitPage(topic, sort, freshOnly)) {
                is UiState.Error -> _uiState.update {
                    it.copy(loading = false, error = state.message)
                }

                is UiState.Success -> {
                    val fresh = state.data.videos.filterNot { it.id in shownVideoIds }
                    shownVideoIds += fresh.map { it.id }
                    _uiState.update {
                        it.copy(
                            items = appendVideos(emptyList(), fresh),
                            loading = false,
                            error = null,
                            fromCache = state.fromCache,
                        )
                    }
                    loadShortsForNewBlocks()
                }

                UiState.Loading -> Unit
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.exhausted || state.items.isEmpty()) return
        _uiState.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            repeat(MAX_EMPTY_ATTEMPTS) {
                when (val page = awaitPage(state.topic, state.sort, freshOnly = true)) {
                    is UiState.Error -> {
                        _uiState.update { it.copy(loadingMore = false, loadMoreError = page.message) }
                        return@launch
                    }

                    is UiState.Success -> {
                        val fresh = page.data.videos.filterNot { it.id in shownVideoIds }
                        if (fresh.isNotEmpty()) {
                            shownVideoIds += fresh.map { it.id }
                            _uiState.update { current ->
                                current.copy(
                                    items = appendVideos(current.items, fresh),
                                    loadingMore = false,
                                )
                            }
                            loadShortsForNewBlocks()
                            return@launch
                        }
                    }

                    UiState.Loading -> Unit
                }
            }
            _uiState.update { it.copy(loadingMore = false, exhausted = true) }
        }
    }

    /**
     * Бере перший «корисний» стан: для refresh — не кеш, для решти — кеш теж ок.
     */
    private suspend fun awaitPage(
        topic: FeedTopic,
        sort: FeedSort,
        freshOnly: Boolean,
    ): UiState<FeedPage> = getFeedPage(topic, sort)
        .filter { state ->
            state !is UiState.Loading &&
                (!freshOnly || (state as? UiState.Success)?.fromCache != true)
        }
        .first()

    private fun appendVideos(target: List<FeedItem>, videos: List<VideoSummary>): List<FeedItem> {
        val items = target.toMutableList()
        videos.forEach { video ->
            items += FeedItem.Video(video)
            videosSinceBlock++
            if (videosSinceBlock % VIDEOS_PER_BLOCK == 0) {
                items += FeedItem.ShortsBlock(
                    id = "shorts-$videosSinceBlock",
                    shorts = emptyList(),
                )
            }
        }
        return items
    }

    /**
     * Один запит шортсів на сторінку: `2 × кількість нових блоків`.
     * Порожні блоки (не вистачило шортсів або помилка) прибираємо, щоб не було дір.
     */
    private suspend fun loadShortsForNewBlocks() {
        val blocks = _uiState.value.items
            .filterIsInstance<FeedItem.ShortsBlock>()
            .filter { it.shorts.isEmpty() }
        if (blocks.isEmpty()) return

        val state = getFeedShorts(
            blocks.size * SHORTS_PER_BLOCK,
            shownShortIds.toSet(),
        ).filter { it is UiState.Success || it is UiState.Error }.first()

        if (state is UiState.Error) {
            _uiState.update { current ->
                current.copy(
                    items = current.items.filterNot {
                        it is FeedItem.ShortsBlock && it.shorts.isEmpty()
                    },
                )
            }
            return
        }

        val shorts = (state as UiState.Success<List<VideoSummary>>).data
        shownShortIds += shorts.map { it.id }
        val filled = mutableMapOf<String, List<VideoSummary>>()
        var cursor = 0
        blocks.forEach { block ->
            val slice = shorts.drop(cursor).take(SHORTS_PER_BLOCK)
            cursor += slice.size
            if (slice.isNotEmpty()) filled[block.id] = slice
        }

        _uiState.update { current ->
            current.copy(
                items = current.items.mapNotNull { item ->
                    when {
                        item is FeedItem.ShortsBlock && filled.containsKey(item.id) ->
                            item.copy(shorts = filled.getValue(item.id))

                        item is FeedItem.ShortsBlock && item.shorts.isEmpty() -> null
                        else -> item
                    }
                },
            )
        }
    }

    private companion object {
        const val VIDEOS_PER_BLOCK = 4
        const val SHORTS_PER_BLOCK = 2
        const val MAX_EMPTY_ATTEMPTS = 3
    }
}
