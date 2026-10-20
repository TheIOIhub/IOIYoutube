package com.ioi.youtube.presentation.home

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.GetFeedPageUseCase
import com.ioi.youtube.domain.usecase.GetFeedShortsUseCase
import com.ioi.youtube.testutil.FakeFeedRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeFeedRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getFeedPage = GetFeedPageUseCase(repository),
        getFeedShorts = GetFeedShortsUseCase(repository),
    )

    private fun videos(count: Int) = (1..count).map { index ->
        TestData.video(id = "video0000$index")
    }

    private fun shorts(count: Int) = (1..count).map { index ->
        TestData.video(id = "short0000$index")
    }

    @Test
    fun `page adds a shorts block after every fourth video and fills two shorts`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(9))))
            repository.enqueueShorts(UiState.Success(shorts(4)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val items = viewModel.uiState.value.items
            assertThat(items.filterIsInstance<FeedItem.Video>()).hasSize(9)
            assertThat(items.filterIsInstance<FeedItem.ShortsBlock>()).hasSize(2)
            assertThat(items[4]).isInstanceOf(FeedItem.ShortsBlock::class.java)
            assertThat(items[9]).isInstanceOf(FeedItem.ShortsBlock::class.java)
            items.filterIsInstance<FeedItem.ShortsBlock>().forEach {
                assertThat(it.shorts).hasSize(2)
            }
            assertThat(repository.lastShortsCount).isEqualTo(4)
        }

    @Test
    fun `load more appends fresh videos and another block`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Success(FeedPage(videos(8).drop(4))))
        repository.enqueueShorts(UiState.Success(shorts(4)))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertThat(items.filterIsInstance<FeedItem.Video>()).hasSize(8)
        assertThat(items.filterIsInstance<FeedItem.ShortsBlock>()).hasSize(2)
        assertThat(viewModel.uiState.value.loadingMore).isFalse()
        assertThat(repository.feedCalls).isEqualTo(2)
    }

    @Test
    fun `load more retries duplicate-only pages three times then marks exhausted`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
            repository.enqueueShorts(UiState.Success(shorts(2)))
            repeat(3) { repository.enqueuePage(UiState.Success(FeedPage(videos(4)))) }

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.exhausted).isTrue()
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
            assertThat(repository.feedCalls).isEqualTo(4)
        }

    @Test
    fun `load more error keeps the feed and exposes retry`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Error("Не вдалося завантажити стрічку"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
        assertThat(viewModel.uiState.value.loadMoreError).isEqualTo("Не вдалося завантажити стрічку")
        assertThat(viewModel.uiState.value.loadingMore).isFalse()
    }

    @Test
    fun `topic change resets items and loads the new topic`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectTopic(FeedTopic.GAMES)
        advanceUntilIdle()

        assertThat(repository.lastTopic).isEqualTo(FeedTopic.GAMES)
        assertThat(viewModel.uiState.value.topic).isEqualTo(FeedTopic.GAMES)
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun `sort change reloads and keeps the selected topic`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectTopic(FeedTopic.TECH)
        advanceUntilIdle()
        viewModel.selectSort(FeedSort.POPULAR)
        advanceUntilIdle()

        assertThat(repository.lastSort).isEqualTo(FeedSort.POPULAR)
        assertThat(repository.lastTopic).isEqualTo(FeedTopic.TECH)
        assertThat(viewModel.uiState.value.sort).isEqualTo(FeedSort.POPULAR)
    }

    @Test
    fun `initial error is exposed with the message`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Error("Не вдалося завантажити стрічку"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Не вдалося завантажити стрічку")
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `block without shorts is removed when the shorts request fails`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
            repository.enqueueShorts(UiState.Error("Не вдалося завантажити шортси"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.ShortsBlock>()).isEmpty()
            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
        }
}
