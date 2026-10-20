package com.ioi.youtube.data.repository

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.VideoSummary
import org.junit.Test

class FeedSortingTest {

    private fun video(id: String, views: Long, uploadedAt: Long?) = VideoSummary(
        id = id,
        title = id,
        uploaderName = "Ch",
        thumbnailUrl = "u",
        durationSeconds = 1,
        viewCount = views,
        uploadDate = null,
        uploadedAt = uploadedAt,
    )

    @Test
    fun `newest puts dated videos first and undated last`() {
        val videos = listOf(
            video("old", 10, 100),
            video("none", 99, null),
            video("new", 1, 300),
        )

        val sorted = sortVideos(videos, FeedSort.NEWEST)

        assertThat(sorted.map { it.id }).containsExactly("new", "old", "none").inOrder()
    }

    @Test
    fun `popular sorts by view count descending`() {
        val videos = listOf(video("a", 10, null), video("b", 500, null), video("c", 100, null))

        assertThat(sortVideos(videos, FeedSort.POPULAR).map { it.id })
            .containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `relevance keeps the server order`() {
        val videos = listOf(video("b", 10, null), video("a", 500, null))

        assertThat(sortVideos(videos, FeedSort.RELEVANCE).map { it.id })
            .containsExactly("b", "a").inOrder()
    }
}
