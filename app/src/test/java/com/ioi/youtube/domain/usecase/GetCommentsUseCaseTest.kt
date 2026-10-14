package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeCommentRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetCommentsUseCaseTest {

    private val repository = FakeCommentRepository()
    private val useCase = GetCommentsUseCase(repository)

    @Test
    fun `forwards comments from repository`() = runTest {
        val comments = TestData.comments()
        repository.setComments(UiState.Success(comments))

        useCase("dQw4w9WgXcQ").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(comments))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards loading state`() = runTest {
        useCase("dQw4w9WgXcQ").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
