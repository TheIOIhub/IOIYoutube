# IOITube — Фаза 7: стрічка, теми, сортування, блоки Shorts (Implementation Plan)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Замінити пласку «Головну» на нескінченну стрічку з темами, клієнтським сортуванням і блоками Shorts після кожного 4-го відео; зробити пошук одноколонковим; навчити вкладку Shorts відкривати конкретне відео.

**Architecture:** NewPipe-джерело віддає `RawVideoSummary`-сторінки по випадкових запитах із пулів тем. `FeedRepositoryImpl` мапить у домен, сортує та кешує лише `ALL + RELEVANCE`. `HomeViewModel` збирає `FeedItem` (відео + блоки) і керує пагінацією/дедупом. UI — `LazyColumn` із чипами зверху.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3, BOM 2024.12.01), Hilt, Room, NewPipeExtractor 0.26.5, Robolectric + Truth + Turbine + MockK.

**Design spec:** `docs/superpowers/specs/2026-10-20-phase-7-feed-search-design.md`

---

## Global Constraints

- Усі команди Gradle — після `source .agent-env.sh` (JAVA_HOME/ANDROID_HOME живуть там).
- Тести: `./gradlew :app:testDebugUnitTest`. Один тест: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.<Class>"`.
- UI-рядки — українською, як у решті застосунку.
- Room-схема **не змінюється**: `uploadedAt` у кеш не пишеться.
- Не додавати нових бібліотек.
- Після кожної задачі — зелений `testDebugUnitTest` і окремий коміт.
- Наприкінці фази: `./gradlew :app:assembleDebug :app:assembleRelease` і оновити `dist/*.apk` + MD5 у `docs/`.
- Плаский список на «Головній», `FEED_CATEGORIES`, `VideoRepository.getHomeFeed()` видаляються повністю — без «сумісних» шляхів.

---

## Task 1: Домен стрічки + `uploadedAt`

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/model/Feed.kt`
- Create: `app/src/test/java/com/ioi/youtube/domain/model/FeedTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/domain/model/VideoSummary.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/mapper/RemoteMappers.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/mapper/CacheMappers.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/mapper/RemoteMappersTest.kt`

- [ ] **Step 1: Написати тести, що падають**

Створити `app/src/test/java/com/ioi/youtube/domain/model/FeedTest.kt`:

```kotlin
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
```

Додати в кінець `RemoteMappersTest`:

```kotlin
    @Test
    fun `maps upload timestamp to domain`() {
        val raw = RawVideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Title",
            uploaderName = "Channel",
            thumbnailUrl = "https://img/1.jpg",
            durationSeconds = 75,
            viewCount = 1500,
            uploadDate = "2 years ago",
            uploadedAt = 1_700_000_000_000,
        )

        assertThat(raw.toDomain().uploadedAt).isEqualTo(1_700_000_000_000)
    }
```

- [ ] **Step 2: Переконатися, що тести не компілюються (RED)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.domain.model.FeedTest"`
Expected: FAIL — `Unresolved reference: FeedTopic`, `uploadedAt`.

- [ ] **Step 3: Створити домен**

`Feed.kt`:

```kotlin
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
```

- [ ] **Step 4: Додати `uploadedAt` у моделі й мапери**

`VideoSummary` — нове поле в кінці (дефолт, щоб не ламати конструктори):

```kotlin
    val uploadDate: String?,
    /** Реальна дата публікації, epoch millis. У кеш не пишеться. */
    val uploadedAt: Long? = null,
```

`RawVideoSummary` — так само в кінці:

```kotlin
    /** URL каналу — потрiбен лише для стрiчки Shorts, у домен не йде. */
    val uploaderUrl: String = "",
    /** Реальна дата публікації, epoch millis. */
    val uploadedAt: Long? = null,
```

`RemoteMappers.kt`:

```kotlin
fun RawVideoSummary.toDomain(): VideoSummary = VideoSummary(
    // ...
    uploadDate = uploadDate,
    uploadedAt = uploadedAt,
)
```

`CacheMappers.kt` — у `CachedVideoEntity.toDomain()` додати `uploadedAt = null` (кеш не зберігає дату; сортування на кеші не застосовується).

`NewPipeRemoteDataSource.toRawSummary()` — додати:

```kotlin
        uploadedAt = uploadDate?.instant?.toEpochMilli(),
```

- [ ] **Step 5: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; нові тести проходять.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(feed): add feed topic, sort and item domain models"
```

---

## Task 2: Сторінки стрічки з пулів запитів + 1 шортс на канал

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/remote/FeedQueries.kt`
- Create: `app/src/test/java/com/ioi/youtube/data/remote/FeedQueriesTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/YouTubeRemoteDataSource.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`
- Modify: `app/src/main/java/com/ioi/youtube/di/DataModule.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/repository/VideoRepositoryImpl.kt` (тільки виклик `fetchFeedPage`)
- Modify: `app/src/test/java/com/ioi/youtube/testutil/FakeRemoteDataSource.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceSmokeTest.kt`

- [ ] **Step 1: Тести пулів запитів (RED)**

`FeedQueriesTest.kt`:

```kotlin
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
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.data.remote.FeedQueriesTest"`
Expected: FAIL — unresolved `TOPIC_QUERIES`, `queriesFor`.

- [ ] **Step 2: Реалізувати пули**

`FeedQueries.kt`:

```kotlin
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
```

- [ ] **Step 3: Оновити інтерфейс джерела**

`YouTubeRemoteDataSource.kt`:

```kotlin
    /** Одна сторінка стрічки для теми (для `ALL` — суміш двох тем). */
    suspend fun fetchFeedPage(topic: FeedTopic): List<RawVideoSummary>

    /** До `count` шортсів, максимум один з каналу, без `excludeIds`. */
    suspend fun fetchFeedShorts(count: Int, excludeIds: Set<String>): List<RawVideoSummary>
```

`fetchHomeFeed()` **видалити** з інтерфейсу.

- [ ] **Step 4: Реалізувати в NewPipeRemoteDataSource**

Замінити `fetchHomeFeed()` на:

```kotlin
    override suspend fun fetchFeedPage(topic: FeedTopic): List<RawVideoSummary> =
        withContext(Dispatchers.IO) {
            val pages = coroutineScope {
                queriesFor(topic, random)
                    .map { query -> async { runCatching { searchPage(query) } } }
                    .awaitAll()
            }
            val loaded = pages.mapNotNull { it.getOrNull() }
            if (loaded.isEmpty()) {
                throw pages.firstNotNullOf { it.exceptionOrNull() }
            }
            loaded.interleave().distinctBy { it.id }.take(FEED_PAGE_SIZE)
        }
```

Конструктор:

```kotlin
class NewPipeRemoteDataSource @Inject constructor(
    private val random: Random = Random.Default,
) : YouTubeRemoteDataSource {
```

`DataModule`:

```kotlin
    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default
```

Замінити `fetchShorts` / `fetchMoreShorts` / `channelsFrom` / `shortsFromChannels`:

```kotlin
    override suspend fun fetchShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        shortsFromChannels(channelsFrom(fetchFeedPage(FeedTopic.ALL)))
    }

    override suspend fun fetchMoreShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        shortsFromChannels(channelsFrom(search(SHORTS_QUERIES.random(random))))
    }

    override suspend fun fetchFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        if (count <= 0) return@withContext emptyList()
        val channels = channelsFrom(search(SHORTS_QUERIES.random(random)))
        shortsFromChannels(channels, limit = count, excludeIds = excludeIds)
    }

    /** Випадкові канали з відео; максимум [SHORTS_CHANNEL_LIMIT]. */
    private fun channelsFrom(videos: List<RawVideoSummary>): List<String> =
        videos.mapNotNull { it.uploaderUrl.takeIf(String::isNotBlank) }
            .distinct()
            .shuffled(random)
            .take(SHORTS_CHANNEL_LIMIT)

    /**
     * Рівно один випадковий шортс з каналу — щоб один канал не займав усю стрічку.
     * Помилки окремих каналів не ховають результати інших.
     */
    private suspend fun shortsFromChannels(
        channels: List<String>,
        limit: Int = Int.MAX_VALUE,
        excludeIds: Set<String> = emptySet(),
    ): List<RawVideoSummary> {
        if (channels.isEmpty()) return emptyList()
        val pages = coroutineScope {
            channels.map { channel -> async { runCatching { shortsOfChannel(channel) } } }.awaitAll()
        }
        val loaded = pages.mapNotNull { it.getOrNull() }
        if (loaded.isEmpty()) {
            throw pages.firstNotNullOf { it.exceptionOrNull() }
        }
        return loaded
            .mapNotNull { shorts -> shorts.filterNot { it.id in excludeIds }.randomOrNull(random) }
            .distinctBy { it.id }
            .shuffled(random)
            .take(limit)
    }
```

Константи: `FEED_SIZE` → `FEED_PAGE_SIZE = 20`, `SHORTS_CHANNEL_LIMIT = 10`, `FEED_CATEGORIES` **видалити**.

Імпорти: `com.ioi.youtube.domain.model.FeedTopic`, `kotlin.random.Random`.

- [ ] **Step 5: Оновити FakeRemoteDataSource і виклик у VideoRepositoryImpl**

`FakeRemoteDataSource`:

```kotlin
    var feed: List<RawVideoSummary> = emptyList()
    val feedByTopic = mutableMapOf<FeedTopic, List<RawVideoSummary>>()
    var feedError: Exception? = null
    var feedShorts: List<RawVideoSummary> = emptyList()
    var feedShortsError: Exception? = null
    var feedCalls = 0
        private set
    var feedShortsCalls = 0
        private set
    var lastFeedTopic: FeedTopic? = null
        private set
    var lastShortExcludeIds: Set<String> = emptySet()
        private set

    override suspend fun fetchFeedPage(topic: FeedTopic): List<RawVideoSummary> {
        feedCalls++
        lastFeedTopic = topic
        feedError?.let { throw it }
        return feedByTopic[topic] ?: feed
    }

    override suspend fun fetchFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): List<RawVideoSummary> {
        feedShortsCalls++
        lastShortExcludeIds = excludeIds
        feedShortsError?.let { throw it }
        return feedShorts.filterNot { it.id in excludeIds }.take(count)
    }
```

`VideoRepositoryImpl.getHomeFeed()` (тимчасово, до Task 4):

```kotlin
            remote.fetchFeedPage(FeedTopic.ALL)
```

`NewPipeRemoteDataSourceSmokeTest` — замінити виклик `fetchHomeFeed()` на:

```kotlin
        val feed = dataSource.fetchFeedPage(FeedTopic.ALL)
```

(імпорт `com.ioi.youtube.domain.model.FeedTopic`; решта перевірок без змін).

- [ ] **Step 6: Переписати тести NewPipeRemoteDataSource**

Видалити три тести `home feed ...` і додати:

```kotlin
    @Test
    fun `feed page searches with a query from the topic pool`() = runTest {
        val search = searchExtractor(listOf(item("aaaaaaaaaaa", "Game")))
        val service = mockk<StreamingService>(relaxed = true)
        val asked = slot<String>()
        every { service.getSearchExtractor(capture(asked)) } returns search
        stubYouTubeService(service)

        val page = NewPipeRemoteDataSource(Random(1)).fetchFeedPage(FeedTopic.GAMES)

        assertThat(page.map { it.id }).containsExactly("aaaaaaaaaaa")
        assertThat(asked.captured).isIn(TOPIC_QUERIES.getValue(FeedTopic.GAMES))
        verifyOrder { search.fetchPage(); search.initialPage }
    }

    @Test
    fun `all feed page uses two different queries and drops duplicates`() = runTest {
        val search = searchExtractor(listOf(item("aaaaaaaaaaa", "A"), item("bbbbbbbbbbb", "B")))
        val service = mockk<StreamingService>(relaxed = true)
        val asked = mutableListOf<String>()
        every { service.getSearchExtractor(capture(asked)) } returns search
        stubYouTubeService(service)

        val page = NewPipeRemoteDataSource(Random(2)).fetchFeedPage(FeedTopic.ALL)

        assertThat(asked).hasSize(2)
        assertThat(asked.toSet()).hasSize(2)
        assertThat(page.map { it.id }).containsExactly("aaaaaaaaaaa", "bbbbbbbbbbb")
    }

    @Test
    fun `feed page reports failure when every query fails`() = runTest {
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(failWith = IOException("offline"))
        stubYouTubeService(service)

        val thrown = runCatching {
            NewPipeRemoteDataSource(Random(1)).fetchFeedPage(FeedTopic.NEWS)
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(thrown).hasMessageThat().isEqualTo("offline")
    }

    @Test
    fun `feed shorts take at most one short per channel`() = runTest {
        val tab1 = shortsTab(
            listOf(
                shortItem("s1111111111", "https://www.youtube.com/UC1"),
                shortItem("s2222222222", "https://www.youtube.com/UC1"),
            ),
        )
        val tab2 = shortsTab(
            listOf(
                shortItem("s3333333333", "https://www.youtube.com/UC2"),
                shortItem("s4444444444", "https://www.youtube.com/UC2"),
            ),
        )
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns searchExtractor(
            listOf(
                homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1"),
                homeItem("iiiiiiiiiii", "https://www.youtube.com/UC2"),
            ),
        )
        every { service.getChannelExtractor("https://www.youtube.com/UC1") } returns
            channelExtractor("https://www.youtube.com/UC1/shorts")
        every { service.getChannelExtractor("https://www.youtube.com/UC2") } returns
            channelExtractor("https://www.youtube.com/UC2/shorts")
        every { service.getChannelTabExtractor(match { it.url.contains("UC1") }) } returns tab1
        every { service.getChannelTabExtractor(match { it.url.contains("UC2") }) } returns tab2
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource(Random(3))
            .fetchFeedShorts(count = 2, excludeIds = emptySet())

        assertThat(shorts).hasSize(2)
        assertThat(shorts.map { it.uploaderUrl })
            .containsExactly("https://www.youtube.com/UC1", "https://www.youtube.com/UC2")
        assertThat(shorts.map { it.id }.toSet()).hasSize(2)
    }

    @Test
    fun `feed shorts skip excluded ids`() = runTest {
        val tab = shortsTab(listOf(shortItem("s1111111111", "https://www.youtube.com/UC1")))
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns searchExtractor(
            listOf(homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1")),
        )
        every { service.getChannelExtractor(any()) } returns
            channelExtractor("https://www.youtube.com/UC1/shorts")
        every { service.getChannelTabExtractor(any()) } returns tab
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource(Random(4))
            .fetchFeedShorts(count = 2, excludeIds = setOf("s1111111111"))

        assertThat(shorts).isEmpty()
    }
```

Додати хелпер поруч із `homeItem`:

```kotlin
    private fun shortItem(id: String, channelUrl: String): StreamInfoItem =
        homeItem(id, channelUrl).apply { isShortFormContent = true }
```

Імпорти в тест: `com.ioi.youtube.domain.model.FeedTopic`, `kotlin.random.Random` (решта вже є).

- [ ] **Step 7: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Якщо `VideoRepositoryImplTest` падає через зміну `fetchHomeFeed` — він має пройти, бо фейк тепер мапить `fetchFeedPage(ALL)` на `feed`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(feed): fetch random feed pages and one short per channel"
```

---

## Task 3: `FeedRepository`, сортування, кеш, use cases

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/repository/FeedRepository.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/repository/FeedRepositoryImpl.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetFeedPageUseCase.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetFeedShortsUseCase.kt`
- Create: `app/src/test/java/com/ioi/youtube/data/repository/FeedRepositoryImplTest.kt`
- Create: `app/src/test/java/com/ioi/youtube/data/repository/FeedSortingTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/di/RepositoryModule.kt`

- [ ] **Step 1: Тести сортування (RED)**

`FeedSortingTest.kt`:

```kotlin
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
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.data.repository.FeedSortingTest"`
Expected: FAIL — `sortVideos` unresolved.

- [ ] **Step 2: Тести репозиторію (RED)**

`FeedRepositoryImplTest.kt` (патерн узятий із `VideoRepositoryImplTest`):

```kotlin
package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: FeedRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        remote = FakeRemoteDataSource()
        repository = FeedRepositoryImpl(remote, videoDao)
    }

    @After
    fun tearDown() = database.close()

    private fun raw(id: String, views: Long = 10, uploadedAt: Long? = null) = RawVideoSummary(
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
    fun `all with relevance emits cache then fresh and writes cache`() = runTest {
        videoDao.replaceSource(
            FeedRepositoryImpl.SOURCE_HOME,
            listOf(
                com.ioi.youtube.data.local.CachedVideoEntity(
                    id = "cached1",
                    sourceKey = FeedRepositoryImpl.SOURCE_HOME,
                    title = "cached1",
                    uploaderName = "Ch",
                    thumbnailUrl = "u",
                    durationSeconds = 1,
                    viewCount = 10,
                    uploadDate = null,
                    cachedAt = 0,
                ),
            ),
        )
        remote.feed = listOf(raw("fresh1"))

        repository.getFeedPage(FeedTopic.ALL, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.videos.map { it.id }).containsExactly("cached1")

            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.fromCache).isFalse()
            assertThat(fresh.data.videos.map { it.id }).containsExactly("fresh1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(FeedRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("fresh1")
    }

    @Test
    fun `topic feed ignores and does not write the cache`() = runTest {
        videoDao.replaceSource(
            FeedRepositoryImpl.SOURCE_HOME,
            listOf(
                com.ioi.youtube.data.local.CachedVideoEntity(
                    id = "cached1",
                    sourceKey = FeedRepositoryImpl.SOURCE_HOME,
                    title = "cached1",
                    uploaderName = "Ch",
                    thumbnailUrl = "u",
                    durationSeconds = 1,
                    viewCount = 10,
                    uploadDate = null,
                    cachedAt = 0,
                ),
            ),
        )
        remote.feedByTopic[FeedTopic.GAMES] = listOf(raw("game1"))

        repository.getFeedPage(FeedTopic.GAMES, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.videos.map { it.id }).containsExactly("game1")
            awaitComplete()
        }

        assertThat(videoDao.getBySource(FeedRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("cached1")
    }

    @Test
    fun `newest sort is applied to fresh videos`() = runTest {
        remote.feed = listOf(raw("old", uploadedAt = 100), raw("new", uploadedAt = 300))

        repository.getFeedPage(FeedTopic.ALL, FeedSort.NEWEST).test {
            awaitItem()
            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.videos.map { it.id }).containsExactly("new", "old").inOrder()
            awaitComplete()
        }
    }

    @Test
    fun `error is emitted only when the cache is empty`() = runTest {
        remote.feedError = IOException("offline")

        repository.getFeedPage(FeedTopic.ALL, FeedSort.RELEVANCE).test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(FeedRepositoryImpl.ERROR_LOAD_FEED)
            awaitComplete()
        }
    }

    @Test
    fun `feed shorts pass exclude ids and map to domain`() = runTest {
        remote.feedShorts = listOf(raw("short1"))

        repository.getFeedShorts(count = 4, excludeIds = setOf("seen1")).test {
            awaitItem()
            val success = awaitItem() as UiState.Success
            assertThat(success.data.map { it.id }).containsExactly("short1")
            awaitComplete()
        }

        assertThat(remote.lastShortExcludeIds).containsExactly("seen1")
    }

    @Test
    fun `feed shorts error maps to shorts message`() = runTest {
        remote.feedShortsError = IOException("offline")

        repository.getFeedShorts(2, emptySet()).test {
            awaitItem()
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(FeedRepositoryImpl.ERROR_LOAD_FEED_SHORTS)
            awaitComplete()
        }
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.data.repository.FeedRepositoryImplTest"`
Expected: FAIL — `FeedRepositoryImpl` unresolved.

- [ ] **Step 3: Реалізувати домен і репозиторій**

`FeedRepository.kt`:

```kotlin
package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>>

    fun getFeedShorts(count: Int, excludeIds: Set<String>): Flow<UiState<List<VideoSummary>>>
}
```

`FeedRepositoryImpl.kt`:

```kotlin
package com.ioi.youtube.data.repository

import android.util.Log
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IOITube"

/**
 * Кешуємо лише першу сторінку «Усі + Релевантність»: це єдиний режим,
 * який показується без мережі. Для теми/сортування кеш не читається й не пишеться.
 */
@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
) : FeedRepository {

    override fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> = flow {
        emit(UiState.Loading)
        val usesCache = topic == FeedTopic.ALL && sort == FeedSort.RELEVANCE
        val cached = if (usesCache) cachedVideoDao.getBySource(SOURCE_HOME) else emptyList()
        if (cached.isNotEmpty()) {
            emit(
                UiState.Success(
                    FeedPage(sortVideos(cached.map { it.toDomain() }, sort)),
                    fromCache = true,
                ),
            )
        }
        val fresh = try {
            remote.fetchFeedPage(topic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "feed page request failed", e)
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_FEED, e))
            return@flow
        }
        val sorted = sortVideos(fresh.map { it.toDomain() }, sort)
        if (usesCache) {
            runCatching {
                cachedVideoDao.replaceSource(
                    SOURCE_HOME,
                    sorted.map { it.toEntity(SOURCE_HOME, System.currentTimeMillis()) },
                )
            }
        }
        emit(UiState.Success(FeedPage(sorted)))
    }.flowOn(Dispatchers.IO)

    override fun getFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val shorts = try {
            remote.fetchFeedShorts(count, excludeIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "feed shorts request failed", e)
            emit(UiState.Error(ERROR_LOAD_FEED_SHORTS, e))
            return@flow
        }
        emit(UiState.Success(shorts.map { it.toDomain() }))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_HOME = "home"
        const val ERROR_LOAD_FEED = "Не вдалося завантажити стрічку"
        const val ERROR_LOAD_FEED_SHORTS = "Не вдалося завантажити шортси"
    }
}

/** Сортування на клієнті: серверне сортування YouTube у 0.26.5 недоступне. */
internal fun sortVideos(videos: List<VideoSummary>, sort: FeedSort): List<VideoSummary> =
    when (sort) {
        FeedSort.RELEVANCE -> videos
        FeedSort.NEWEST -> videos.sortedByDescending { it.uploadedAt ?: Long.MIN_VALUE }
        FeedSort.POPULAR -> videos.sortedByDescending { it.viewCount }
    }
```

Use cases:

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFeedPageUseCase @Inject constructor(
    private val repository: FeedRepository,
) {
    operator fun invoke(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> =
        repository.getFeedPage(topic, sort)
}
```

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFeedShortsUseCase @Inject constructor(
    private val repository: FeedRepository,
) {
    operator fun invoke(count: Int, excludeIds: Set<String>): Flow<UiState<List<VideoSummary>>> =
        repository.getFeedShorts(count, excludeIds)
}
```

`RepositoryModule`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindFeedRepository(impl: FeedRepositoryImpl): FeedRepository
```

- [ ] **Step 4: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(feed): add feed repository with client-side sorting and cache policy"
```

---

## Task 4: `HomeViewModel` + `HomeScreen` (чипи, блоки, нескінченність) і видалення старої стрічки

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt` (тимчасовий `onShortClick`)
- Modify: `app/src/test/java/com/ioi/youtube/testutil/FakeRepositories.kt` (прибрати `getHomeFeed`, додати `FakeFeedRepository` в окремому файлі)
- Create: `app/src/test/java/com/ioi/youtube/testutil/FakeFeedRepository.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/home/HomeViewModelTest.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/home/HomeScreenTest.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/repository/VideoRepositoryImplTest.kt` (видалити тести `home feed ...`)
- Delete: `app/src/main/java/com/ioi/youtube/domain/usecase/GetHomeFeedUseCase.kt`
- Delete: `app/src/test/java/com/ioi/youtube/domain/usecase/GetHomeFeedUseCaseTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/domain/repository/VideoRepository.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/repository/VideoRepositoryImpl.kt`

- [ ] **Step 1: Фейк і тести ViewModel (RED)**

`FakeFeedRepository.kt`:

```kotlin
package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeFeedRepository : FeedRepository {

    private val pages = ArrayDeque<UiState<FeedPage>>()
    private val shortsPages = ArrayDeque<UiState<List<VideoSummary>>>()

    var feedCalls = 0
        private set
    var shortsCalls = 0
        private set
    var lastTopic: FeedTopic? = null
        private set
    var lastSort: FeedSort? = null
        private set
    var lastShortsCount = 0
        private set
    var lastShortExcludeIds: Set<String> = emptySet()
        private set

    fun enqueuePage(state: UiState<FeedPage>) {
        pages.addLast(state)
    }

    fun enqueueShorts(state: UiState<List<VideoSummary>>) {
        shortsPages.addLast(state)
    }

    override fun getFeedPage(topic: FeedTopic, sort: FeedSort): Flow<UiState<FeedPage>> {
        feedCalls++
        lastTopic = topic
        lastSort = sort
        val state = if (pages.isEmpty()) {
            UiState.Success(FeedPage(emptyList(), exhausted = true))
        } else {
            pages.removeFirst()
        }
        return flowOf(state)
    }

    override fun getFeedShorts(
        count: Int,
        excludeIds: Set<String>,
    ): Flow<UiState<List<VideoSummary>>> {
        shortsCalls++
        lastShortsCount = count
        lastShortExcludeIds = excludeIds
        val state = if (shortsPages.isEmpty()) UiState.Success(emptyList()) else shortsPages.removeFirst()
        return flowOf(state)
    }
}
```

`HomeViewModelTest.kt` — повністю замінити:

```kotlin
package com.ioi.youtube.presentation.home

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.GetFeedPageUseCase
import com.ioi.youtube.domain.usecase.GetFeedShortsUseCase
import com.ioi.youtube.testutil.FakeFeedRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeFeedRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getFeedPage = GetFeedPageUseCase(repository),
        getFeedShorts = GetFeedShortsUseCase(repository),
    )

    private fun videos(count: Int) = (1..count).map { index ->
        TestData.video(id = "video0000$index")
    }

    private fun shorts(count: Int) = (1..count).map { index ->
        TestData.video(id = "short0000$index")
    }

    @Test
    fun `page adds a shorts block after every fourth video and fills two shorts`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(9))))
            repository.enqueueShorts(UiState.Success(shorts(4)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val items = viewModel.uiState.value.items
            assertThat(items.filterIsInstance<FeedItem.Video>()).hasSize(9)
            assertThat(items.filterIsInstance<FeedItem.ShortsBlock>()).hasSize(2)
            assertThat(items[4]).isInstanceOf(FeedItem.ShortsBlock::class.java)
            assertThat(items[9]).isInstanceOf(FeedItem.ShortsBlock::class.java)
            items.filterIsInstance<FeedItem.ShortsBlock>().forEach {
                assertThat(it.shorts).hasSize(2)
            }
            assertThat(repository.lastShortsCount).isEqualTo(4)
        }

    @Test
    fun `load more appends fresh videos and another block`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Success(FeedPage(videos(8).drop(4))))
        repository.enqueueShorts(UiState.Success(shorts(4)))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertThat(items.filterIsInstance<FeedItem.Video>()).hasSize(8)
        assertThat(items.filterIsInstance<FeedItem.ShortsBlock>()).hasSize(2)
        assertThat(viewModel.uiState.value.loadingMore).isFalse()
        assertThat(repository.feedCalls).isEqualTo(2)
    }

    @Test
    fun `load more retries duplicate-only pages three times then marks exhausted`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
            repository.enqueueShorts(UiState.Success(shorts(2)))
            repeat(3) { repository.enqueuePage(UiState.Success(FeedPage(videos(4)))) }

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.exhausted).isTrue()
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
            assertThat(repository.feedCalls).isEqualTo(4)
        }

    @Test
    fun `load more error keeps the feed and exposes retry`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Error("Не вдалося завантажити стрічку"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
        assertThat(viewModel.uiState.value.loadMoreError).isEqualTo("Не вдалося завантажити стрічку")
        assertThat(viewModel.uiState.value.loadingMore).isFalse()
    }

    @Test
    fun `topic change resets items and loads the new topic`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
        repository.enqueueShorts(UiState.Success(shorts(2)))
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectTopic(FeedTopic.GAMES)
        advanceUntilIdle()

        assertThat(repository.lastTopic).isEqualTo(FeedTopic.GAMES)
        assertThat(viewModel.uiState.value.topic).isEqualTo(FeedTopic.GAMES)
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun `sort change reloads and keeps the selected topic`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))
        repository.enqueuePage(UiState.Success(FeedPage(emptyList())))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectTopic(FeedTopic.TECH)
        advanceUntilIdle()
        viewModel.selectSort(FeedSort.POPULAR)
        advanceUntilIdle()

        assertThat(repository.lastSort).isEqualTo(FeedSort.POPULAR)
        assertThat(repository.lastTopic).isEqualTo(FeedTopic.TECH)
        assertThat(viewModel.uiState.value.sort).isEqualTo(FeedSort.POPULAR)
    }

    @Test
    fun `initial error is exposed with the message`() = runTest(testDispatcher) {
        repository.enqueuePage(UiState.Error("Не вдалося завантажити стрічку"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Не вдалося завантажити стрічку")
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `block without shorts is removed when the shorts request fails`() =
        runTest(testDispatcher) {
            repository.enqueuePage(UiState.Success(FeedPage(videos(4))))
            repository.enqueueShorts(UiState.Error("Не вдалося завантажити шортси"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.ShortsBlock>()).isEmpty()
            assertThat(viewModel.uiState.value.items.filterIsInstance<FeedItem.Video>()).hasSize(4)
        }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.home.HomeViewModelTest"`
Expected: FAIL — `HomeUiState`/`FeedItem` у старому ViewModel не збігаються з тестом.

- [ ] **Step 2: Реалізувати HomeViewModel**

Повністю замінити `HomeViewModel.kt`:

```kotlin
package com.ioi.youtube.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedPage
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.usecase.GetFeedPageUseCase
import com.ioi.youtube.domain.usecase.GetFeedShortsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val topic: FeedTopic = FeedTopic.ALL,
    val sort: FeedSort = FeedSort.RELEVANCE,
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val exhausted: Boolean = false,
    val fromCache: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getFeedPage: GetFeedPageUseCase,
    private val getFeedShorts: GetFeedShortsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val shownVideoIds = mutableSetOf<String>()
    private val shownShortIds = mutableSetOf<String>()
    private var videosSinceBlock = 0
    private var loadJob: Job? = null

    init {
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun selectTopic(topic: FeedTopic) {
        if (topic == _uiState.value.topic) return
        _uiState.update { it.copy(topic = topic) }
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun selectSort(sort: FeedSort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        loadFirstPage(clear = true, freshOnly = false)
    }

    fun retry() = loadFirstPage(clear = true, freshOnly = false)

    /** Pull-to-refresh: список не очищаємо, кеш не показуємо. */
    fun refresh() = loadFirstPage(clear = false, freshOnly = true)

    private fun loadFirstPage(clear: Boolean, freshOnly: Boolean) {
        loadJob?.cancel()
        shownVideoIds.clear()
        shownShortIds.clear()
        videosSinceBlock = 0
        _uiState.update {
            it.copy(
                items = if (clear) emptyList() else it.items,
                loading = true,
                loadingMore = false,
                error = null,
                loadMoreError = null,
                exhausted = false,
                fromCache = false,
            )
        }
        val topic = _uiState.value.topic
        val sort = _uiState.value.sort
        loadJob = viewModelScope.launch {
            when (val state = awaitPage(topic, sort, freshOnly)) {
                is UiState.Error -> _uiState.update {
                    it.copy(loading = false, error = state.message)
                }

                is UiState.Success -> {
                    val fresh = state.data.videos.filterNot { it.id in shownVideoIds }
                    shownVideoIds += fresh.map { it.id }
                    _uiState.update {
                        it.copy(
                            items = appendVideos(emptyList(), fresh),
                            loading = false,
                            error = null,
                            fromCache = state.fromCache,
                        )
                    }
                    loadShortsForNewBlocks()
                }

                UiState.Loading -> Unit
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.exhausted || state.items.isEmpty()) return
        _uiState.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            repeat(MAX_EMPTY_ATTEMPTS) {
                when (val page = awaitPage(state.topic, state.sort, freshOnly = true)) {
                    is UiState.Error -> {
                        _uiState.update { it.copy(loadingMore = false, loadMoreError = page.message) }
                        return@launch
                    }

                    is UiState.Success -> {
                        val fresh = page.data.videos.filterNot { it.id in shownVideoIds }
                        if (fresh.isNotEmpty()) {
                            shownVideoIds += fresh.map { it.id }
                            _uiState.update { current ->
                                current.copy(
                                    items = appendVideos(current.items, fresh),
                                    loadingMore = false,
                                )
                            }
                            loadShortsForNewBlocks()
                            return@launch
                        }
                    }

                    UiState.Loading -> Unit
                }
            }
            _uiState.update { it.copy(loadingMore = false, exhausted = true) }
        }
    }

    /**
     * Бере перший «корисний» стан: для refresh — не кеш, для решти — кеш теж ок.
     */
    private suspend fun awaitPage(
        topic: FeedTopic,
        sort: FeedSort,
        freshOnly: Boolean,
    ): UiState<FeedPage> = getFeedPage(topic, sort)
        .filter { state ->
            state !is UiState.Loading &&
                (!freshOnly || (state as? UiState.Success)?.fromCache != true)
        }
        .first()

    private fun appendVideos(target: List<FeedItem>, videos: List<VideoSummary>): List<FeedItem> {
        val items = target.toMutableList()
        videos.forEach { video ->
            items += FeedItem.Video(video)
            videosSinceBlock++
            if (videosSinceBlock % VIDEOS_PER_BLOCK == 0) {
                items += FeedItem.ShortsBlock(
                    id = "shorts-$videosSinceBlock",
                    shorts = emptyList(),
                )
            }
        }
        return items
    }

    /**
     * Один запит шортсів на сторінку: `2 × кількість нових блоків`.
     * Порожні блоки (не вистачило шортсів або помилка) прибираємо, щоб не було дір.
     */
    private suspend fun loadShortsForNewBlocks() {
        val blocks = _uiState.value.items
            .filterIsInstance<FeedItem.ShortsBlock>()
            .filter { it.shorts.isEmpty() }
        if (blocks.isEmpty()) return

        val state = getFeedShorts(
            blocks.size * SHORTS_PER_BLOCK,
            shownShortIds.toSet(),
        ).filter { it is UiState.Success || it is UiState.Error }.first()

        if (state is UiState.Error) {
            _uiState.update { current ->
                current.copy(
                    items = current.items.filterNot {
                        it is FeedItem.ShortsBlock && it.shorts.isEmpty()
                    },
                )
            }
            return
        }

        val shorts = (state as UiState.Success<List<VideoSummary>>).data
        shownShortIds += shorts.map { it.id }
        val filled = mutableMapOf<String, List<VideoSummary>>()
        var cursor = 0
        blocks.forEach { block ->
            val slice = shorts.drop(cursor).take(SHORTS_PER_BLOCK)
            cursor += slice.size
            if (slice.isNotEmpty()) filled[block.id] = slice
        }

        _uiState.update { current ->
            current.copy(
                items = current.items.mapNotNull { item ->
                    when {
                        item is FeedItem.ShortsBlock && filled.containsKey(item.id) ->
                            item.copy(shorts = filled.getValue(item.id))

                        item is FeedItem.ShortsBlock && item.shorts.isEmpty() -> null
                        else -> item
                    }
                },
            )
        }
    }

    private companion object {
        const val VIDEOS_PER_BLOCK = 4
        const val SHORTS_PER_BLOCK = 2
        const val MAX_EMPTY_ATTEMPTS = 3
    }
}
```

- [ ] **Step 3: Переписати HomeScreen (тести спершу)**

`HomeScreenTest.kt` — повністю замінити:

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        uiState: HomeUiState,
        onVideoClick: (String) -> Unit = {},
        onShortClick: (String) -> Unit = {},
        onTopicSelected: (FeedTopic) -> Unit = {},
        onSortSelected: (FeedSort) -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = uiState,
                    onVideoClick = onVideoClick,
                    onShortClick = onShortClick,
                    onTopicSelected = onTopicSelected,
                    onSortSelected = onSortSelected,
                    onLoadMore = onLoadMore,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun `topic chip selection is propagated`() {
        var selected: FeedTopic? = null
        show(HomeUiState(items = emptyList(), loading = false), onTopicSelected = { selected = it })

        composeRule.onNodeWithTag("topic-chips").performScrollToNode(hasText("Ігри"))
        composeRule.onNodeWithText("Ігри").performClick()

        assertThat(selected).isEqualTo(FeedTopic.GAMES)
    }

    @Test
    fun `sort menu selects popular`() {
        var selected: FeedSort? = null
        show(HomeUiState(items = emptyList(), loading = false), onSortSelected = { selected = it })

        composeRule.onNodeWithTag("topic-chips").performScrollToNode(hasText("Релевантність"))
        composeRule.onNodeWithText("Релевантність").performClick()
        composeRule.onNodeWithText("Популярні").performClick()

        assertThat(selected).isEqualTo(FeedSort.POPULAR)
    }

    @Test
    fun `shorts block shows two cards and opens the tapped short`() {
        var clicked: String? = null
        val block = FeedItem.ShortsBlock(
            id = "shorts-4",
            shorts = listOf(
                TestData.video(id = "shortAAAAA", title = "First short"),
                TestData.video(id = "shortBBBBB", title = "Second short"),
            ),
        )
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Video")), block),
                loading = false,
            ),
            onShortClick = { clicked = it },
        )

        composeRule.onNodeWithText("YouTube Shorts").assertExists()
        composeRule.onNodeWithText("First short").assertExists()
        composeRule.onNodeWithText("Second short").performClick()

        assertThat(clicked).isEqualTo("shortBBBBB")
    }

    @Test
    fun `video click is propagated`() {
        var clicked: String? = null
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(id = "aaaaaaaaaaa", title = "First video"))),
                loading = false,
            ),
            onVideoClick = { clicked = it },
        )

        composeRule.onNodeWithText("First video").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }

    @Test
    fun `cached feed shows offline banner`() {
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Cached video"))),
                loading = false,
                fromCache = true,
            ),
        )

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `empty error state shows message and retry`() {
        var retried = false
        show(HomeUiState(loading = false, error = "Не вдалося завантажити стрічку"), onRetry = { retried = true })

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `load more error keeps items and offers retry`() {
        var retried = false
        show(
            HomeUiState(
                items = listOf(FeedItem.Video(TestData.video(title = "Kept video"))),
                loading = false,
                loadMoreError = "Не вдалося завантажити стрічку",
            ),
            onLoadMore = { retried = true },
        )

        composeRule.onNodeWithText("Kept video").assertExists()
        composeRule.onNodeWithText("Повторити").performClick()

        assertThat(retried).isTrue()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.home.HomeScreenTest"`
Expected: FAIL — сигнатура `HomeScreen` інша.

- [ ] **Step 4: Реалізувати HomeScreen**

Повністю замінити `HomeScreen.kt`:

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.FeedItem
import com.ioi.youtube.domain.model.FeedSort
import com.ioi.youtube.domain.model.FeedTopic
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard

@Composable
fun HomeRoute(
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onShortClick = onShortClick,
        onTopicSelected = viewModel::selectTopic,
        onSortSelected = viewModel::selectSort,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    onTopicSelected: (FeedTopic) -> Unit,
    onSortSelected: (FeedSort) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopicChips(
            selectedTopic = uiState.topic,
            sort = uiState.sort,
            onTopicSelected = onTopicSelected,
            onSortSelected = onSortSelected,
        )

        when {
            uiState.error != null && uiState.items.isEmpty() -> ErrorBox(
                message = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            uiState.loading && uiState.items.isEmpty() -> LoadingBox()

            uiState.items.isEmpty() && !uiState.loading -> Text(
                text = "Тут поки що порожньо",
                modifier = Modifier.fillMaxSize().padding(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> FeedList(
                uiState = uiState,
                onVideoClick = onVideoClick,
                onShortClick = onShortClick,
                onLoadMore = onLoadMore,
                onRefresh = onRefresh,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    uiState: HomeUiState,
    onVideoClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, uiState.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (lastVisible >= uiState.items.lastIndex - 4) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = uiState.loading && uiState.items.isNotEmpty(),
        onRefresh = onRefresh,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (uiState.fromCache) {
                item(key = "offline-banner") { OfflineBanner() }
            }
            items(
                items = uiState.items,
                key = { item ->
                    when (item) {
                        is FeedItem.Video -> item.video.id
                        is FeedItem.ShortsBlock -> item.id
                    }
                },
            ) { item ->
                when (item) {
                    is FeedItem.Video -> VideoCard(video = item.video, onClick = onVideoClick)
                    is FeedItem.ShortsBlock -> if (item.shorts.isNotEmpty()) {
                        ShortsBlockRow(block = item, onShortClick = onShortClick)
                    }
                }
            }
            item(key = "footer") {
                FeedFooter(uiState = uiState, onLoadMore = onLoadMore)
            }
        }
    }
}

@Composable
private fun FeedFooter(
    uiState: HomeUiState,
    onLoadMore: () -> Unit,
) {
    when {
        uiState.loadingMore -> Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.loadMoreError != null -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = uiState.loadMoreError,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onLoadMore) { Text("Повторити") }
        }

        uiState.exhausted -> Text(
            text = "Більше немає",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TopicChips(
    selectedTopic: FeedTopic,
    sort: FeedSort,
    onTopicSelected: (FeedTopic) -> Unit,
    onSortSelected: (FeedSort) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("topic-chips"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = FeedTopic.entries, key = { it.id }) { topic ->
            FilterChip(
                selected = topic == selectedTopic,
                onClick = { onTopicSelected(topic) },
                label = { Text(topic.label) },
            )
        }
        item(key = "sort") {
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                FilterChip(
                    selected = sort != FeedSort.RELEVANCE,
                    onClick = { menuOpen = true },
                    label = { Text(sort.label) },
                    leadingIcon = {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                    },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    FeedSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortSelected(option)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsBlockRow(
    block: FeedItem.ShortsBlock,
    onShortClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SmartDisplay,
                contentDescription = null,
                tint = Color(0xFFFF0033),
            )
            Text(
                text = "YouTube Shorts",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.shorts.forEach { short ->
                ShortCard(
                    short = short,
                    onClick = onShortClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ShortCard(
    short: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(short.id) },
    ) {
        AsyncImage(
            model = short.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        ) {
            Text(
                text = short.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${short.uploaderName} · ${Formatters.formatViews(short.viewCount)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 5: Прибрати старий шлях `getHomeFeed`**

- `IOITubeApp.kt`: `HomeRoute(onVideoClick = ..., onShortClick = { navController.navigate(Routes.SHORTS) })` — тимчасово, до Task 6 (відкриває вкладку без конкретного відео).
- `VideoRepository.kt`: видалити `getHomeFeed()`.
- `VideoRepositoryImpl.kt`: видалити `getHomeFeed()` і константи `SOURCE_HOME`/`ERROR_LOAD_FEED` з companion.
- `FakeRepositories.kt`: у `FakeVideoRepository` видалити `feed`, `feedCalls`, `setFeed`, `getHomeFeed`.
- `VideoRepositoryImplTest.kt`: видалити всі тести `home feed ...` і хелпери, що лишились без вжитку (`rawVideo`, `videoEntity`, `videoDao`, якщо більше не потрібні).
- Видалити `GetHomeFeedUseCase.kt` і `GetHomeFeedUseCaseTest.kt`.

- [ ] **Step 6: GREEN і повний прогін**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(home): rebuild feed with topics, sorting and shorts blocks"
```

---

## Task 5: Пошук в один стовпець

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/search/SearchScreen.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/search/SearchScreenTest.kt`
- Delete: `app/src/main/java/com/ioi/youtube/presentation/components/VideoGridCard.kt`
- Delete: `app/src/test/java/com/ioi/youtube/presentation/components/VideoGridCardTest.kt` (якщо існує)

- [ ] **Step 1: Оновити тест (RED)**

У `SearchScreenTest` додати:

```kotlin
    @Test
    fun `results are shown in one column`() {
        val results = SearchResults(
            query = "kotlin",
            videos = listOf(
                TestData.video(id = "aaaaaaaaaaa", title = "First result"),
                TestData.video(id = "bbbbbbbbb", title = "Second result"),
            ),
        )

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(results),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        val first = composeRule.onNodeWithText("First result").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Second result").fetchSemanticsNode().boundsInRoot

        assertThat(second.top).isGreaterThan(first.top)
        assertThat(second.left).isEqualTo(first.left)
    }
```

Імпорти: `androidx.compose.ui.test.fetchSemanticsNode`, `com.ioi.youtube.domain.model.SearchResults`, `com.ioi.youtube.testutil.TestData`.

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.search.SearchScreenTest"`
Expected: FAIL — картки в сітці стоять поруч (`left` різний).

- [ ] **Step 2: Замінити сітку на список**

У `SearchScreen.kt`:
- видалити імпорти `LazyVerticalGrid`, `GridCells`, `grid.items`, `VideoGridCard`;
- додати `androidx.compose.foundation.lazy.LazyColumn`, `androidx.compose.foundation.lazy.items`, `VideoCard`;
- замінити блок `LazyVerticalGrid(columns = GridCells.Fixed(2), ...)` на:

```kotlin
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (uiState.fromCache) {
                                item(key = "offline-banner") { OfflineBanner() }
                            }
                            items(items = uiState.data.videos, key = { it.id }) { video ->
                                VideoCard(video = video, onClick = onVideoClick)
                            }
                        }
```

- [ ] **Step 3: Видалити `VideoGridCard`**

```bash
git rm app/src/main/java/com/ioi/youtube/presentation/components/VideoGridCard.kt
git rm app/src/test/java/com/ioi/youtube/presentation/components/VideoGridCardTest.kt
```

(якщо тесту немає — `git rm` лише файлу картки).

- [ ] **Step 4: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(search): show results as single-column large cards"
```

---

## Task 6: Відкриття конкретного Shorts-відео

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/Routes.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBar.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsViewModel.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/navigation/RoutesTest.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/shorts/ShortsViewModelTest.kt`

- [ ] **Step 1: Тести (RED)**

`RoutesTest` — додати:

```kotlin
    @Test
    fun `shorts pattern has optional video id`() {
        assertThat(Routes.SHORTS_PATTERN).isEqualTo("shorts?videoId={videoId}")
    }

    @Test
    fun `shorts route without id returns the tab route`() {
        assertThat(Routes.shorts()).isEqualTo("shorts")
        assertThat(Routes.shorts(null)).isEqualTo("shorts")
    }

    @Test
    fun `shorts route with id carries the video`() {
        assertThat(Routes.shorts("dQw4w9WgXcQ")).isEqualTo("shorts?videoId=dQw4w9WgXcQ")
    }
```

`ShortsViewModelTest` — оновити фабрику й додати тест:

```kotlin
    private fun createViewModel(videoId: String? = null) = ShortsViewModel(
        savedStateHandle = SavedStateHandle(
            videoId?.let { mapOf(Routes.ARG_VIDEO_ID to it) } ?: emptyMap(),
        ),
        getShorts = GetShortsUseCase(shorts),
        getMoreShorts = GetMoreShortsUseCase(shorts),
        getVideoStream = GetVideoStreamUseCase(videos),
    )

    @Test
    fun `requested short is placed first even when absent from the feed`() =
        runTest(testDispatcher) {
            shorts.setShorts(UiState.Success(listOf(TestData.video(id = "feedvideo01"))))

            val viewModel = createViewModel(videoId = "requested01")
            advanceUntilIdle()

            val data = (viewModel.uiState.value.shorts as UiState.Success).data
            assertThat(data.first().id).isEqualTo("requested01")
            assertThat(data.map { it.id }).contains("feedvideo01")
        }

    @Test
    fun `requested short stays first when it is already in the feed`() =
        runTest(testDispatcher) {
            shorts.setShorts(
                UiState.Success(
                    listOf(
                        TestData.video(id = "feedvideo01"),
                        TestData.video(id = "requested01"),
                    ),
                ),
            )

            val viewModel = createViewModel(videoId = "requested01")
            advanceUntilIdle()

            val data = (viewModel.uiState.value.shorts as UiState.Success).data
            assertThat(data.map { it.id }).containsExactly("requested01", "feedvideo01").inOrder()
        }
```

Імпорти: `androidx.lifecycle.SavedStateHandle`, `com.ioi.youtube.presentation.navigation.Routes`.

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.navigation.RoutesTest" --tests "com.ioi.youtube.presentation.shorts.ShortsViewModelTest"`
Expected: FAIL — немає `SHORTS_PATTERN`/`shorts()`; конструктор VM не приймає `SavedStateHandle`.

- [ ] **Step 2: Реалізувати маршрут**

`Routes.kt`:

```kotlin
    const val SHORTS = "shorts"
    const val SHORTS_PATTERN = "shorts?videoId={videoId}"
    // ...
    fun shorts(videoId: String? = null): String =
        if (videoId.isNullOrBlank()) SHORTS else "$SHORTS?videoId=$videoId"
```

- [ ] **Step 3: Реалізувати старт на відео у ShortsViewModel**

Додати в конструктор:

```kotlin
class ShortsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getShorts: GetShortsUseCase,
    private val getMoreShorts: GetMoreShortsUseCase,
    private val getVideoStream: GetVideoStreamUseCase,
) : ViewModel() {

    private val requestedVideoId: String? =
        savedStateHandle.get<String>(Routes.ARG_VIDEO_ID)?.takeIf { it.isNotBlank() }
```

У колекторі `init` замінити `ShortsUiState(shorts = state)` на:

```kotlin
                uiState.value = ShortsUiState(
                    shorts = when (state) {
                        is UiState.Success -> UiState.Success(
                            withRequestedFirst(state.data),
                            state.fromCache,
                        )

                        else -> state
                    },
                )
```

Додати приватний метод:

```kotlin
    /** Передане зі стрічки відео стає першою сторінкою пейджера. */
    private fun withRequestedFirst(shorts: List<VideoSummary>): List<VideoSummary> {
        val id = requestedVideoId ?: return shorts
        val existing = shorts.firstOrNull { it.id == id }
        val rest = shorts.filterNot { it.id == id }
        val first = existing ?: VideoSummary(
            id = id,
            title = "",
            uploaderName = "",
            thumbnailUrl = "",
            durationSeconds = 0,
            viewCount = 0,
            uploadDate = null,
        )
        return listOf(first) + rest
    }
```

Імпорт `com.ioi.youtube.domain.model.VideoSummary` уже є.

- [ ] **Step 4: Навігація і нижня смуга**

`IOITubeApp.kt`:

```kotlin
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.SEARCH ||
        currentRoute?.startsWith(Routes.SHORTS) == true
```

```kotlin
            composable(Routes.HOME) {
                HomeRoute(
                    onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) },
                    onShortClick = { videoId -> navController.navigate(Routes.shorts(videoId)) },
                )
            }
```

```kotlin
            composable(
                route = Routes.SHORTS_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_VIDEO_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ShortsRoute()
            }
```

Імпорти: `androidx.navigation.NavType`, `androidx.navigation.navArgument`. Старий `composable(Routes.SHORTS)` прибрати.

`IOITubeBottomBar.kt`:

```kotlin
            val selected = currentRoute?.substringBefore('?') == route
```

- [ ] **Step 5: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(shorts): open a specific short from the home feed"
```

---

## Task 7: Фінальна перевірка фази

- [ ] **Step 1: Повний прогін тестів**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Збірка APK**

Run: `./gradlew :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Скопіювати APK у `dist/`**

```bash
cp app/build/outputs/apk/debug/app-debug.apk dist/IOITube-1.0-debug.apk
cp app/build/outputs/apk/release/app-release.apk dist/IOITube-1.0-release.apk
md5sum dist/IOITube-1.0-debug.apk dist/IOITube-1.0-release.apk
```

Оновити MD5 у документації релізу, якщо вона є.

- [ ] **Step 4: Ручний смоук на пристрої (обовʼязково)**

1. «Головна»: чипи «Усі»…«Подорожі» + чип сортування; перемикання теми змінює вміст.
2. Два pull-to-refresh поспіль дають різні відео.
3. Скрол донизу довантажує нові відео; після кожного 4-го відео — блок із 2 Shorts різних каналів.
4. Тап по шортсу відкриває вкладку Shorts на цьому відео; нижня смуга лишається видимою і «Shorts» підсвічена.
5. «Пошук» — один стовпець великих карток.
6. Вимкнути мережу: показується кеш «Усі + Релевантність» + офлайн-банер; для теми — помилка з «Спробувати знову».
7. Помилка довантаження (авіарежим під час скролу) не очищає стрічку; «Повторити» працює.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(release): rebuild apks for feed and search phase"
```
