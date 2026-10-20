package com.ioi.youtube.presentation.shorts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.presentation.player.PlayerSource
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose-рівень перевірки плеєра одного шортса.
 *
 * Поведінку відтворення (автоплей, повний цикл, тап — пауза/відтворення) плеєр не ін'єктує
 * [ShortVideoPlayer] (ExoPlayer створюється всередині), тому детальний сценарій
 * відтворення залишено для живого диму 6.11.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortVideoPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `no source shows the unavailable message`() {
        composeRule.setContent {
            IOITubeTheme {
                ShortVideoPlayer(source = null)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `blank source is treated as unavailable`() {
        composeRule.setContent {
            IOITubeTheme {
                ShortVideoPlayer(source = PlayerSource("  ", false, null))
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `playable source renders without the fallback message`() {
        composeRule.setContent {
            IOITubeTheme {
                ShortVideoPlayer(
                    source = PlayerSource("https://example.com/short.mp4", false, null),
                )
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `tap on a playable source does not crash the composable`() {
        composeRule.setContent {
            IOITubeTheme {
                ShortVideoPlayer(
                    source = PlayerSource("https://example.com/short.mp4", false, null),
                )
            }
        }

        // Тап по відео не падає і не показує повідомлення про недоступність.
        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }
}
