package com.ioi.youtube.presentation.player

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerHolderTest {

    private lateinit var holder: PlayerHolder

    @Before
    fun setUp() {
        holder = PlayerHolder(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        holder.release()
    }

    private val audio = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)

    private fun stream(vararg variants: VideoVariant) = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = "https://hls",
        videoOnly = variants.toList(),
        audioTracks = listOf(audio),
    )

    @Test
    fun `switching video keeps the current position`() {
        val first = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
        val second = VideoVariant("https://v/720.mp4", 720, 1_000_000)
        holder.update(stream(first, second), first, audio, auto = false)
        holder.player.seekTo(5_000)

        holder.update(stream(first, second), second, audio, auto = false)

        assertThat(holder.player.currentPosition).isAtLeast(4_000)
    }

    @Test
    fun `switching audio keeps the current position`() {
        val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
        val english = AudioTrackOption("en.1", "English · оригінал", "en", "https://a/en", 128_000, true)
        val source = stream(video).copy(audioTracks = listOf(audio, english))
        holder.update(source, video, audio, auto = false)
        holder.player.seekTo(3_000)

        holder.update(source, video, english, auto = false)

        assertThat(holder.player.currentPosition).isAtLeast(2_000)
    }

    @Test
    fun `stream without any playable url reports an error instead of crashing`() {
        holder.update(VideoStream("", "0p", ""), null, null, auto = true)

        assertThat(holder.error).isEqualTo(PlayerHolder.ERROR_PLAYBACK)
    }

    @Test
    fun `release is idempotent`() {
        holder.release()
        holder.release()

        assertThat(holder.error).isNull()
    }
}
