package com.ioi.youtube.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedTest {

    @Test
    fun `all topic is first and ids are unique`() {
        assertThat(FeedTopic.entries.first()).isEqualTo(FeedTopic.ALL)
        assertThat(FeedTopic.entries.map { it.id }).containsNoDuplicates()
        assertThat(FeedTopic.entries.map { it.label }).containsNoDuplicates()
    }

    @Test
    fun `sorts are exposed in menu order`() {
        assertThat(FeedSort.entries).containsExactly(
            FeedSort.RELEVANCE,
            FeedSort.NEWEST,
            FeedSort.POPULAR,
        ).inOrder()
    }

    @Test
    fun `feed page defaults to not exhausted`() {
        assertThat(FeedPage(videos = emptyList()).exhausted).isFalse()
    }
}
