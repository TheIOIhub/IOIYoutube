package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeSearchRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchVideosUseCaseTest {

    private val repository = FakeSearchRepository()
    private val useCase = SearchVideosUseCase(repository)

    @Test
    fun `forwards search results from repository`() = runTest {
        val results = SearchResults(query = "kotlin", videos = listOf(TestData.video()))
        repository.setResults(UiState.Success(results))

        useCase("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(results))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards error state from repository`() = runTest {
        repository.setResults(UiState.Error("Search failed"))

        useCase("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("Search failed"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
