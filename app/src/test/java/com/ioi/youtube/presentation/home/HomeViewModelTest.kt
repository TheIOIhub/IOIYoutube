package com.ioi.youtube.presentation.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.GetHomeFeedUseCase
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeVideoRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(GetHomeFeedUseCase(repository))

    @Test
    fun `starts with loading then emits success`() = runTest(testDispatcher) {
        val videos = listOf(TestData.video(), TestData.video("abcdefghijk"))
        repository.setFeed(UiState.Success(videos))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            assertThat(awaitItem()).isEqualTo(UiState.Success(videos))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits error state from repository`() = runTest(testDispatcher) {
        repository.setFeed(UiState.Error("Не вдалося завантажити стрічку"))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            assertThat(awaitItem()).isEqualTo(UiState.Error("Не вдалося завантажити стрічку"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-subscribes to the use case`() = runTest(testDispatcher) {
        repository.setFeed(UiState.Success(listOf(TestData.video())))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(repository.feedCalls).isEqualTo(1)

            viewModel.retry()
            advanceUntilIdle()

            assertThat(repository.feedCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading initial value is exposed before subscription`() {
        // Ще до старту колекції stateIn вiддає initialValue.
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value).isEqualTo(UiState.Loading)
    }
}
