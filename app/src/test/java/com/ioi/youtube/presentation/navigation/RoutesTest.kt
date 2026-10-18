package com.ioi.youtube.presentation.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutesTest {

    @Test
    fun `home route`() {
        assertThat(Routes.HOME).isEqualTo("home")
    }

    @Test
    fun `search route`() {
        assertThat(Routes.SEARCH).isEqualTo("search")
    }

    @Test
    fun `player route template`() {
        assertThat(Routes.PLAYER).isEqualTo("player/{videoId}")
    }

    @Test
    fun `video id argument key`() {
        assertThat(Routes.ARG_VIDEO_ID).isEqualTo("videoId")
    }

    @Test
    fun `player route builds from video id`() {
        assertThat(Routes.player("dQw4w9WgXcQ")).isEqualTo("player/dQw4w9WgXcQ")
    }
}
