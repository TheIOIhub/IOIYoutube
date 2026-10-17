package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `null url shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(videoUrl = null)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `blank url shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(videoUrl = "   ")
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }
}
