package com.ioi.youtube.presentation.shorts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class ShortsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        uiState: ShortsUiState,
        onRetry: () -> Unit = {},
        onLoadMore: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                ShortsScreen(
                    uiState = uiState,
                    onRetry = onRetry,
                    onLoadStream = {},
                    onRetryStream = {},
                    onLoadMore = onLoadMore,
                )
            }
        }
    }

    @Test
    fun `shows the title channel and views of the current short`() {
        setContent(
            ShortsUiState(
                shorts = UiState.Success(listOf(TestData.video("aaaaaaaaaaa", "Short one"))),
            ),
        )

        composeRule.onNodeWithText("Short one").assertExists()
        composeRule.onNodeWithText("@Test Channel").assertExists()
        composeRule.onNodeWithText("1.5M views").assertExists()
    }

    @Test
    fun `feed error shows retry`() {
        var retried = false
        setContent(
            uiState = ShortsUiState(shorts = UiState.Error("Не вдалося завантажити шортси")),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithText("Не вдалося завантажити шортси").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `empty feed shows empty text`() {
        setContent(ShortsUiState(shorts = UiState.Success(emptyList())))

        composeRule.onNodeWithText("Тут поки що порожньо").assertExists()
    }

    @Test
    fun `approaching the end asks for more shorts`() {
        var moreAsked = false

        setContent(
            uiState = ShortsUiState(
                shorts = UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))),
            ),
            onLoadMore = { moreAsked = true },
        )

        composeRule.waitForIdle()

        assertThat(moreAsked).isTrue()
    }
}
