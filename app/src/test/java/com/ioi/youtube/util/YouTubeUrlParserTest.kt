package com.ioi.youtube.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun `extracts id from watch url`() {
        assertThat(YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `extracts id from watch url with extra params`() {
        assertThat(YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&list=PL123"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `extracts id from short url`() {
        assertThat(YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=42"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `extracts id from shorts embed and live`() {
        assertThat(YouTubeUrlParser.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlParser.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlParser.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `accepts bare id with whitespace`() {
        assertThat(YouTubeUrlParser.extractVideoId("  dQw4w9WgXcQ ")).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `returns null for invalid input`() {
        assertThat(YouTubeUrlParser.extractVideoId("")).isNull()
        assertThat(YouTubeUrlParser.extractVideoId("not a url")).isNull()
        assertThat(YouTubeUrlParser.extractVideoId("https://example.com/watch?v=short")).isNull()
        assertThat(YouTubeUrlParser.extractVideoId("dQw4w9WgXc")).isNull()
    }

    @Test
    fun `builds watch url`() {
        assertThat(YouTubeUrlParser.buildWatchUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }
}
