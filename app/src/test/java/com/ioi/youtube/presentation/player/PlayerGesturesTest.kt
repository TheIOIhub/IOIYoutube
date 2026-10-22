package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerGesturesTest {

    @Test
    fun `tap zones split the player into left center and right`() {
        val width = 1000f
        assertThat(tapZone(50f, width)).isEqualTo(TapZone.LEFT)
        assertThat(tapZone(340f, width)).isEqualTo(TapZone.LEFT)
        assertThat(tapZone(350f, width)).isEqualTo(TapZone.CENTER)
        assertThat(tapZone(500f, width)).isEqualTo(TapZone.CENTER)
        assertThat(tapZone(650f, width)).isEqualTo(TapZone.CENTER)
        assertThat(tapZone(660f, width)).isEqualTo(TapZone.RIGHT)
        assertThat(tapZone(950f, width)).isEqualTo(TapZone.RIGHT)
    }

    @Test
    fun `swipe up raises the level and swipe down lowers it`() {
        assertThat(adjustedLevel(0.5f, -100f, 1000f)).isWithin(0.0001f).of(0.6f)
        assertThat(adjustedLevel(0.5f, 100f, 1000f)).isWithin(0.0001f).of(0.4f)
    }

    @Test
    fun `level is clamped to zero and one`() {
        assertThat(adjustedLevel(0.9f, -500f, 1000f)).isEqualTo(1f)
        assertThat(adjustedLevel(0.1f, 500f, 1000f)).isEqualTo(0f)
        assertThat(adjustedLevel(0.5f, 0f, 0f)).isEqualTo(0.5f)
    }

    @Test
    fun `seek target stays inside the video`() {
        assertThat(seekTarget(10_000L, -SEEK_STEP_MS, 60_000L)).isEqualTo(5_000L)
        assertThat(seekTarget(3_000L, -SEEK_STEP_MS, 60_000L)).isEqualTo(0L)
        assertThat(seekTarget(8_000L, SEEK_STEP_MS, 10_000L)).isEqualTo(10_000L)
        assertThat(seekTarget(58_000L, SEEK_STEP_MS, 60_000L)).isEqualTo(60_000L)
    }

    @Test
    fun `seek target is not clamped when duration is unknown`() {
        assertThat(seekTarget(58_000L, SEEK_STEP_MS, -1L)).isEqualTo(63_000L)
    }

    @Test
    fun `level percent is rounded`() {
        assertThat(levelPercent(0f)).isEqualTo(0)
        assertThat(levelPercent(0.404f)).isEqualTo(40)
        assertThat(levelPercent(0.406f)).isEqualTo(41)
        assertThat(levelPercent(1.5f)).isEqualTo(100)
    }
}
