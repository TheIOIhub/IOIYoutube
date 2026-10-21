package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
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
class VideoPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer

    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val ukrainian = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)
    private val english = AudioTrackOption("en.1", "English · оригінал", "en", "https://a/en", 128_000, true)
    private val stream = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = "https://hls",
        videoOnly = listOf(video),
        audioTracks = listOf(english, ukrainian),
    )

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    private fun show(
        stream: VideoStream? = this.stream,
        player: ExoPlayer? = this.player,
        audioOptions: List<AudioTrackOption> = this.stream.audioTracks,
        onSelectAudio: (AudioTrackOption) -> Unit = {},
        onScaleChange: (VideoScale) -> Unit = {},
        selectedVideo: VideoVariant? = video,
        selectedAudio: AudioTrackOption? = english,
        isFullscreen: Boolean = false,
        onToggleFullscreen: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(
                    player = player,
                    stream = stream,
                    videoOptions = this.stream.videoOnly,
                    audioOptions = audioOptions,
                    selectedVideo = selectedVideo,
                    selectedAudio = selectedAudio,
                    onSelectAuto = {},
                    onSelectVideo = {},
                    onSelectAudio = onSelectAudio,
                    scale = VideoScale.FIT,
                    onScaleChange = onScaleChange,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
        }
    }

    @Test
    fun `missing stream shows unavailable message instead of player`() {
        show(stream = null)

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `available stream hides the unavailable message`() {
        show()

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `audio button is shown only with more than one track`() {
        show(audioOptions = listOf(english, ukrainian))

        composeRule.onNodeWithContentDescription("Аудіодоріжка").performClick()
        composeRule.onNodeWithText("Українська · дубляж").assertExists()
    }

    @Test
    fun `single audio track hides the audio button`() {
        show(audioOptions = listOf(ukrainian), selectedAudio = ukrainian)

        composeRule.onNodeWithContentDescription("Аудіодоріжка").assertDoesNotExist()
    }

    @Test
    fun `selecting an audio track reports it`() {
        var selected: AudioTrackOption? = null
        show(onSelectAudio = { selected = it })

        composeRule.onNodeWithContentDescription("Аудіодоріжка").performClick()
        composeRule.onNodeWithText("Українська · дубляж").performClick()

        assertThat(selected).isEqualTo(ukrainian)
    }

    @Test
    fun `quality menu does not mix in audio tracks`() {
        show()

        composeRule.onNodeWithContentDescription("Якість").performClick()
        composeRule.onNodeWithText("Авто (HLS)").assertExists()
        composeRule.onNodeWithText("Українська · дубляж").assertDoesNotExist()
    }

    @Test
    fun `scale button reports fill`() {
        var scale: VideoScale? = null
        show(onScaleChange = { scale = it })

        composeRule.onNodeWithContentDescription("Заповнити екран").performClick()

        assertThat(scale).isEqualTo(VideoScale.FILL)
    }

    @Test
    fun `fit and fill map to the right resize mode`() {
        assertThat(resizeModeFor(VideoScale.FIT))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        assertThat(resizeModeFor(VideoScale.FILL))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    }

    @Test
    fun `fullscreen button is hidden without a callback`() {
        show()

        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
    }

    @Test
    fun `unavailable fullscreen still offers the exit button`() {
        show(stream = null, isFullscreen = true, onToggleFullscreen = {})

        composeRule.onNodeWithText("Відео недоступне").assertExists()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }
}
