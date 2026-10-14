# IOITube — Фаза 2: Шар даних

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md` · **Try:** фаза 2 полягає на інтерфейсах/моделях Фази 1.

**Goal фази:** реальні дані з YouTube через NewPipe Extractor 0.26.5, локальний кеш у Room (офлайн-режим зі спеки §7), реалізації репозиторіїв, DI через Hilt. Після фази застосунок показує справжню головну стрічку.

**Architecture:** `data/remote` (NewPipe + OkHttp downloader) → `data/repository` (offline-first, Room-кеш) → інтерфейси `domain/repository`. Domain не знає про NewPipe/Room.

**Tech Stack:** NewPipe Extractor 0.26.5 (JitPack), OkHttp 4.12.0, Room 2.6.1, Hilt, Coroutines.

**Важливо про NewPipe API (v0.26.5, перевірено):**
- `NewPipe.init(downloader)` — обов'язково перед першим зверненням.
- `Downloader` — абстрактний клас; реалізуємо `execute(request: Request)`.
- `Request`: `url()`, `httpMethod()`, `headers(): Map<String, List<String>>`, `dataToSend(): ByteArray?`.
- `Response(responseCode: Int, responseMessage: String, responseHeaders: Map<String, List<String>>, responseBody: String, latestUrl: String)`.
- Ліва стрічка: `NewPipe.getService(ServiceList.YouTube.serviceId).kioskList.getDefaultKioskExtractor().getInitialPage().items`.
- Пошук: `NewPipe.getService(ServiceList.YouTube.serviceId).getSearchExtractor(query).getInitialPage().items`.
- Деталі/потік: `StreamInfo.getInfo(watchUrl)` → `getName()`, `getUploaderName()`, `getUploaderAvatars()`, `getDescription()` (nullable `Description` → `.getContent()`), `getViewCount()` (**-1** = немає), `getLikeCount()` (**-1**), `getTextualUploadDate()`, `getThumbnails()`, `getDuration()` (**-1**), `getRelatedItems()`, `getVideoStreams()`.
- Комментарі: `CommentsInfo.getInfo(watchUrl)` — **може повернути null**; `isCommentsDisabled()`.
- `CommentsInfoItem`: `getCommentId()`, `getCommentText()` (`Description`), `getUploaderName()`, `getUploaderAvatars()`, `getTextualUploadDate()`, `getLikeCount()` (**int**, -1 = немає), `isHeartedByUploader()`, `isPinned()`.
- `StreamInfoItem`: `getName()`, `getUrl()`, `getUploaderName()`, `getTextualUploadDate()`, `getViewCount()` (**-1**), `getDuration()` (**-1**), `getThumbnails()` (з `InfoItem`).
- `VideoStream`: `getUrl()`, `getResolution()` ("720p"), `getFormat()` ("MPEG-4"), `getBitrate()`, `isVideoOnly()`.
- Усі виклики NewPipe блокують потік — лише `Dispatchers.IO`.

---

### Task 2.1: OkHttpDownloader + ExtractorInitializer

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/extractor/OkHttpDownloader.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/extractor/ExtractorInitializer.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/extractor/ExtractorInitializerTest.kt`

**Interfaces:**
- Consumes: OkHttp (підключений у Task 1.2), NewPipe Extractor (v0.26.5).
- Produces: `class OkHttpDownloader @Inject constructor(client: OkHttpClient) : Downloader()`; `class ExtractorInitializer @Inject constructor(downloader: Downloader)` з `fun init()` (ідемпотентна). Обидва `@Singleton`.

- [ ] **Step 1: Напиши тест `ExtractorInitializerTest.kt`**

```kotlin
package com.ioi.youtube.data.extractor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExtractorInitializerTest {

    @Test
    fun `init is accepted by NewPipe`() {
        val initializer = ExtractorInitializer(TestDownloader())

        initializer.init()

        // Якщо NewPipe прийняв downloader, сервіс YouTube реєструється.
        initializer.init() // другий виклик — no-op, не повинен падати
        assertThat(true).isTrue()
    }

    private class TestDownloader : org.schabi.newpipe.extractor.downloader.Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response =
            org.schabi.newpipe.extractor.downloader.Response(
                200,
                "OK",
                emptyMap(),
                "{}",
                request.url(),
            )
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.extractor.ExtractorInitializerTest"`

Expected: FAIL (`Unresolved reference: ExtractorInitializer`).

- [ ] **Step 3: Створи `OkHttpDownloader.kt`**

```kotlin
package com.ioi.youtube.data.extractor

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Адаптер, який дає NewPipe Extractor можливість робити HTTP-запити через OkHttp.
 */
@Singleton
class OkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val mediaType = request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
        val body: RequestBody? = request.dataToSend()?.let { bytes ->
            bytes.toRequestBody(mediaType)
        }

        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .apply {
                request.headers().forEach { (name, values) ->
                    if (!name.equals("Content-Length", ignoreCase = true)) {
                        values.forEach { value -> addHeader(name, value) }
                    }
                }
            }
            .build()

        val httpResponse = try {
            client.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw IOException("NewPipe downloader: ${e.message}", e)
        }

        val responseBody = httpResponse.body?.string().orEmpty()
        val headers: Map<String, List<String>> = httpResponse.headers.toMultimap()

        return Response(
            httpResponse.code,
            httpResponse.message,
            headers,
            responseBody,
            httpResponse.request.url.toString(),
        )
    }
}
```

- [ ] **Step 4: Створи `ExtractorInitializer.kt`**

```kotlin
package com.ioi.youtube.data.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Викликає NewPipe.init(downloader) рівно один раз за життя процесу.
 * Має бути викликаний до першого звернення до extractor (див. Task 2.12).
 */
@Singleton
class ExtractorInitializer @Inject constructor(
    private val downloader: Downloader,
) {
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader)
            initialized = true
        }
    }
}
```

- [ ] **Step 5: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.extractor.ExtractorInitializerTest"`

Expected: PASS. Тест не робить мережевих запитiв — NewPipe лише реєструє downloader.

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add OkHttp downloader for NewPipe Extractor"
```

---

### Task 2.2: YouTubeUrlParser

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/util/YouTubeUrlParser.kt`
- Test: `app/src/test/java/com/ioi/youtube/util/YouTubeUrlParserTest.kt`

**Interfaces:**
- Consumes: нічого.
- Produces:
  - `YouTubeUrlParser.extractVideoId(url: String): String?` — голий 11-символьний ID, `watch?v=`, `youtu.be/`, `/shorts/`, `/embed/`, `/live/`, інакше `null`.
  - `YouTubeUrlParser.buildWatchUrl(videoId: String): String` — `https://www.youtube.com/watch?v=<id>`.

- [ ] **Step 1: Напиши падаючий тест `YouTubeUrlParserTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.util.YouTubeUrlParserTest"`

Expected: FAIL (`Unresolved reference: YouTubeUrlParser`).

- [ ] **Step 3: Створи `YouTubeUrlParser.kt`**

```kotlin
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
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.util.YouTubeUrlParserTest"`

Expected: PASS (7 тестiв). Це перший пункт Review Focus із майстер-плану.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add youtube url parser with tests"
```

---

### Task 2.3: Raw-моделі та маппери

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/mapper/RemoteMappers.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/mapper/RemoteMappersTest.kt`

**Interfaces:**
- Consumes: домен-моделі (Task 1.6).
- Produces: `RawVideoSummary`, `RawVideoDetails`, `RawComment`, `RawVideoStream`; extension-функції `RawVideoSummary.toDomain()`, `RawVideoDetails.toDomain()`, `RawComment.toDomain()`, `RawVideoStream.toDomain()`.

- [ ] **Step 1: Напиши падаючий тест `RemoteMappersTest.kt`**

```kotlin
package com.ioi.youtube.data.mapper

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import org.junit.Test

class RemoteMappersTest {

    @Test
    fun `maps raw summary to domain`() {
        val raw = RawVideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Title",
            uploaderName = "Channel",
            thumbnailUrl = "https://img/1.jpg",
            durationSeconds = 75,
            viewCount = 1500,
            uploadDate = "2 years ago",
        )

        val domain = raw.toDomain()

        assertThat(domain.id).isEqualTo("dQw4w9WgXcQ")
        assertThat(domain.title).isEqualTo("Title")
        assertThat(domain.durationSeconds).isEqualTo(75L)
        assertThat(domain.viewCount).isEqualTo(1500L)
    }

    @Test
    fun `maps raw details including recommended videos`() {
        val recommended = RawVideoSummary("aaaaa", "Rec", "Ch", "u", 10, 1, null)
        val raw = RawVideoDetails(
            id = "dQw4w9WgXcQ",
            title = "T",
            uploaderName = "U",
            uploaderAvatarUrl = null,
            description = "D",
            viewCount = 0,
            likeCount = 0,
            uploadDate = null,
            thumbnailUrl = "t",
            durationSeconds = 0,
            recommended = listOf(recommended),
        )

        val domain = raw.toDomain()

        assertThat(domain.description).isEqualTo("D")
        assertThat(domain.uploadDate).isNull()
        assertThat(domain.recommendedVideos).hasSize(1)
        assertThat(domain.recommendedVideos.first().id).isEqualTo("aaaaa")
    }

    @Test
    fun `maps zero values from NewPipe no-data markers`() {
        val raw = RawComment(
            id = "c1",
            authorName = "A",
            authorAvatarUrl = null,
            text = "",
            likeCount = 0,
            publishedTime = null,
            heartedByUploader = false,
            pinned = false,
        )

        val domain = raw.toDomain()

        assertThat(domain.likeCount).isEqualTo(0L)
        assertThat(domain.text).isEmpty()
    }

    @Test
    fun `maps raw stream to domain`() {
        val raw = RawVideoStream(url = "https://v/x", resolution = "720p", format = "MPEG-4")

        val domain = raw.toDomain()

        assertThat(domain.url).isEqualTo("https://v/x")
        assertThat(domain.resolution).isEqualTo("720p")
        assertThat(domain.format).isEqualTo("MPEG-4")
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.mapper.RemoteMappersTest"`

Expected: FAIL (`Unresolved reference: RawVideoSummary`).

- [ ] **Step 3: Створи `RemoteModels.kt`**

```kotlin
package com.ioi.youtube.data.model

/**
 * "Сірі" моделі даних із NewPipe Extractor.
 * Відділені від домену, щоб маппери бути чистими й тестованими без мережі.
 */
data class RawVideoSummary(
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
)

data class RawVideoDetails(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val description: String,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String?,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val recommended: List<RawVideoSummary>,
)

data class RawComment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long,
    val publishedTime: String?,
    val heartedByUploader: Boolean,
    val pinned: Boolean,
)

data class RawVideoStream(
    val url: String,
    val resolution: String,
    val format: String,
)
```

- [ ] **Step 4: Створи `RemoteMappers.kt`**

```kotlin
package com.ioi.youtube.data.mapper

import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary

fun RawVideoSummary.toDomain(): VideoSummary = VideoSummary(
    id = id,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
)

fun RawVideoDetails.toDomain(): VideoDetails = VideoDetails(
    id = id,
    title = title,
    uploaderName = uploaderName,
    uploaderAvatarUrl = uploaderAvatarUrl,
    description = description,
    viewCount = viewCount,
    likeCount = likeCount,
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    recommendedVideos = recommended.map { it.toDomain() },
)

fun RawComment.toDomain(): Comment = Comment(
    id = id,
    authorName = authorName,
    authorAvatarUrl = authorAvatarUrl,
    text = text,
    likeCount = likeCount,
    publishedTime = publishedTime,
    heartedByUploader = heartedByUploader,
    pinned = pinned,
)

fun RawVideoStream.toDomain(): VideoStream = VideoStream(
    url = url,
    resolution = resolution,
    format = format,
)
```

- [ ] **Step 5: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.mapper.RemoteMappersTest"`

Expected: PASS (4 тести).

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add remote models and mappers with tests"
```

---

### Task 2.4: NewPipeRemoteDataSource

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/remote/YouTubeRemoteDataSource.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`

**Interfaces:**
- Consumes: `YouTubeUrlParser` (Task 2.2), raw-моделі (Task 2.3), `ExtractorInitializer` (Task 2.1 — ініціалізація відбувається в Task 2.12).
- Produces:
  - `interface YouTubeRemoteDataSource` з `suspend fun fetchHomeFeed(): List<RawVideoSummary>`, `search(query: String): List<RawVideoSummary>`, `fetchVideoDetails(videoId: String): RawVideoDetails`, `fetchComments(videoId: String): List<RawComment>`, `fetchVideoStream(videoId: String): RawVideoStream`.
  - `class NewPipeRemoteDataSource : YouTubeRemoteDataSource` (`@Singleton`, без конструктора) — єдина точка, що знає NewPipe API.

- [ ] **Step 1: Створи `YouTubeRemoteDataSource.kt`**

```kotlin
package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary

/**
 * Єдине джерело даних із YouTube. Розриває залежність UI/репозиторіїв від NewPipe,
 * щоб тести використовували фейк (див. Task 2.5).
 * Усі методи блокують потік — викликати лише з Dispatchers.IO.
 */
interface YouTubeRemoteDataSource {
    suspend fun fetchHomeFeed(): List<RawVideoSummary>
    suspend fun search(query: String): List<RawVideoSummary>
    suspend fun fetchVideoDetails(videoId: String): RawVideoDetails
    suspend fun fetchComments(videoId: String): List<RawComment>
    suspend fun fetchVideoStream(videoId: String): RawVideoStream
}
```

- [ ] **Step 2: Створи `NewPipeRemoteDataSource.kt`**

```kotlin
package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.util.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реалізація YouTubeRemoteDataSource на NewPipe Extractor 0.26.5.
 * NewPipe повертає посилання на відеопотік без рекламних сегментiв (спека §1).
 */
@Singleton
class NewPipeRemoteDataSource @Inject constructor() : YouTubeRemoteDataSource {

    private val youtube: StreamingService
        get() = NewPipe.getService(ServiceList.YouTube.serviceId)

    override suspend fun fetchHomeFeed(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        youtube.kioskList
            .getDefaultKioskExtractor()
            .getInitialPage()
            .items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toRawSummary() }
    }

    override suspend fun search(query: String): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        youtube.getSearchExtractor(query)
            .getInitialPage()
            .items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toRawSummary() }
    }

    override suspend fun fetchVideoDetails(videoId: String): RawVideoDetails =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            RawVideoDetails(
                id = videoId,
                title = info.name.orEmpty(),
                uploaderName = info.uploaderName.orEmpty(),
                uploaderAvatarUrl = info.uploaderAvatars.lastOrNull()?.url,
                description = info.description?.content.orEmpty(),
                viewCount = info.viewCount.orZero(),
                likeCount = info.likeCount.orZero(),
                uploadDate = info.textualUploadDate,
                thumbnailUrl = info.thumbnails.lastOrNull()?.url.orEmpty(),
                durationSeconds = info.duration.orZero(),
                recommended = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toRawSummary() },
            )
        }

    override suspend fun fetchComments(videoId: String): List<RawComment> =
        withContext(Dispatchers.IO) {
            val info = CommentsInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
                ?: return@withContext emptyList()
            if (info.isCommentsDisabled) return@withContext emptyList()
            info.relatedItems
                .filterIsInstance<CommentsInfoItem>()
                .map { it.toRawComment() }
        }

    override suspend fun fetchVideoStream(videoId: String): RawVideoStream =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            val stream = pickBestVideoStream(info)
            RawVideoStream(
                url = stream.url,
                resolution = stream.resolution.orEmpty(),
                format = stream.format.orEmpty(),
            )
        }

    private fun pickBestVideoStream(info: StreamInfo): VideoStream {
        // Progressive-потоки (відео + аудіо в одному файлi) — ExoPlayer грає їх напряму.
        val candidates = info.videoStreams.filterNot { it.isVideoOnly }
        return candidates.maxWithOrNull(
            compareBy<VideoStream> { it.resolutionPixels() }.thenBy { it.bitrate },
        ) ?: throw NoSuchElementException("No progressive video stream for video ${info.id}")
    }

    private fun VideoStream.resolutionPixels(): Int =
        resolution?.substringBefore('p')?.substringBefore('P')?.toIntOrNull() ?: 0

    /** NewPipe позначає вiдсутнi значення як -1. */
    private fun Long.orZero(): Long = if (this < 0) 0 else this

    private fun StreamInfoItem.toRawSummary() = RawVideoSummary(
        id = YouTubeUrlParser.extractVideoId(url).orEmpty(),
        title = name.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.lastOrNull()?.url.orEmpty(),
        durationSeconds = duration.orZero(),
        viewCount = viewCount.orZero(),
        uploadDate = textualUploadDate,
    )

    private fun CommentsInfoItem.toRawComment() = RawComment(
        id = commentId.orEmpty(),
        authorName = uploaderName.orEmpty(),
        authorAvatarUrl = uploaderAvatars.lastOrNull()?.url,
        text = commentText.content,
        likeCount = if (likeCount >= 0) likeCount.toLong() else 0L,
        publishedTime = textualUploadDate,
        heartedByUploader = isHeartedByUploader,
        pinned = isPinned,
    )
}
```

- [ ] **Step 3: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. Якщо Kotlin скаржиться на nullable-типи NewPipe (`url`, `resolution`, `format`, `name`, `uploaderName`, `commentId`, `textualUploadDate`) — Java-гетери без анотацій дають platform-типи, тому `.orEmpty()` унiверсальний і не повинен ламати компіляцію. Єдине мiсце, де може знадобитись правка — `Text`-поля: при compile-помилці залиш виклиди `.orEmpty()` як є.

- [ ] **Step 4: Димний тест із реальним YouTube (мережевий, запускати вручну)**

Створи `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceSmokeTest.kt`:

```kotlin
package com.ioi.youtube.data.remote

import com.ioi.youtube.data.extractor.ExtractorInitializer
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import kotlinx.coroutines.runBlocking

class NewPipeRemoteDataSourceSmokeTest {

    @Test
    fun `feed and search return data`() = runBlocking {
        val initializer = ExtractorInitializer(object : Downloader() {
            override fun execute(request: Request): Response =
                Response(200, "OK", emptyMap(), "", request.url())
        })
        // Димний тест НЕ використовує мережевий downloader – NewPipe.ui потребує реальний.
        // Реальну перевірку робимо тільки на пристрої (Task 2.12), тому тут пропускаємо.
        assumeTrue(false)
        initializer.init()
        val dataSource = NewPipeRemoteDataSource()
        val feed = dataSource.fetchHomeFeed()
        val search = dataSource.search("kotlin")
        check(feed.isNotEmpty() || search.isNotEmpty())
        println("feed=${feed.size}, search=${search.size}")
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.remote.NewPipeRemoteDataSourceSmokeTest"`

Expected: PASS зі статусом `skipped` (assumeTrue(false)) — тест навмисно не чіпає мережу в CI. Реальна перевірка — у Task 2.12 на пристрої.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add NewPipe remote data source"
```

---

### Task 2.5: FakeRemoteDataSource

**Files:**
- Create: `app/src/test/java/com/ioi/youtube/testutil/FakeRemoteDataSource.kt`

**Interfaces:**
- Consumes: `YouTubeRemoteDataSource` (Task 2.4), raw-моделі (Task 2.3).
- Produces: `class FakeRemoteDataSource : YouTubeRemoteDataSource` з публічними полями `feed`, `searchResults`, `details`, `comments`, `stream`, полями помилок `feedError`, `searchError`, `detailsError`, `commentsError`, `streamError` та лічильниками викливів.

- [ ] **Step 1: Створи `FakeRemoteDataSource.kt`**

```kotlin
package com.ioi.youtube.testutil

import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource

class FakeRemoteDataSource : YouTubeRemoteDataSource {

    var feed: List<RawVideoSummary> = emptyList()
    var searchResults: List<RawVideoSummary> = emptyList()
    var details: RawVideoDetails? = null
    var comments: List<RawComment> = emptyList()
    var stream: RawVideoStream? = null

    var feedError: Exception? = null
    var searchError: Exception? = null
    var detailsError: Exception? = null
    var commentsError: Exception? = null
    var streamError: Exception? = null

    var feedCalls = 0
        private set
    var searchCalls = 0
        private set
    var detailsCalls = 0
        private set
    var commentsCalls = 0
        private set
    var streamCalls = 0
        private set

    override suspend fun fetchHomeFeed(): List<RawVideoSummary> {
        feedCalls++
        feedError?.let { throw it }
        return feed
    }

    override suspend fun search(query: String): List<RawVideoSummary> {
        searchCalls++
        searchError?.let { throw it }
        return searchResults
    }

    override suspend fun fetchVideoDetails(videoId: String): RawVideoDetails {
        detailsCalls++
        detailsError?.let { throw it }
        return requireNotNull(details) { "FakeRemoteDataSource.details is not set" }
    }

    override suspend fun fetchComments(videoId: String): List<RawComment> {
        commentsCalls++
        commentsError?.let { throw it }
        return comments
    }

    override suspend fun fetchVideoStream(videoId: String): RawVideoStream {
        streamCalls++
        streamError?.let { throw it }
        return requireNotNull(stream) { "FakeRemoteDataSource.stream is not set" }
    }
}
```

- [ ] **Step 2: Перевір компіляцію тестів**

Run: `./gradlew :app:compileDebugUnitTestKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коміт**

```bash
git add -A
git commit -m "test: add fake remote data source"
```

---

### Task 2.6: Room — сутність відео + маппери кешу

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/local/CachedVideoEntity.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/local/CachedVideoDao.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/mapper/CacheMappers.kt`

**Interfaces:**
- Consumes: `VideoSummary` (Task 1.6).
- Produces:
  - `@Entity(tableName = "cached_videos") data class CachedVideoEntity(id: String, sourceKey: String, title: String, uploaderName: String, thumbnailUrl: String, durationSeconds: Long, viewCount: Long, uploadDate: String?, cachedAt: Long)`.
  - `CachedVideoDao`: `suspend fun getBySource(sourceKey: String): List<CachedVideoEntity>`, `suspend fun insertAll(videos: List<CachedVideoEntity>)`, `suspend fun deleteBySource(sourceKey: String)`, `suspend fun deleteOlderThan(timestamp: Long)`, `@Transaction suspend fun replaceSource(sourceKey: String, videos: List<CachedVideoEntity>)`.
  - `fun CachedVideoEntity.toDomain(): VideoSummary`, `fun VideoSummary.toEntity(sourceKey: String, cachedAt: Long): CachedVideoEntity`.

- [ ] **Step 1: Створи `CachedVideoEntity.kt`**

```kotlin
package com.ioi.youtube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кеш списку відео (головна стрічка та результати пошуку).
 * sourceKey = "home" або "search:<query>" — одним полем покриває обидва випадки.
 */
@Entity(tableName = "cached_videos", primaryKeys = ["sourceKey", "id"])
data class CachedVideoEntity(
    val id: String,
    val sourceKey: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
    val cachedAt: Long,
)
```

- [ ] **Step 2: Створи `CachedVideoDao.kt`**

```kotlin
package com.ioi.youtube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedVideoDao {

    @Query("SELECT * FROM cached_videos WHERE sourceKey = :sourceKey ORDER BY rowid ASC")
    suspend fun getBySource(sourceKey: String): List<CachedVideoEntity>

    @Query("SELECT * FROM cached_videos WHERE sourceKey = :sourceKey ORDER BY rowid ASC")
    fun observeBySource(sourceKey: String): Flow<List<CachedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<CachedVideoEntity>)

    @Query("DELETE FROM cached_videos WHERE sourceKey = :sourceKey")
    suspend fun deleteBySource(sourceKey: String)

    @Query("DELETE FROM cached_videos WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Transaction
    suspend fun replaceSource(sourceKey: String, videos: List<CachedVideoEntity>) {
        deleteBySource(sourceKey)
        insertAll(videos)
    }
}
```

- [ ] **Step 3: Створи `CacheMappers.kt`**

```kotlin
package com.ioi.youtube.data.mapper

import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.domain.model.VideoSummary

fun CachedVideoEntity.toDomain(): VideoSummary = VideoSummary(
    id = id,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
)

fun VideoSummary.toEntity(sourceKey: String, cachedAt: Long): CachedVideoEntity =
    CachedVideoEntity(
        id = id,
        sourceKey = sourceKey,
        title = title,
        uploaderName = uploaderName,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadDate = uploadDate,
        cachedAt = cachedAt,
    )
```

- [ ] **Step 4: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add cached video entity, dao and cache mappers"
```

---

### Task 2.7: Room — сутність деталей + база даних

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/local/CachedVideoDetailsEntity.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/local/CachedVideoDetailsDao.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/local/IOIDatabase.kt`

**Interfaces:**
- Consumes: `CachedVideoEntity`/`CachedVideoDao`/відео-маппери (Task 2.6).
- Produces:
  - `@Entity(tableName = "cached_video_details") data class CachedVideoDetailsEntity(id, title, uploaderName, description, viewCount: Long, likeCount: Long, uploadDate: String?, thumbnailUrl, durationSeconds: Long, cachedAt: Long)`.
  - `CachedVideoDetailsDao`: `suspend fun getById(videoId: String): CachedVideoDetailsEntity?`, `suspend fun upsert(details: CachedVideoDetailsEntity)`, `suspend fun deleteOlderThan(timestamp: Long)`.
  - `abstract class IOIDatabase : RoomDatabase()` з `cachedVideoDao()`, `cachedVideoDetailsDao()`, `version = 1`.
  - Маппери деталей у `data/mapper/CacheMappers.kt`: `CachedVideoDetailsEntity.toDomain()`, `VideoDetails.toDetailsEntity(cachedAt)`.

- [ ] **Step 1: Створи `CachedVideoDetailsEntity.kt`**

```kotlin
package com.ioi.youtube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кеш метаданих відео (спека §5.4: "результати пошуку та метадані відео").
 * Рекомендації та коментарі в кеш не пишуть — вони завжди завантажуються з мережі.
 */
@Entity(tableName = "cached_video_details")
data class CachedVideoDetailsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val uploaderName: String,
    val description: String,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String?,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val cachedAt: Long,
)
```

- [ ] **Step 2: Створи `CachedVideoDetailsDao.kt`**

```kotlin
package com.ioi.youtube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedVideoDetailsDao {

    @Query("SELECT * FROM cached_video_details WHERE id = :videoId LIMIT 1")
    suspend fun getById(videoId: String): CachedVideoDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: CachedVideoDetailsEntity)

    @Query("DELETE FROM cached_video_details WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
```

- [ ] **Step 3: Створи `IOIDatabase.kt`**

```kotlin
package com.ioi.youtube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedVideoEntity::class, CachedVideoDetailsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IOIDatabase : RoomDatabase() {
    abstract fun cachedVideoDao(): CachedVideoDao
    abstract fun cachedVideoDetailsDao(): CachedVideoDetailsDao
}
```

- [ ] **Step 4: Додай маппери деталей у `CacheMappers.kt`**

В кiнць файлу `app/src/main/java/com/ioi/youtube/data/mapper/CacheMappers.kt` (пiсля кодy з Task 2.6) дописати:

```kotlin

fun CachedVideoDetailsEntity.toDomain(): VideoDetails = VideoDetails(
    id = id,
    title = title,
    uploaderName = uploaderName,
    uploaderAvatarUrl = null,
    description = description,
    viewCount = viewCount,
    likeCount = likeCount,
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    recommendedVideos = emptyList(),
)

fun VideoDetails.toDetailsEntity(cachedAt: Long): CachedVideoDetailsEntity =
    CachedVideoDetailsEntity(
        id = id,
        title = title,
        uploaderName = uploaderName,
        description = description,
        viewCount = viewCount,
        likeCount = likeCount,
        uploadDate = uploadDate,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        cachedAt = cachedAt,
    )
```

- [ ] **Step 5: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. KSP має згенерувати `IOIDatabase_Impl`.

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add room database with cache entities and daos"
```

---

### Task 2.8: Тести Room DAO (Robolectric)

**Files:**
- Create: `app/src/test/java/com/ioi/youtube/data/local/CachedVideoDaoTest.kt`
- Create: `app/src/test/java/com/ioi/youtube/data/local/CachedVideoDetailsDaoTest.kt`

**Interfaces:**
- Consumes: `IOIDatabase`, `CachedVideoEntity`, `CachedVideoDetailsEntity` (Tasks 2.6–2.7).
- Produces: тести insert/get/delete/transaction-поведінки обох DAO — це підтвердження, що кеш-шар збеpігає між сесіями.

- [ ] **Step 1: Напиши тест `CachedVideoDaoTest.kt`**

```kotlin
package com.ioi.youtube.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedVideoDaoTest {

    private lateinit var database: IOIDatabase
    private lateinit var dao: CachedVideoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cachedVideoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts and reads by source key`() = runTest {
        val now = 1_000L
        dao.insertAll(
            listOf(
                videoEntity("bbb", "home", now),
                videoEntity("aaa", "home", now),
            ),
        )

        val result = dao.getBySource("home")

        assertThat(result.map { it.id }).containsExactly("bbb", "aaa").inOrder()
    }

    @Test
    fun `replaceSource replaces list atomically`() = runTest {
        dao.insertAll(listOf(videoEntity("old", "home", 1L)))

        dao.replaceSource("home", listOf(videoEntity("new1", "home", 2L), videoEntity("new2", "home", 2L)))

        val result = dao.getBySource("home")
        assertThat(result.map { it.id }).containsExactly("new1", "new2").inOrder()
    }

    @Test
    fun `deleteOlderThan removes stale rows only`() = runTest {
        dao.insertAll(
            listOf(
                videoEntity("stale", "home", 100L),
                videoEntity("fresh", "home", 500L),
            ),
        )

        dao.deleteOlderThan(300L)

        assertThat(dao.getBySource("home").map { it.id }).containsExactly("fresh")
    }

    @Test
    fun `getBySource returns empty list for unknown key`() = runTest {
        assertThat(dao.getBySource("search:nothing")).isEmpty()
    }

    private fun videoEntity(id: String, sourceKey: String, cachedAt: Long) = CachedVideoEntity(
        id = id,
        sourceKey = sourceKey,
        title = "Title $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 61,
        viewCount = 100,
        uploadDate = null,
        cachedAt = cachedAt,
    )
}
```

- [ ] **Step 2: Напиши тест `CachedVideoDetailsDaoTest.kt`**

```kotlin
package com.ioi.youtube.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedVideoDetailsDaoTest {

    private lateinit var database: IOIDatabase
    private lateinit var dao: CachedVideoDetailsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cachedVideoDetailsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert then getById returns details`() = runTest {
        dao.upsert(details("vid1", 10L))

        val result = dao.getById("vid1")

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Video vid1")
        assertThat(result.viewCount).isEqualTo(999L)
    }

    @Test
    fun `upsert overwrites existing row`() = runTest {
        dao.upsert(details("vid1", 10L))
        val updated = details("vid1", 20L).copy(title = "Updated title")
        dao.upsert(updated)

        assertThat(dao.getById("vid1")!!.title).isEqualTo("Updated title")
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertThat(dao.getById("missing")).isNull()
    }

    private fun details(id: String, cachedAt: Long) = CachedVideoDetailsEntity(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        description = "Desc",
        viewCount = 999,
        likeCount = 42,
        uploadDate = null,
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 212,
        cachedAt = cachedAt,
    )
}
```

- [ ] **Step 3: Запусти тести — мають пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.local.*"`

Expected: PASS (7 тестiв). Robolectric завантажує Android-JAR — перший запуск довший.

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "test: add room dao tests with robolectric"
```

---

### Task 2.9: VideoRepositoryImpl (офлайн-first)

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/repository/VideoRepositoryImpl.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `VideoRepository` (Task 1.8), `YouTubeRemoteDataSource` (Task 2.4), `CachedVideoDao`/`CachedVideoDetailsDao`/маппери кешу (Tasks 2.6–2.7), `FakeRemoteDataSource` (Task 2.5).
- Produces: `@Singleton class VideoRepositoryImpl` з `companion object` константами: `SOURCE_HOME = "home"`, `ERROR_LOAD_FEED`, `ERROR_LOAD_DETAILS`, `ERROR_LOAD_STREAM`.

- [ ] **Step 1: Напиши падаючий тест `VideoRepositoryImplTest.kt`**

```kotlin
package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.data.local.CachedVideoDetailsEntity
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.mapper.toDetailsEntity
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import com.ioi.youtube.testutil.TestData
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
class VideoRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var detailsDao: CachedVideoDetailsDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: VideoRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        detailsDao = database.cachedVideoDetailsDao()
        remote = FakeRemoteDataSource()
        repository = VideoRepositoryImpl(remote, videoDao, detailsDao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `home feed emits cached then fresh`() = runTest {
        remote.feed = listOf(rawVideo("fresh1"))
        videoDao.replaceSource(VideoRepositoryImpl.SOURCE_HOME, listOf(videoEntity("cached1")))

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val cached = awaitItem() as UiState.Success
            assertThat(cached.data.map { it.id }).containsExactly("cached1")
            assertThat(cached.fromCache).isTrue()

            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.map { it.id }).containsExactly("fresh1")
            assertThat(fresh.fromCache).isFalse()

            awaitComplete()
        }
    }

    @Test
    fun `home feed persists fresh data to cache`() = runTest {
        remote.feed = listOf(rawVideo("fresh1"), rawVideo("fresh2"))

        repository.getHomeFeed().test {
            awaitItem()
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertThat(videoDao.getBySource(VideoRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("fresh1", "fresh2")
    }

    @Test
    fun `home feed emits error when network fails and cache is empty`() = runTest {
        remote.feedError = IOException("offline")

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(VideoRepositoryImpl.ERROR_LOAD_FEED)
            awaitComplete()
        }
    }

    @Test
    fun `home feed shows cache without error when network fails`() = runTest {
        remote.feedError = IOException("offline")
        videoDao.replaceSource(VideoRepositoryImpl.SOURCE_HOME, listOf(videoEntity("cached1")))

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.data.map { it.id }).containsExactly("cached1")
            assertThat(cached.fromCache).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `video details caches fresh details`() = runTest {
        remote.details = rawDetails("vid1")

        repository.getVideoDetails("vid1").test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val cached = detailsDao.getById("vid1")
        assertThat(cached).isNotNull()
        assertThat(cached!!.title).isEqualTo("Video vid1")
    }

    @Test
    fun `video details emits cached details when network fails`() = runTest {
        remote.detailsError = IOException("offline")
        detailsDao.upsert(TestData.details("vid1").toDetailsEntity(cachedAt = 10L))

        repository.getVideoDetails("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.id).isEqualTo("vid1")
            awaitComplete()
        }
    }

    @Test
    fun `video stream emits success without cache`() = runTest {
        remote.stream = RawVideoStream("https://v/stream", "720p", "MPEG-4")

        repository.getVideoStream("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data.url).isEqualTo("https://v/stream")
            awaitComplete()
        }
    }

    @Test
    fun `video stream emits error when unavailable`() = runTest {
        remote.streamError = NoSuchElementException("no progressive stream")

        repository.getVideoStream("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(VideoRepositoryImpl.ERROR_LOAD_STREAM)
            awaitComplete()
        }
    }

    private fun rawVideo(id: String) = RawVideoSummary(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
    )

    private fun videoEntity(id: String) = CachedVideoEntity(
        id = id,
        sourceKey = VideoRepositoryImpl.SOURCE_HOME,
        title = "Cached $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
        cachedAt = 10L,
    )

    private fun rawDetails(id: String) = RawVideoDetails(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        uploaderAvatarUrl = null,
        description = "Desc",
        viewCount = 100,
        likeCount = 5,
        uploadDate = null,
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        recommended = listOf(rawVideo("rec1")),
    )
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.VideoRepositoryImplTest"`

Expected: FAIL (`Unresolved reference: VideoRepositoryImpl`).

- [ ] **Step 3: Створи `VideoRepositoryImpl.kt`**

```kotlin
package com.ioi.youtube.data.repository

import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.mapper.toDetailsEntity
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first: спочатку кеш, потiм свiжi данi (спека §7).
 * Якщо мережа недоступна i кеш порожнiй — Error, iнакше показуємо кеш.
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
    private val cachedVideoDetailsDao: CachedVideoDetailsDao,
) : VideoRepository {

    override fun getHomeFeed(): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val cached = cachedVideoDao.getBySource(SOURCE_HOME)
        if (cached.isNotEmpty()) {
            emit(UiState.Success(cached.map { it.toDomain() }, fromCache = true))
        }
        try {
            val fresh = remote.fetchHomeFeed()
            cachedVideoDao.replaceSource(
                SOURCE_HOME,
                fresh.map { it.toEntity(SOURCE_HOME, System.currentTimeMillis()) },
            )
            emit(UiState.Success(fresh.map { it.toDomain() }))
        } catch (e: Exception) {
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_FEED, e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>> = flow {
        emit(UiState.Loading)
        val cached = cachedVideoDetailsDao.getById(videoId)
        if (cached != null) {
            emit(UiState.Success(cached.toDomain(), fromCache = true))
        }
        try {
            val fresh = remote.fetchVideoDetails(videoId).toDomain()
            cachedVideoDetailsDao.upsert(fresh.toDetailsEntity(System.currentTimeMillis()))
            emit(UiState.Success(fresh))
        } catch (e: Exception) {
            if (cached == null) emit(UiState.Error(ERROR_LOAD_DETAILS, e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getVideoStream(videoId: String): Flow<UiState<VideoStream>> = flow {
        emit(UiState.Loading)
        try {
            emit(UiState.Success(remote.fetchVideoStream(videoId).toDomain()))
        } catch (e: Exception) {
            emit(UiState.Error(ERROR_LOAD_STREAM, e))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_HOME = "home"
        const val ERROR_LOAD_FEED = "Не вдалося завантажити стрічку"
        const val ERROR_LOAD_DETAILS = "Не вдалося завантажити відео"
        const val ERROR_LOAD_STREAM = "Не вдалося отримати відеопотік"
    }
}
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.VideoRepositoryImplTest"`

Expected: PASS (8 тестiв). Покриває пункти Review Focus №3 та №4 з майстер-плану.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add offline-first video repository with tests"
```

---

### Task 2.10: SearchRepositoryImpl

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/repository/SearchRepositoryImpl.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/repository/SearchRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SearchRepository` (Task 1.8), `YouTubeRemoteDataSource` (Task 2.4), `FakeRemoteDataSource` (Task 2.5).
- Produces: `@Singleton class SearchRepositoryImpl` з `companion object`: `SOURCE_PREFIX = "search:"`, `ERROR_LOAD_SEARCH`, `fun searchKey(query: String) = "$SOURCE_PREFIX$query"`.

- [ ] **Step 1: Напиши падаючий тест `SearchRepositoryImplTest.kt`**

```kotlin
package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.SearchResults
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
class SearchRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: SearchRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        remote = FakeRemoteDataSource()
        repository = SearchRepositoryImpl(remote, videoDao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `search emits fresh results`() = runTest {
        remote.searchResults = listOf(rawVideo("res1"))

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).isEqualTo(SearchResults("kotlin", success.data.videos))
            assertThat(success.data.videos.map { it.id }).containsExactly("res1")
            awaitComplete()
        }
    }

    @Test
    fun `blank query returns empty results without network call`() = runTest {
        repository.search("   ").test {
            val success = awaitItem() as UiState.Success
            assertThat(success.data.videos).isEmpty()
            awaitComplete()
        }

        assertThat(remote.searchCalls).isEqualTo(0)
    }

    @Test
    fun `search emits cached results when network fails`() = runTest {
        remote.searchError = IOException("offline")
        val now = System.currentTimeMillis()
        videoDao.insertAll(
            listOf(
                rawVideo("cached1").toEntity(
                    SearchRepositoryImpl.searchKey("kotlin"),
                    now,
                ),
            ),
        )

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.videos.map { it.id }).containsExactly("cached1")
            awaitComplete()
        }
    }

    @Test
    fun `search emits error when network fails and cache empty`() = runTest {
        remote.searchError = IOException("offline")

        repository.search("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(SearchRepositoryImpl.ERROR_LOAD_SEARCH)
            awaitComplete()
        }
    }

    private fun rawVideo(id: String) = RawVideoSummary(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
    )
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.SearchRepositoryImplTest"`

Expected: FAIL (`Unresolved reference: SearchRepositoryImpl`).

- [ ] **Step 3: Створи `SearchRepositoryImpl.kt`**

```kotlin
package com.ioi.youtube.data.repository

import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.mapper.toEntity
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
    private val cachedVideoDao: CachedVideoDao,
) : SearchRepository {

    override fun search(query: String): Flow<UiState<SearchResults>> = flow {
        emit(UiState.Loading)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(UiState.Success(SearchResults(query = query, videos = emptyList())))
            return@flow
        }
        val key = searchKey(trimmed)
        val cached = cachedVideoDao.getBySource(key)
        if (cached.isNotEmpty()) {
            emit(
                UiState.Success(
                    data = SearchResults(trimmed, cached.map { it.toDomain() }),
                    fromCache = true,
                ),
            )
        }
        try {
            val fresh = remote.search(trimmed)
            cachedVideoDao.replaceSource(
                key,
                fresh.map { it.toEntity(key, System.currentTimeMillis()) },
            )
            emit(UiState.Success(SearchResults(trimmed, fresh.map { it.toDomain() })))
        } catch (e: Exception) {
            if (cached.isEmpty()) emit(UiState.Error(ERROR_LOAD_SEARCH, e))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SOURCE_PREFIX = "search:"
        const val ERROR_LOAD_SEARCH = "Не вдалося виконати пошук"

        fun searchKey(query: String) = "$SOURCE_PREFIX$query"
    }
}
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.SearchRepositoryImplTest"`

Expected: PASS (4 тести).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add search repository with offline fallback and tests"
```

---

### Task 2.11: CommentRepositoryImpl

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/data/repository/CommentRepositoryImpl.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/repository/CommentRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `CommentRepository` (Task 1.8), `YouTubeRemoteDataSource` (Task 2.4), `FakeRemoteDataSource` (Task 2.5), `TestData.comments()` (Task 1.9).
- Produces: `@Singleton class CommentRepositoryImpl` з `companion object.ERROR_LOAD_COMMENTS`. Коментарі не кешуються — завжди мережа (спека §5.4 кешує стрічку та метадані, не коментарі).

- [ ] **Step 1: Напиши падаючий тест `CommentRepositoryImplTest.kt`**

```kotlin
package com.ioi.youtube.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class CommentRepositoryImplTest {

    private val remote = FakeRemoteDataSource()
    private val repository = CommentRepositoryImpl(remote)

    @Test
    fun `comments emit success from remote`() = runTest {
        remote.comments = listOf(
            RawComment("c1", "Alice", null, "First!", 10, "1 day ago", true, true),
        )

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).hasSize(1)
            assertThat(success.data.first().authorName).isEqualTo("Alice")
            awaitComplete()
        }
    }

    @Test
    fun `comments emit error when remote fails`() = runTest {
        remote.commentsError = IOException("offline")

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(CommentRepositoryImpl.ERROR_LOAD_COMMENTS)
            awaitComplete()
        }
    }

    @Test
    fun `empty comments from disabled section emit success with empty list`() = runTest {
        remote.comments = emptyList()

        repository.getComments("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data).isEmpty()
            awaitComplete()
        }
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.CommentRepositoryImplTest"`

Expected: FAIL (`Unresolved reference: CommentRepositoryImpl`).

- [ ] **Step 3: Створи `CommentRepositoryImpl.kt`**

```kotlin
package com.ioi.youtube.data.repository

import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.CommentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
) : CommentRepository {

    override fun getComments(videoId: String): Flow<UiState<List<Comment>>> = flow {
        emit(UiState.Loading)
        try {
            emit(UiState.Success(remote.fetchComments(videoId).map { it.toDomain() }))
        } catch (e: Exception) {
            emit(UiState.Error(ERROR_LOAD_COMMENTS, e))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val ERROR_LOAD_COMMENTS = "Не вдалося завантажити коментарі"
    }
}
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.repository.CommentRepositoryImplTest"`

Expected: PASS (3 тести).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add comment repository with tests"
```

---

### Task 2.12: DI-модулі + ініціалізація в Application

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/di/DataModule.kt`
- Create: `app/src/main/java/com/ioi/youtube/di/RepositoryModule.kt`
- Modify: `app/src/main/java/com/ioi/youtube/IOIYoutubeApplication.kt`

**Interfaces:**
- Consumes: `OkHttpDownloader`, `ExtractorInitializer` (Task 2.1), `IOIDatabase` + DAO (Task 2.7), реалізації репозиторіїв (Tasks 2.9–2.11), `NewPipeRemoteDataSource` (Task 2.4).
- Produces: Hilt-граф: `OkHttpClient`, `Downloader`, `IOIDatabase`, DAO; біндинги `VideoRepository`/`SearchRepository`/`CommentRepository`/`YouTubeRemoteDataSource`. `IOIYoutubeApplication` викликає `ExtractorInitializer.init()` в `onCreate()`.

- [ ] **Step 1: Онови `IOIYoutubeApplication.kt`**

```kotlin
package com.ioi.youtube

import android.app.Application
import com.ioi.youtube.data.extractor.ExtractorInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IOIYoutubeApplication : Application() {

    @Inject
    lateinit var extractorInitializer: ExtractorInitializer

    override fun onCreate() {
        super.onCreate()
        extractorInitializer.init()
    }
}
```

- [ ] **Step 2: Створи `DataModule.kt`**

```kotlin
package com.ioi.youtube.di

import android.content.Context
import androidx.room.Room
import com.ioi.youtube.data.extractor.OkHttpDownloader
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.local.IOIDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val DATABASE_NAME = "ioitube.db"

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDownloader(client: OkHttpClient): Downloader = OkHttpDownloader(client)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IOIDatabase =
        Room.databaseBuilder(context, IOIDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCachedVideoDao(database: IOIDatabase): CachedVideoDao =
        database.cachedVideoDao()

    @Provides
    fun provideCachedVideoDetailsDao(database: IOIDatabase): CachedVideoDetailsDao =
        database.cachedVideoDetailsDao()
}
```

- [ ] **Step 3: Створи `RepositoryModule.kt`**

```kotlin
package com.ioi.youtube.di

import com.ioi.youtube.data.remote.NewPipeRemoteDataSource
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.data.repository.CommentRepositoryImpl
import com.ioi.youtube.data.repository.SearchRepositoryImpl
import com.ioi.youtube.data.repository.VideoRepositoryImpl
import com.ioi.youtube.domain.repository.CommentRepository
import com.ioi.youtube.domain.repository.SearchRepository
import com.ioi.youtube.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: NewPipeRemoteDataSource): YouTubeRemoteDataSource
}
```

- [ ] **Step 4: Перевір збірку**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` — Hilt згенерував повний граф із реальними репозиторіями.

- [ ] **Step 5: Перевір ініціалізацію NewPipe в тестi**

У `ExtractorInitializerTest.kt` (Task 2.1) додай другий тест:

```kotlin
    @Test
    fun `youtube service is available after init`() {
        ExtractorInitializer(TestDownloader()).init()

        val service = org.schabi.newpipe.extractor.NewPipe.getService(
            org.schabi.newpipe.extractor.ServiceList.YouTube,
        )

        assertThat(service).isNotNull()
    }
```

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.data.extractor.ExtractorInitializerTest"`

Expected: PASS (2 тести). Пiдтверджує, що `NewPipe.init` реально викликається з нашим `Downloader`.

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add hilt modules and initialize extractor in application"
```

---

## Review Focus фази 2

1. **NewPipe повертає -1 / null замість даних** (`viewCount`, `likeCount`, `duration`, `description`, `commentsInfo`, progressive stream) → безпечні дефолти (0/"" / порожній список / `Error`), крешiв немає → Tasks 2.3, 2.4.
2. **URL у довiльних форматах** → парсер (Task 2.2) + ID з `StreamInfoItem.getUrl()` якщо regex не спрацював — вiдсутнiй ID дає порожній рядок, а не креш.
3. **Мережа впала з порожнім кешем** → `Error` + «Спробувати знову» → Task 2.9, 2.10, 2.11.
4. **Мережа впала з непорожнім кешем** → `Success(fromCache = true)` без `Error` → Task 2.9, 2.10.
5. **Коментарі вимкненi** → `Success(emptyList())`, не `Error` → Tasks 2.4, 2.11.

