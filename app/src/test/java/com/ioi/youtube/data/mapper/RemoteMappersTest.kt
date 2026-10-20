package com.ioi.youtube.data.mapper

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawAudioTrack
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.model.RawVideoVariant
import org.junit.Test

class RemoteMappersTest {

    @Test
    fun `maps raw summary to domain`() {
        val raw = RawVideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Title",
            uploaderName = "Channel",
            thumbnailUrl = "https://img/1.jpg",
            durationSeconds = 75,
            viewCount = 1500,
            uploadDate = "2 years ago",
        )

        val domain = raw.toDomain()

        assertThat(domain.id).isEqualTo("dQw4w9WgXcQ")
        assertThat(domain.title).isEqualTo("Title")
        assertThat(domain.durationSeconds).isEqualTo(75L)
        assertThat(domain.viewCount).isEqualTo(1500L)
    }

    @Test
    fun `maps raw details including recommended videos`() {
        val recommended = RawVideoSummary("aaaaa", "Rec", "Ch", "u", 10, 1, null)
        val raw = RawVideoDetails(
            id = "dQw4w9WgXcQ",
            title = "T",
            uploaderName = "U",
            uploaderAvatarUrl = null,
            description = "D",
            viewCount = 0,
            likeCount = 0,
            uploadDate = null,
            thumbnailUrl = "t",
            durationSeconds = 0,
            recommended = listOf(recommended),
        )

        val domain = raw.toDomain()

        assertThat(domain.description).isEqualTo("D")
        assertThat(domain.uploadDate).isNull()
        assertThat(domain.recommendedVideos).hasSize(1)
        assertThat(domain.recommendedVideos.first().id).isEqualTo("aaaaa")
    }

    @Test
    fun `maps zero values from NewPipe no-data markers`() {
        val raw = RawComment(
            id = "c1",
            authorName = "A",
            authorAvatarUrl = null,
            text = "",
            likeCount = 0,
            publishedTime = null,
            heartedByUploader = false,
            pinned = false,
        )

        val domain = raw.toDomain()

        assertThat(domain.likeCount).isEqualTo(0L)
        assertThat(domain.text).isEmpty()
    }

    @Test
    fun `maps raw stream to domain`() {
        val raw = RawVideoStream(url = "https://v/x", resolution = "720p", format = "MPEG-4")

        val domain = raw.toDomain()

        assertThat(domain.url).isEqualTo("https://v/x")
        assertThat(domain.resolution).isEqualTo("720p")
        assertThat(domain.format).isEqualTo("MPEG-4")
    }

    @Test
    fun `maps hls url to domain`() {
        val raw = RawVideoStream(
            url = "https://v/x",
            resolution = "720p",
            format = "MPEG-4",
            hlsUrl = "https://manifest.example/hls_variant",
        )

        val domain = raw.toDomain()

        assertThat(domain.hlsUrl).isEqualTo("https://manifest.example/hls_variant")
    }

    @Test
    fun `maps upload timestamp to domain`() {
        val raw = RawVideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Title",
            uploaderName = "Channel",
            thumbnailUrl = "https://img/1.jpg",
            durationSeconds = 75,
            viewCount = 1500,
            uploadDate = "2 years ago",
            uploadedAt = 1_700_000_000_000,
        )

        assertThat(raw.toDomain().uploadedAt).isEqualTo(1_700_000_000_000)
    }

    @Test
    fun `maps video-only variants and audio tracks`() {
        val raw = RawVideoStream(
            url = "https://v/360.mp4",
            resolution = "360p",
            format = "MPEG-4",
            hlsUrl = "https://hls",
            videoOnly = listOf(RawVideoVariant("https://v/1080.mp4", 1080, 2_000_000)),
            audioTracks = listOf(
                RawAudioTrack("uk.1", "Ukrainian", "uk", "https://a/uk", 128_000, false),
            ),
        )

        val domain = raw.toDomain()

        assertThat(domain.videoOnly.single().height).isEqualTo(1080)
        assertThat(domain.audioTracks.single().label).isEqualTo("Українська · дубляж")
        assertThat(domain.audioTracks.single().original).isFalse()
    }
}
