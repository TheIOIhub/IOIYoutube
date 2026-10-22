package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    private fun show(
        onExpand: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                MiniPlayer(
                    player = player,
                    onExpand = onExpand,
                    onClose = onClose,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `mini player is visible`() {
        show()

        composeRule.onNodeWithTag("mini-player").assertExists()
    }

    @Test
    fun `close button reports close`() {
        var closed = false
        show(onClose = { closed = true })

        composeRule.onNodeWithContentDescription("Закрити міні-плеєр").performClick()

        assertThat(closed).isTrue()
    }

    @Test
    fun `video tap reports expand`() {
        var expanded = false
        show(onExpand = { expanded = true })

        composeRule.onNodeWithContentDescription("Розгорнути відео").performClick()

        assertThat(expanded).isTrue()
    }

    @Test
    fun `play pause toggles play when ready`() {
        show()
        assertThat(player.playWhenReady).isFalse()

        composeRule.onNodeWithContentDescription("Відтворити").performClick()
        assertThat(player.playWhenReady).isTrue()

        composeRule.onNodeWithContentDescription("Пауза").performClick()
        assertThat(player.playWhenReady).isFalse()
    }
}
