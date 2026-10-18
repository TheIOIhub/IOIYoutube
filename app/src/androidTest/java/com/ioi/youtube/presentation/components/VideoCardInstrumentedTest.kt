package com.ioi.youtube.presentation.components

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ioi.youtube.domain.model.VideoSummary
import org.junit.Assert.assertEquals
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoCardInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun rendersRealCardAndHandlesClick() {
        val video = VideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Instrumented title",
            uploaderName = "Instrumented Channel",
            thumbnailUrl = "https://example.com/x.jpg",
            durationSeconds = 61,
            viewCount = 1500,
            uploadDate = "2 years ago",
        )
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                VideoCard(video = video, onClick = { clicked = it })
            }
        }

        composeRule.onNodeWithText("Instrumented title").assertExists()
        composeRule.onNodeWithText("Instrumented Channel · 1.5K views · 1:01").assertExists()
        composeRule.onNodeWithText("Instrumented title").performClick()

        assertEquals("dQw4w9WgXcQ", clicked)
    }
}
