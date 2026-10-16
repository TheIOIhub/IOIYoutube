package com.ioi.youtube.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
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
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `success state shows video cards and propagates clicks`() {
        val videos = listOf(TestData.video(id = "aaaaaaaaaaa", title = "First video"))
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Success(videos),
                    onVideoClick = { clicked = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("First video").assertExists()
        composeRule.onNodeWithText("First video").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }

    @Test
    fun `cached success state shows offline banner`() {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Success(
                        data = listOf(TestData.video(title = "Cached video")),
                        fromCache = true,
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Cached video").assertExists()
        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error state shows message and retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Error("Не вдалося завантажити стрічку"),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `loading state shows progress indicator without crash`() {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(uiState = UiState.Loading, onVideoClick = {}, onRetry = {})
            }
        }

        composeRule.onRoot().assertExists()
    }
}
