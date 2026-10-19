package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
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

    private val progressive = PlayerSource(
        url = "https://example.com/v.mp4",
        isHls = false,
        fallbackUrl = null,
    )

    @Test
    fun `missing source shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = null)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `blank url shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = PlayerSource(url = "   ", isHls = false, fallbackUrl = null))
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `available source renders player without unavailable message`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `quality menu is absent while hls manifest has no video tracks yet`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(
                    source = PlayerSource(
                        url = "https://manifest.example/hls_variant",
                        isHls = true,
                        fallbackUrl = "https://example.com/v.mp4",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Авто").assertDoesNotExist()
    }

    @Test
    fun `fullscreen button is hidden without a callback`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive)
            }
        }

        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertDoesNotExist()
    }

    @Test
    fun `fullscreen button is shown and reflects the state with a callback`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive, isFullscreen = true, onToggleFullscreen = {})
            }
        }

        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
    }

    @Test
    fun `unavailable fullscreen still offers the exit button`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = null, isFullscreen = true, onToggleFullscreen = {})
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }
}
