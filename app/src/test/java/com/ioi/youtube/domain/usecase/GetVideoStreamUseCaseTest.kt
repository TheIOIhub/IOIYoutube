package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.testutil.FakeVideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetVideoStreamUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetVideoStreamUseCase(repository)

    @Test
    fun `forwards stream from repository`() = runTest {
        val stream = VideoStream(url = "https://rr3---sn-abc.googlevideo.com/videoplayback", resolution = "720p", format = "MPEG-4")
        repository.setStream("abcdefghijk", UiState.Success(stream))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(stream))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards error state`() = runTest {
        repository.setStream("abcdefghijk", UiState.Error("No stream available"))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("No stream available"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
