package com.ioi.youtube.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamOptionsTest {

    private fun variant(url: String, height: Int, bitrate: Int) = VideoVariant(url, height, bitrate)

    private fun audio(
        id: String,
        label: String,
        bitrate: Int,
        original: Boolean = false,
        url: String = "https://a/$id",
    ) = AudioTrackOption(id, label, null, url, bitrate, original)

    @Test
    fun `video variants keep the best bitrate per height and sort by height`() {
        val chosen = chooseVideoVariants(
            listOf(
                variant("a", 1080, 1_000_000),
                variant("b", 1080, 3_000_000),
                variant("c", 720, 500_000),
                variant(" ", 480, 100),
            ),
        )

        assertThat(chosen.map { it.url }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `default video prefers 1080 over 2160`() {
        val variants = chooseVideoVariants(
            listOf(variant("4k", 2160, 1), variant("fhd", 1080, 1), variant("hd", 720, 1)),
        )

        assertThat(defaultVideo(variants)?.height).isEqualTo(1080)
    }

    @Test
    fun `default video falls back to the highest when everything is above 1080`() {
        val variants = chooseVideoVariants(listOf(variant("4k", 2160, 1), variant("2k", 1440, 1)))

        assertThat(defaultVideo(variants)?.height).isEqualTo(2160)
    }

    @Test
    fun `audio tracks keep the best bitrate per id and put original first`() {
        val chosen = chooseAudioTracks(
            listOf(
                audio("en.1", "English · оригінал", 128_000, original = true),
                audio("uk.1", "Українська · дубляж", 96_000),
                audio("uk.1", "Українська · дубляж", 160_000),
            ),
        )

        assertThat(chosen.map { it.id }).containsExactly("en.1", "uk.1").inOrder()
        assertThat(chosen.first { it.id == "uk.1" }.bitrate).isEqualTo(160_000)
    }

    @Test
    fun `default audio prefers the original track`() {
        val tracks = listOf(
            audio("uk.1", "Українська", 160_000),
            audio("en.1", "English", 128_000, original = true),
        )

        assertThat(defaultAudio(tracks)?.id).isEqualTo("en.1")
    }

    @Test
    fun `audio label uses the Ukrainian display name`() {
        assertThat(audioLabel("uk", null, original = false)).isEqualTo("Українська · дубляж")
        assertThat(audioLabel("en", null, original = true)).isEqualTo("Англійська · оригінал")
    }

    @Test
    fun `audio label falls back to the name and then to the index`() {
        assertThat(audioLabel(null, "Dubbed", original = false)).isEqualTo("Dubbed · дубляж")
        assertThat(audioLabel(null, null, original = true, index = 3))
            .isEqualTo("Аудіо 3 · оригінал")
    }

    @Test
    fun `video label includes height and bitrate`() {
        assertThat(formatVideoLabel(1080, 4_300_000)).isEqualTo("1080p · 4.3 Мбіт/с")
    }
}
