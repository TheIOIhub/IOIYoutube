package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Чиста логіка без Android: голий JVM, без Robolectric. */
class QualityOptionsTest {

    @Test
    fun `keeps one option per height with the highest bitrate sorted by height`() {
        val tracks = listOf(
            VideoTrackInfo(groupIndex = 0, trackIndex = 0, height = 360, bitrate = 444_000),
            VideoTrackInfo(groupIndex = 1, trackIndex = 0, height = 1080, bitrate = 1_500_000),
            VideoTrackInfo(groupIndex = 1, trackIndex = 1, height = 1080, bitrate = 4_300_000),
            VideoTrackInfo(groupIndex = 2, trackIndex = 0, height = 720, bitrate = 1_058_000),
        )

        val options = chooseQualityOptions(tracks)

        assertThat(options.map { it.height }).containsExactly(1080, 720, 360).inOrder()
        assertThat(options.map { it.groupIndex }).containsExactly(1, 2, 0).inOrder()
        assertThat(options.map { it.trackIndex }).containsExactly(1, 0, 0).inOrder()
        assertThat(options.first().bitrate).isEqualTo(4_300_000)
    }

    @Test
    fun `ignores tracks without height and empty input`() {
        assertThat(chooseQualityOptions(emptyList())).isEmpty()
        assertThat(
            chooseQualityOptions(listOf(VideoTrackInfo(0, 0, height = 0, bitrate = 100_000))),
        ).isEmpty()
    }

    @Test
    fun `formats labels in kilobits and megabits`() {
        assertThat(formatQualityLabel(360, 444_226)).isEqualTo("360p · 444 Кбіт/с")
        assertThat(formatQualityLabel(1080, 4_334_157)).isEqualTo("1080p · 4.3 Мбіт/с")
        assertThat(formatQualityLabel(2160, 18_076_636)).isEqualTo("2160p · 18.1 Мбіт/с")
        assertThat(formatQualityLabel(360, 0)).isEqualTo("360p · —")
    }
}
