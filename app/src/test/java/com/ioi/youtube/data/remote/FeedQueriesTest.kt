package com.ioi.youtube.data.remote

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedTopic
import kotlin.random.Random
import org.junit.Test

class FeedQueriesTest {

    @Test
    fun `every topic has at least three queries`() {
        FeedTopic.entries.filter { it != FeedTopic.ALL }.forEach { topic ->
            assertThat(TOPIC_QUERIES.getValue(topic).size).isAtLeast(3)
        }
        assertThat(TOPIC_QUERIES).doesNotContainKey(FeedTopic.ALL)
    }

    @Test
    fun `all returns two queries from two different topics`() {
        val queries = queriesFor(FeedTopic.ALL, Random(1))

        assertThat(queries).hasSize(2)
        assertThat(queries[0]).isNotEqualTo(queries[1])
    }

    @Test
    fun `topic returns a query from its own pool`() {
        val query = queriesFor(FeedTopic.GAMES, Random(7)).single()

        assertThat(query).isIn(TOPIC_QUERIES.getValue(FeedTopic.GAMES))
    }

    @Test
    fun `the same seed produces the same queries`() {
        assertThat(queriesFor(FeedTopic.ALL, Random(42)))
            .isEqualTo(queriesFor(FeedTopic.ALL, Random(42)))
    }
}
