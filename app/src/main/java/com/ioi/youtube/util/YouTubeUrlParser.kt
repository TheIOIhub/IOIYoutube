package com.ioi.youtube.util

object YouTubeUrlParser {

    private val BARE_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private val PATTERNS = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""/live/([A-Za-z0-9_-]{11})"""),
    )

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        if (BARE_ID.matches(trimmed)) return trimmed
        return PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(trimmed)?.groupValues?.get(1)
        }
    }

    fun buildWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
}
