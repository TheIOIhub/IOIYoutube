package com.ioi.youtube.presentation.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.SearchVideosUseCase
import com.ioi.youtube.testutil.FakeSearchRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeSearchRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(SearchVideosUseCase(repository))

    @Test
    fun `initial state is loading`() {
        assertThat(createViewModel().uiState.value).isEqualTo(UiState.Loading)
    }

    @Test
    fun `blank query returns empty results without network call`() = runTest(testDispatcher) {
        repository.setResults(UiState.Success(SearchResults("x", listOf(TestData.video()))))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            advanceTimeBy(400)

            val state = awaitItem() as UiState.Success
            assertThat(state.data.videos).isEmpty()
            assertThat(repository.searchCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query is debounced and searched once`() = runTest(testDispatcher) {
        val video = TestData.video(title = "Kotlin tutorial")
        repository.setResults(UiState.Success(SearchResults("kotlin", listOf(video))))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            viewModel.onQueryChange("ko")
            advanceTimeBy(100)
            viewModel.onQueryChange("kotlin")
            advanceTimeBy(100)
            expectNoEvents() // debounce ще тримає запит

            advanceTimeBy(300)
            val state = awaitItem() as UiState.Success
            assertThat(state.data.videos).hasSize(1)
            assertThat(repository.searchCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-runs last query`() = runTest(testDispatcher) {
        repository.setResults(UiState.Success(SearchResults("android", emptyList())))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onQueryChange("android")
            advanceUntilIdle()
            assertThat(repository.searchCalls).isEqualTo(1)

            viewModel.retry()
            advanceUntilIdle()

            assertThat(repository.searchCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query is trimmed before search`() = runTest(testDispatcher) {
        repository.setResults(UiState.Success(SearchResults("kotlin", listOf(TestData.video()))))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            viewModel.onQueryChange("  kotlin  ")
            advanceTimeBy(400)
            assertThat(awaitItem()).isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.searchCalls).isEqualTo(1)
        assertThat(repository.lastQuery).isEqualTo("kotlin")
    }

    @Test
    fun `error state is forwarded`() = runTest(testDispatcher) {
        repository.setResults(UiState.Error("Не вдалося виконати пошук"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            viewModel.onQueryChange("android")
            advanceTimeBy(400)
            assertThat(awaitItem()).isEqualTo(UiState.Error("Не вдалося виконати пошук"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
