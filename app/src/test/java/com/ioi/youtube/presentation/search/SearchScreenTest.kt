package com.ioi.youtube.presentation.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
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
    }

    @Test
    fun `results grid shows videos`() {
        val videos = listOf(TestData.video(id = "aaaaaaaaaaa", title = "Grid result"))

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("grid", videos)),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Grid result").assertExists()
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
}
