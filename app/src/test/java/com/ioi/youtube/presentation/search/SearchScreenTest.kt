package com.ioi.youtube.presentation.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `query typing calls onQueryChange`() {
        var query: String? = null

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("", emptyList())),
                    onQueryChange = { query = it },
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        assertThat(query).isEqualTo("kot")
        composeRule.onNode(hasText("kot")).assertExists()
    }

    @Test
    fun `results grid shows videos`() {
        val videos = listOf(TestData.video(id = "aaaaaaaaaaa", title = "Grid result"))
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("grid", videos)),
                    onQueryChange = {},
                    onVideoClick = { clicked = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Grid result").assertExists()
        composeRule.onNodeWithText("Grid result").performClick()
        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertDoesNotExist()
    }

    @Test
    fun `cached search shows offline banner`() {
        val videos = listOf(TestData.video(title = "Cached result"))

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("cache", videos), fromCache = true),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error shows retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Error("Не вдалося виконати пошук"),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося виконати пошук").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `empty results show empty box`() {
        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("none", emptyList())),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Нічого не знайдено").assertExists()
    }

    @Test
    fun `results are shown in one column`() {
        val results = SearchResults(
            query = "kotlin",
            videos = listOf(
                TestData.video(id = "aaaaaaaaaaa", title = "First result"),
                TestData.video(id = "bbbbbbbbb", title = "Second result"),
            ),
        )

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(results),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        val first = composeRule.onNodeWithText("First result").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Second result").fetchSemanticsNode().boundsInRoot

        assertThat(second.top).isGreaterThan(first.top)
        assertThat(second.left).isEqualTo(first.left)
    }
}
