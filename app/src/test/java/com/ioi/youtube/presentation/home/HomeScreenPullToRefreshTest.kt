package com.ioi.youtube.presentation.home

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenPullToRefreshTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `pulling down on the home list triggers refresh`() {
        val refreshed = AtomicBoolean(false)
        val items = listOf(FeedItem.Video(TestData.video(id = "aaaaaaaaaaa", title = "First video")))

        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = HomeUiState(items = items, loading = false),
                    onVideoClick = {},
                    onShortClick = {},
                    onTopicSelected = {},
                    onSortSelected = {},
                    onLoadMore = {},
                    onRefresh = { refreshed.set(true) },
                    onRetry = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("First video")))
            .performTouchInput {
                swipeDown(top, bottom, 2000L)
            }

        composeRule.runOnIdle { }

        assertThat(refreshed.get()).isTrue()
    }
}
