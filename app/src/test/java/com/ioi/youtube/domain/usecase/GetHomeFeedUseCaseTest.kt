package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.VideoRepository
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetHomeFeedUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetHomeFeedUseCase(repository)

    @Test
    fun `forwards feed state from repository`() = runTest {
        val videos = listOf(TestData.video(), TestData.video("abcdefghijk"))
        repository.setFeed(UiState.Success(videos))

        useCase().test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(videos))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards loading state from repository`() = runTest {
        useCase().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delegates to repository exactly once per invocation`() = runTest {
        val mockedRepository = mockk<VideoRepository> {
            every { getHomeFeed() } returns flowOf(UiState.Success(listOf(TestData.video())))
        }

        GetHomeFeedUseCase(mockedRepository)().first()

        verify(exactly = 1) { mockedRepository.getHomeFeed() }
    }
}
