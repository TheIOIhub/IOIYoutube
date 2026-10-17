package com.ioi.youtube.presentation.player

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetCommentsUseCase
import com.ioi.youtube.domain.usecase.GetVideoDetailsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.testutil.FakeCommentRepository
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
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val videoRepository = FakeVideoRepository()
    private val commentRepository = FakeCommentRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(videoId: String = "dQw4w9WgXcQ") = PlayerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("videoId" to videoId)),
        getVideoDetails = GetVideoDetailsUseCase(videoRepository),
        getVideoStream = GetVideoStreamUseCase(videoRepository),
        getComments = GetCommentsUseCase(commentRepository),
    )

    @Test
    fun `initial state has all three sections loading`() {
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value).isEqualTo(PlayerUiState())
        assertThat(viewModel.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `combines details, stream and comments`() = runTest(testDispatcher) {
        videoRepository.setDetails("dQw4w9WgXcQ", UiState.Success(TestData.details("dQw4w9WgXcQ")))
        videoRepository.setStream("dQw4w9WgXcQ", UiState.Success(VideoStream("https://v/1", "720p", "MPEG-4")))
        commentRepository.setComments(UiState.Success(TestData.comments()))

        createViewModel().uiState.test {
            awaitItem() // stateIn initialValue: all Loading
            val state = awaitItem()
            assertThat((state.video as UiState.Success).data.title).isEqualTo("Video dQw4w9WgXcQ")
            assertThat((state.stream as UiState.Success).data.url).isEqualTo("https://v/1")
            assertThat((state.comments as UiState.Success).data).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `comments error does not affect details and stream`() = runTest(testDispatcher) {
        videoRepository.setDetails("dQw4w9WgXcQ", UiState.Success(TestData.details("dQw4w9WgXcQ")))
        videoRepository.setStream("dQw4w9WgXcQ", UiState.Error("Не вдалося отримати відеопотік"))
        commentRepository.setComments(UiState.Error("Не вдалося завантажити коментарі"))

        createViewModel().uiState.test {
            awaitItem() // stateIn initialValue: all Loading
            val state = awaitItem()
            assertThat(state.video).isInstanceOf(UiState.Success::class.java)
            assertThat((state.stream as UiState.Error).message).isEqualTo("Не вдалося отримати відеопотік")
            assertThat((state.comments as UiState.Error).message).isEqualTo("Не вдалося завантажити коментарі")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry restarts all three flows`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(videoRepository.detailsCalls).isEqualTo(1)
            assertThat(videoRepository.streamCalls).isEqualTo(1)
            assertThat(commentRepository.commentsCalls).isEqualTo(1)

            viewModel.retry()
            advanceUntilIdle()

            assertThat(videoRepository.detailsCalls).isEqualTo(2)
            assertThat(videoRepository.streamCalls).isEqualTo(2)
            assertThat(commentRepository.commentsCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
