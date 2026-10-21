package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinchScaleTest {

    @Test
    fun `pinch out above threshold fills the screen`() {
        assertThat(scaleAfterPinch(1.3f)).isEqualTo(VideoScale.FILL)
    }

    @Test
    fun `pinch in below threshold fits the video`() {
        assertThat(scaleAfterPinch(0.7f)).isEqualTo(VideoScale.FIT)
    }

    @Test
    fun `small pinch keeps the current mode`() {
        assertThat(scaleAfterPinch(1.1f)).isNull()
        assertThat(scaleAfterPinch(0.9f)).isNull()
    }
}
