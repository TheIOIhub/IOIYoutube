package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.VideoStream
import org.junit.Test

class PlayerSourceTest {

    @Test
    fun `no stream means no source`() {
        assertThat((null as VideoStream?).toPlayerSource()).isNull()
    }

    @Test
    fun `hls manifest wins and progressive stays as fallback`() {
        val stream = VideoStream(
            url = "https://p/360.mp4",
            resolution = "360p",
            format = "MPEG-4",
            hlsUrl = "https://m/hls",
        )

        val source = stream.toPlayerSource()

        assertThat(source).isEqualTo(
            PlayerSource(url = "https://m/hls", isHls = true, fallbackUrl = "https://p/360.mp4"),
        )
    }

    @Test
    fun `stream without hls plays progressive without fallback`() {
        val stream = VideoStream("https://p/360.mp4", "360p", "MPEG-4")

        val source = stream.toPlayerSource()

        assertThat(source).isEqualTo(
            PlayerSource(url = "https://p/360.mp4", isHls = false, fallbackUrl = null),
        )
    }

    @Test
    fun `blank hls url is ignored and blank progressive produces no source`() {
        val blankHls = VideoStream("https://p/360.mp4", "360p", "MPEG-4", hlsUrl = "   ")

        assertThat(blankHls.toPlayerSource()?.isHls).isFalse()
        assertThat(VideoStream("   ", "360p", "MPEG-4").toPlayerSource()).isNull()
    }
}
