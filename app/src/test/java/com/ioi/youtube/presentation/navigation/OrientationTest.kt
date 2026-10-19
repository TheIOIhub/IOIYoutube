package com.ioi.youtube.presentation.navigation

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrientationTest {

    @Test
    fun `player screen rotates freely`() {
        assertThat(orientationForRoute(Routes.PLAYER))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
    }

    @Test
    fun `every other route stays portrait`() {
        assertThat(orientationForRoute(Routes.HOME))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(orientationForRoute(Routes.SEARCH))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(orientationForRoute(null))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
}
