package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import org.junit.Test

class PlaybackPlanTest {

    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val audio = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)

    private fun stream(
        url: String = "https://v/360.mp4",
        hlsUrl: String? = "https://hls",
    ) = VideoStream(
        url = url,
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = hlsUrl,
        videoOnly = listOf(video),
        audioTracks = listOf(audio),
    )

    @Test
    fun `merged plan wins when video and audio are selected`() {
        assertThat(buildPlaybackPlan(stream(), video, audio))
            .isEqualTo(PlaybackPlan.Merged("https://v/1080.mp4", "https://a/uk"))
    }

    @Test
    fun `progressive is used when nothing is selected`() {
        assertThat(buildPlaybackPlan(stream(), null, null))
            .isEqualTo(PlaybackPlan.Progressive("https://v/360.mp4"))
    }

    @Test
    fun `hls is the last fallback`() {
        assertThat(buildPlaybackPlan(stream(url = ""), null, null))
            .isEqualTo(PlaybackPlan.Hls("https://hls"))
    }

    @Test
    fun `auto mode prefers hls`() {
        assertThat(buildPlaybackPlan(stream(), null, null, auto = true))
            .isEqualTo(PlaybackPlan.Hls("https://hls"))
    }

    @Test
    fun `no playable source returns null`() {
        assertThat(buildPlaybackPlan(VideoStream("", "0p", ""), null, null)).isNull()
        assertThat(buildPlaybackPlan(null, video, audio)).isNull()
    }
}
