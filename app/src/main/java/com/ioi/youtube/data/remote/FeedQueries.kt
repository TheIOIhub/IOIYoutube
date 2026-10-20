package com.ioi.youtube.data.remote

import com.ioi.youtube.domain.model.FeedTopic
import kotlin.random.Random

/**
 * Запити, що представляють тему в пошуку YouTube.
 * Пул потрібен, бо видача стабільна: той самий запит = ті самі відео.
 */
internal val TOPIC_QUERIES: Map<FeedTopic, List<String>> = mapOf(
    FeedTopic.MUSIC to listOf("музика", "нова музика", "пісні", "музичний огляд"),
    FeedTopic.GAMES to listOf("ігри", "огляд ігор", "ігрові новини", "проходження ігор"),
    FeedTopic.SPORT to listOf("спорт", "футбол", "спортивні новини", "баскетбол"),
    FeedTopic.FILMS to listOf("фільми", "трейлери", "кіноогляд", "серіали"),
    FeedTopic.TECH to listOf("технології", "огляд техніки", "гаджети", "штучний інтелект"),
    FeedTopic.NEWS to listOf("новини", "новини України", "світові новини"),
    FeedTopic.HUMOR to listOf("гумор", "смішні відео", "приколи"),
    FeedTopic.AUTO to listOf("авто", "огляд авто", "автоновини"),
    FeedTopic.FOOD to listOf("кулінарія", "рецепти", "готування"),
    FeedTopic.TRAVEL to listOf("подорожі", "мандрівки", "цікаві місця"),
)

private val ALL_TOPICS: List<FeedTopic> = TOPIC_QUERIES.keys.toList()

/**
 * Запити на одну сторінку.
 * «Усі» — дві різні теми по одному випадковому запиту; тема — один випадковий запит.
 */
internal fun queriesFor(topic: FeedTopic, random: Random): List<String> = when (topic) {
    FeedTopic.ALL -> ALL_TOPICS.shuffled(random).take(2).map { it.randomQuery(random) }
    else -> listOf(topic.randomQuery(random))
}

private fun FeedTopic.randomQuery(random: Random): String =
    TOPIC_QUERIES.getValue(this).random(random)
