package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.Comment
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
class CommentsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: UiState<List<Comment>>, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            IOITubeTheme {
                CommentsSection(state = state, onRetry = onRetry)
            }
        }
    }

    @Test
    fun `collapsed by default shows count and hides comments`() {
        setContent(UiState.Success(TestData.comments()))

        composeRule.onNodeWithText("Коментарі (2)").assertExists()
        composeRule.onNodeWithText("Показати коментарі").assertExists()
        composeRule.onNodeWithText("First!").assertDoesNotExist()
        composeRule.onNodeWithText("Nice video").assertDoesNotExist()
    }

    @Test
    fun `expanding shows comments and collapsing hides them again`() {
        setContent(UiState.Success(TestData.comments()))

        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Показати коментарі").assertDoesNotExist()
        composeRule.onNodeWithText("Згорнути").assertExists()

        composeRule.onNodeWithText("Згорнути").performClick()
        composeRule.onNodeWithText("First!").assertDoesNotExist()
        composeRule.onNodeWithText("Показати коментарі").assertExists()
    }

    @Test
    fun `collapsed loading does not spin and expanded error offers retry`() {
        var retried = false
        setContent(UiState.Error("Не вдалося завантажити коментарі"), onRetry = { retried = true })

        composeRule.onNodeWithText("Коментарі").assertExists()
        composeRule.onNodeWithText("Спробувати знову").assertDoesNotExist()

        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("Не вдалося завантажити коментарі").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `loading spinner appears only after expanding`() {
        setContent(UiState.Loading)

        composeRule.onNodeWithText("Завантаження…").assertDoesNotExist()

        composeRule.onNodeWithText("Показати коментарі").performClick()

        composeRule.onNodeWithText("Завантаження…").assertExists()
    }
}
