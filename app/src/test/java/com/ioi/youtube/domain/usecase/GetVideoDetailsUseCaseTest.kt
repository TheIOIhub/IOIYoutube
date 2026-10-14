package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetVideoDetailsUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetVideoDetailsUseCase(repository)

    @Test
    fun `forwards details for requested video id`() = runTest {
        val details = TestData.details("abcdefghijk")
        repository.setDetails("abcdefghijk", UiState.Success(details))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(details))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards error state`() = runTest {
        repository.setDetails("abcdefghijk", UiState.Error("Video unavailable"))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("Video unavailable"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
