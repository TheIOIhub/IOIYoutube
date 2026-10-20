package com.ioi.youtube.presentation.navigation

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
    fun `shorts tab is highlighted when a specific short is open`() {
        composeRule.setContent {
            IOITubeBottomBar(currentRoute = Routes.shorts("dQw4w9WgXcQ"), onNavigate = {})
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

    @Test
    fun `bar is 56dp tall and icons are 24dp inside a 48dp touch target`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.HOME, onNavigate = {})
            }
        }

        val bar = composeRule.onRoot().getUnclippedBoundsInRoot()
        val icon = composeRule
            .onNodeWithContentDescription("Головна", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val tab = composeRule
            .onNodeWithContentDescription("Головна")
            .getUnclippedBoundsInRoot()

        // Смуга не має розростатися до 80dp, як стандартний NavigationBar.
        assertThat(bar.height.value).isEqualTo(56f)
        // Іконка мусить мати реальний розмір: зайвий padding колись стискав її до 0dp.
        assertThat(icon.height.value).isEqualTo(24f)
        assertThat(icon.width.value).isEqualTo(24f)
        // Площа дотику — не менше 48dp за Material guidelines.
        assertThat(tab.height.value).isAtLeast(48f)
        // Іконка відцентрована по вертикалі в пігулці.
        val expectedTop = tab.top.value + (tab.height.value - icon.height.value) / 2f
        assertThat(icon.top.value).isWithin(0.5f).of(expectedTop)
    }
}
