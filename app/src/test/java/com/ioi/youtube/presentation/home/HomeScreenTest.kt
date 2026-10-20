package com.ioi.youtube.presentation.home

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        uiState: HomeUiState,
        onVideoClick: (String) -> Unit = {},
        onShortClick: (String) -> Unit = {},
        onTopicSelected: (FeedTopic) -> Unit = {},
        onSortSelected: (FeedSort) -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = uiState,
                    onVideoClick = onVideoClick,
                    onShortClick = onShortClick,
                    onTopicSelected = onTopicSelected,
                    onSortSelected = onSortSelected,
                    onLoadMore = onLoadMore,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun `topic chip selection is propagated`() {
        var selected: FeedTopic? = null
        show(HomeUiState(items = emptyList(), loading = false), onTopicSelected = { selected = it })

        composeRule.onNodeWithTag("topic-chips").performScrollToNode(hasText("Ігри"))
        composeRule.onNodeWithText("Ігри").performClick()

        assertThat(selected).isEqualTo(FeedTopic.GAMES)
    }

    @Test
    fun `sort menu selects popular`() {
        var selected: FeedSort? = null
        show(HomeUiState(items = emptyList(), loading = false), onSortSelected = { selected = it })

        composeRule.onNodeWithTag("topic-chips").performScrollToNode(hasText("Релевантність"))
        composeRule.onNodeWithText("Релевантність").performClick()
        composeRule.onNodeWithText("Популярні").performClick()

        assertThat(selected).isEqualTo(FeedSort.POPULAR)
    }

    @Test
    fun `shorts block shows two cards and opens the tapped short`() {
        var clicked: String? = null
        val block = FeedItem.ShortsBlock(
            id = "shorts-4",
            shorts = listOf(
                TestData.video(id = "shortAAAAA", title = "First short"),
                TestData.video(id = "shortBBBBB", title = "Second short"),
            ),
        )
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Video")), block),
                loading = false,
            ),
            onShortClick = { clicked = it },
        )

        composeRule.onNodeWithText("YouTube Shorts").assertExists()
        composeRule.onNodeWithText("First short").assertExists()
        composeRule.onNodeWithText("Second short").performClick()

        assertThat(clicked).isEqualTo("shortBBBBB")
    }

    @Test
    fun `video click is propagated`() {
        var clicked: String? = null
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(id = "aaaaaaaaaaa", title = "First video"))),
                loading = false,
            ),
            onVideoClick = { clicked = it },
        )

        composeRule.onNodeWithText("First video").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }

    @Test
    fun `cached feed shows offline banner`() {
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Cached video"))),
                loading = false,
                fromCache = true,
            ),
        )

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `empty error state shows message and retry`() {
        var retried = false
        show(HomeUiState(loading = false, error = "Не вдалося завантажити стрічку"), onRetry = { retried = true })

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `load more error keeps items and offers retry`() {
        var retried = false
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Kept video"))),
                loading = false,
                loadMoreError = "Не вдалося завантажити стрічку",
            ),
            onLoadMore = { retried = true },
        )

        composeRule.onNodeWithText("Kept video").assertExists()
        composeRule.onNodeWithText("Повторити").performClick()

        assertThat(retried).isTrue()
    }
}
