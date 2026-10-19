package com.ioi.youtube.presentation.shorts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetMoreShortsUseCase
import com.ioi.youtube.domain.usecase.GetShortsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.testutil.FakeShortsRepository
import com.ioi.youtube.testutil.FakeVideoRepository
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
class ShortsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val shorts = FakeShortsRepository()
    private val videos = FakeVideoRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ShortsViewModel(
        getShorts = GetShortsUseCase(shorts),
        getMoreShorts = GetMoreShortsUseCase(shorts),
        getVideoStream = GetVideoStreamUseCase(videos),
    )

    @Test
    fun `starts with loading then emits the feed`() = runTest(testDispatcher) {
        val feed = listOf(TestData.video("aaaaaaaaaaa"), TestData.video("bbbbbbbbbbb"))
        shorts.setShorts(UiState.Success(feed))

        createViewModel().uiState.test {
            assertThat(awaitItem().shorts).isEqualTo(UiState.Loading)
            assertThat(awaitItem().shorts).isEqualTo(UiState.Success(feed))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load stream fetches once per short and caches the result`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        videos.setStream(
            "aaaaaaaaaaa",
            UiState.Success(VideoStream("https://v/x.mp4", "720p", "MPEG-4")),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()
            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()

            assertThat(videos.streamCalls).isEqualTo(1)
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry stream drops the cached error and fetches again`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        videos.setStream("aaaaaaaaaaa", UiState.Error("Не вдалося отримати відеопотiк"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Error::class.java)

            videos.setStream(
                "aaaaaaaaaaa",
                UiState.Success(VideoStream("https://v/x.mp4", "720p", "MPEG-4")),
            )
            viewModel.retryStream("aaaaaaaaaaa")
            advanceUntilIdle()

            assertThat(videos.streamCalls).isEqualTo(2)
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more appends only previously unseen shorts`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        val second = TestData.video("bbbbbbbbbbb")
        val third = TestData.video("ccccccccccc")
        shorts.setShorts(UiState.Success(listOf(first, second)))
        shorts.setMoreShorts(UiState.Success(listOf(second, third)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second)))

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second, third)))
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more failure keeps the current feed`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        shorts.setShorts(UiState.Success(listOf(first)))
        shorts.setMoreShorts(UiState.Error("Не вдалося завантажити шортси"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts).isEqualTo(UiState.Success(listOf(first)))
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry reloads the feed`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(shorts.shortsCalls).isEqualTo(1)

            viewModel.retry()
            advanceUntilIdle()

            assertThat(shorts.shortsCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
