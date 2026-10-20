package com.ioi.youtube.presentation.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IOITubeBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `icons are shown without labels`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.HOME, onNavigate = {})
            }
        }

        composeRule.onNodeWithContentDescription("Головна").assertExists()
        composeRule.onNodeWithContentDescription("Пошук").assertExists()
        composeRule.onNodeWithText("Головна").assertDoesNotExist()
        composeRule.onNodeWithText("Пошук").assertDoesNotExist()
    }


    @Test
    fun `shorts tab is present`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.SHORTS, onNavigate = {})
            }
        }

        composeRule.onNodeWithContentDescription("Shorts").assertExists()
    }
    @Test
    fun `tapping an icon reports its route`() {
        var navigated: String? = null

        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.HOME, onNavigate = { navigated = it })
            }
        }

        composeRule.onNodeWithContentDescription("Пошук").performClick()

        assertThat(navigated).isEqualTo(Routes.SEARCH)
    }

    @Test
    fun `shorts tab is in the bottom bar next to the other two tabs`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.SHORTS, onNavigate = {})
            }
        }

        composeRule.onNodeWithContentDescription("Головна").assertExists()
        composeRule.onNodeWithContentDescription("Shorts").assertExists()
        composeRule.onNodeWithContentDescription("Пошук").assertExists()
    }
}
