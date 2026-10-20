package com.ioi.youtube.domain.model

enum class FeedTopic(val id: String, val label: String) {
    ALL("all", "Усі"),
    MUSIC("music", "Музика"),
    GAMES("games", "Ігри"),
    SPORT("sport", "Спорт"),
    FILMS("films", "Фільми"),
    TECH("tech", "Технології"),
    NEWS("news", "Новини"),
    HUMOR("humor", "Гумор"),
    AUTO("auto", "Авто"),
    FOOD("food", "Кулінарія"),
    TRAVEL("travel", "Подорожі"),
}

enum class FeedSort(val label: String) {
    RELEVANCE("Релевантність"),
    NEWEST("Нові"),
    POPULAR("Популярні"),
}

/** Один елемент стрічки: або відео, або блок Shorts. */
sealed interface FeedItem {
    data class Video(val video: VideoSummary) : FeedItem
    data class ShortsBlock(val id: String, val shorts: List<VideoSummary>) : FeedItem
}

data class FeedPage(
    val videos: List<VideoSummary>,
    val exhausted: Boolean = false,
)
