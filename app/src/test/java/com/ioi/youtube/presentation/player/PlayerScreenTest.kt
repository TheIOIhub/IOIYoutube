package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import kotlin.OptIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val holders = mutableListOf<PlayerHolder>()

    @After
    fun tearDown() {
        holders.forEach { it.release() }
    }

    private fun holder(): PlayerHolder =
        PlayerHolder(ApplicationProvider.getApplicationContext<Context>()).also { holders += it }

    private fun successState() = PlayerUiState(
        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
        stream = UiState.Success(
            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
        ),
        comments = UiState.Success(emptyList()),
    )

    @Test
    fun `video error replaces player with message and retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(video = UiState.Error("Не вдалося завантажити відео")),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити відео").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()
        assertThat(retried).isTrue()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows title, meta line, comments and recommendations`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Loading,
                        comments = UiState.Success(
                            listOf(
                                Comment("c1", "Alice", null, "First!", 150, "1 day ago", true, true),
                            ),
                        ),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Video dQw4w9WgXcQ").assertExists()
        composeRule.onNodeWithText("Test Channel").assertExists()
        composeRule.onNodeWithText("1.5M views", substring = true).assertExists()
        composeRule.onNodeWithText("Коментарі (1)").assertExists()
        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Закріплено").assertExists()

        // Рекомендації — великі вертикальні картки в LazyColumn, тож сам list scrollable;
        // свайп по root скролить його, і заголовок рекомендацій підніматиметься над вікном.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Рекомендації"))

        composeRule.onNodeWithText("Рекомендації").assertExists()
    }

    @Test
    fun `offline cached video shows banner`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ"), fromCache = true),
                        stream = UiState.Loading,
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `stream error shows row with retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Error("Не вдалося отримати відеопотік"),
                        comments = UiState.Loading,
                    ),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося отримати відеопотік").assertExists()
        // Коментарі тут у стані Loading і згорнуті, тож retry-кнопку дає лише StreamErrorRow —
        // onNodeWithText вимагає рівно одного вузла, що еквівалентно перевірці count == 1.
        composeRule.onNodeWithText("Спробувати знову").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `stream success renders player instead of unavailable message`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    holder = holder(),
                )
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `fullscreen renders only the player`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(
                            listOf(
                                Comment("c1", "Alice", null, "First!", 150, "1 day ago", true, true),
                            ),
                        ),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    holder = holder(),
                    isFullscreen = true,
                    onToggleFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithText("Video dQw4w9WgXcQ").assertDoesNotExist()
        composeRule.onNodeWithText("Коментарі (1)").assertDoesNotExist()
        composeRule.onNodeWithText("Рекомендації").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }

    @Test
    fun `fullscreen button reports the toggle`() {
        var toggled = false

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    holder = holder(),
                    onToggleFullscreen = { toggled = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("На весь екран").performClick()

        assertThat(toggled).isTrue()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `player stays pinned while the page scrolls`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    holder = holder(),
                )
            }
        }

        val before = composeRule.onNodeWithTag("player-container")
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.onRoot().performGesture {
            swipeUp(startY = 400f, endY = 50f, durationMillis = 300)
        }

        val after = composeRule.onNodeWithTag("player-container")
            .fetchSemanticsNode().boundsInRoot.top

        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `disposing the screen does not release the shared holder`() {
        var show by mutableStateOf(true)
        val sharedHolder = holder()

        composeRule.setContent {
            IOITubeTheme {
                if (show) {
                    PlayerScreen(
                        uiState = successState(),
                        onVideoClick = {},
                        onRetry = {},
                        holder = sharedHolder,
                    )
                }
            }
        }

        composeRule.runOnIdle { show = false }
        composeRule.waitForIdle()

        assertThat(sharedHolder.isReleased).isFalse()
    }

    @Test
    fun `recommendations open a video`() {
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = { clicked = it },
                    onRetry = {},
                    holder = holder(),
                )
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Video aaaaaaaaaaa"))
        composeRule.onNodeWithText("Video aaaaaaaaaaa").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }
}
