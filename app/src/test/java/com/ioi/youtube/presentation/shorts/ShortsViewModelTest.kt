package com.ioi.youtube.presentation.shorts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetMoreShortsUseCase
import com.ioi.youtube.domain.usecase.GetShortsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.presentation.navigation.Routes
import com.ioi.youtube.testutil.FakeShortsRepository
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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

    private fun createViewModel(videoId: String? = null) = ShortsViewModel(
        savedStateHandle = SavedStateHandle(
            videoId?.let { mapOf(Routes.ARG_VIDEO_ID to it) } ?: emptyMap(),
        ),
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
    fun `load more skips the repository loading emission`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        val second = TestData.video("bbbbbbbbbbb")
        shorts.setShorts(UiState.Success(listOf(first)))
        // Реальний ShortsRepositoryImpl спершу емiтить Loading, потiм данi.
        shorts.moreShortsFlow = flow {
            emit(UiState.Loading)
            emit(UiState.Success(listOf(second)))
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second)))
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
    fun `load more still appends a new short after a feed reload`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        val second = TestData.video("bbbbbbbbbbb")
        val third = TestData.video("ccccccccccc")
        val fourth = TestData.video("ddddddddddd")
        val fifth = TestData.video("eeeeeeeeeee")
        shorts.setShorts(UiState.Success(listOf(first, second)))
        shorts.setMoreShorts(UiState.Success(listOf(third, fourth)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second)))

            viewModel.loadMore()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second, third, fourth)))

            // Повторне завантаження стрiчки: новий набiр не мiстить ранiше
            // довантаженi «more»-елементи, а «more»-сторiнка їх повертає.
            shorts.setShorts(UiState.Success(listOf(first, second, third)))
            shorts.setMoreShorts(UiState.Success(listOf(fourth, fifth)))
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second, third, fourth, fifth)))
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

    @Test
    fun `requested short is placed first even when absent from the feed`() =
        runTest(testDispatcher) {
            shorts.setShorts(UiState.Success(listOf(TestData.video(id = "feedvideo01"))))

            val viewModel = createViewModel(videoId = "requested01")
            advanceUntilIdle()

            val data = (viewModel.uiState.value.shorts as UiState.Success).data
            assertThat(data.first().id).isEqualTo("requested01")
            assertThat(data.map { it.id }).contains("feedvideo01")
        }

    @Test
    fun `requested short stays first when it is already in the feed`() =
        runTest(testDispatcher) {
            shorts.setShorts(
                UiState.Success(
                    listOf(
                        TestData.video(id = "feedvideo01"),
                        TestData.video(id = "requested01"),
                    ),
                ),
            )

            val viewModel = createViewModel(videoId = "requested01")
            advanceUntilIdle()

            val data = (viewModel.uiState.value.shorts as UiState.Success).data
            assertThat(data.map { it.id }).containsExactly("requested01", "feedvideo01").inOrder()
        }
}
