package com.ioi.youtube.presentation.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `video card renders meta line and handles click`() {
        val video = TestData.video(id = "dQw4w9WgXcQ", title = "Never Gonna Give You Up")
        var clickedId: String? = null

        composeRule.setContent {
            IOITubeTheme {
                VideoCard(video = video, onClick = { clickedId = it })
            }
        }

        composeRule.onNodeWithText("Never Gonna Give You Up").assertExists()
        composeRule.onNodeWithText(
            "Test Channel · 1.5M views · 3:32",
        ).assertExists()
        composeRule.onNodeWithText("Never Gonna Give You Up").performClick()

        assertThat(clickedId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `offline banner renders its message`() {
        composeRule.setContent {
            IOITubeTheme { OfflineBanner() }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error box renders message and retry button`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                ErrorBox(message = "Не вдалося завантажити стрічку", onRetry = { retried = true })
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }
}
