# IOITube — Фаза 8: плеєр, аудіодоріжки, масштаб, рекомендації (Implementation Plan)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Прибрати рестарт відео при повороті, закріпити плеєр зверху, зробити рекомендації вертикальними великими картками, додати вибір якості (merged video-only + audio) і аудіодоріжки (дубляж), а також перемикач `Fit`/`Fill`.

**Architecture:** `NewPipeRemoteDataSource` віддає `videoOnly` + `audioTracks`. Доменні чисті функції будують список якостей/доріжок і `PlaybackPlan` (`Merged → Progressive → Hls`). `PlayerHolder` володіє єдиним `ExoPlayer` на весь екран відео (створюється **до** розгалуження портрет/ландшафт), зберігає позицію при перемиканні джерела й робить fallback. `VideoPlayer` більше не створює плеєр — лише показує його та меню.

**Tech Stack:** Media3 1.5.1 (`exoplayer`, `exoplayer-hls`, `ui`), Compose Material 3, Hilt, NewPipeExtractor 0.26.5, Robolectric + Truth + MockK.

**Design spec:** `docs/superpowers/specs/2026-10-20-phase-8-player-design.md`

---

## Global Constraints

- Усі команди Gradle — після `source .agent-env.sh`.
- Тести: `./gradlew :app:testDebugUnitTest`. Один клас: `--tests "com.ioi.youtube.presentation.player.PlayerHolderTest"`.
- Живі тести — лише з `IOITUBE_LIVE_TESTS=1`.
- UI-рядки — українською.
- **HLS-модель фази 6 скасовується**: основний шлях — `MergingMediaSource`; HLS — запасний.
- `ShortVideoPlayer` і `PlayerSource.toPlayerSource()` **не змінюються** (шортси лишаються на HLS).
- Один `ExoPlayer` на екран відео: жодного `ExoPlayer` всередині `VideoPlayer`.
- Після кожної задачі — зелений `testDebugUnitTest` і окремий коміт.
- Наприкінці: `assembleDebug` + `assembleRelease`, оновлені APK у `dist/` і MD5.

---

## Task 1: Доменні варіанти якості/аудіо + мапінг із NewPipe

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/domain/model/VideoStream.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/model/StreamOptions.kt`
- Create: `app/src/test/java/com/ioi/youtube/domain/model/StreamOptionsTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/mapper/RemoteMappers.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/mapper/RemoteMappersTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`
- Modify: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt`

- [ ] **Step 1: Тести чистих функцій (RED)**

`StreamOptionsTest.kt`:

```kotlin
package com.ioi.youtube.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamOptionsTest {

    private fun variant(url: String, height: Int, bitrate: Int) = VideoVariant(url, height, bitrate)

    private fun audio(
        id: String,
        label: String,
        bitrate: Int,
        original: Boolean = false,
        url: String = "https://a/$id",
    ) = AudioTrackOption(id, label, null, url, bitrate, original)

    @Test
    fun `video variants keep the best bitrate per height and sort by height`() {
        val chosen = chooseVideoVariants(
            listOf(
                variant("a", 1080, 1_000_000),
                variant("b", 1080, 3_000_000),
                variant("c", 720, 500_000),
                variant(" ", 480, 100),
            ),
        )

        assertThat(chosen.map { it.url }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `default video prefers 1080 over 2160`() {
        val variants = chooseVideoVariants(
            listOf(variant("4k", 2160, 1), variant("fhd", 1080, 1), variant("hd", 720, 1)),
        )

        assertThat(defaultVideo(variants)?.height).isEqualTo(1080)
    }

    @Test
    fun `default video falls back to the highest when everything is above 1080`() {
        val variants = chooseVideoVariants(listOf(variant("4k", 2160, 1), variant("2k", 1440, 1)))

        assertThat(defaultVideo(variants)?.height).isEqualTo(2160)
    }

    @Test
    fun `audio tracks keep the best bitrate per id and put original first`() {
        val chosen = chooseAudioTracks(
            listOf(
                audio("en.1", "English · оригінал", 128_000, original = true),
                audio("uk.1", "Українська · дубляж", 96_000),
                audio("uk.1", "Українська · дубляж", 160_000),
            ),
        )

        assertThat(chosen.map { it.id }).containsExactly("en.1", "uk.1").inOrder()
        assertThat(chosen.first { it.id == "uk.1" }.bitrate).isEqualTo(160_000)
    }

    @Test
    fun `default audio prefers the original track`() {
        val tracks = listOf(
            audio("uk.1", "Українська", 160_000),
            audio("en.1", "English", 128_000, original = true),
        )

        assertThat(defaultAudio(tracks)?.id).isEqualTo("en.1")
    }

    @Test
    fun `audio label uses the Ukrainian display name`() {
        assertThat(audioLabel("uk", null, original = false)).isEqualTo("Українська · дубляж")
        assertThat(audioLabel("en", null, original = true)).isEqualTo("Англійська · оригінал")
    }

    @Test
    fun `audio label falls back to the name and then to the index`() {
        assertThat(audioLabel(null, "Dubbed", original = false)).isEqualTo("Dubbed · дубляж")
        assertThat(audioLabel(null, null, original = true, index = 3))
            .isEqualTo("Аудіо 3 · оригінал")
    }

    @Test
    fun `video label includes height and bitrate`() {
        assertThat(formatVideoLabel(1080, 4_300_000)).isEqualTo("1080p · 4.3 Мбіт/с")
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.domain.model.StreamOptionsTest"`
Expected: FAIL — unresolved `VideoVariant`, `AudioTrackOption`, `chooseVideoVariants` тощо.

- [ ] **Step 2: Домен**

`VideoStream.kt` — повністю:

```kotlin
package com.ioi.youtube.domain.model

/** Відео-доріжка без звуку (для MergingMediaSource). */
data class VideoVariant(
    val url: String,
    val height: Int,
    val bitrate: Int,
)

/** Аудіо-доріжка; label готовий до показу в меню. */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val url: String,
    val bitrate: Int,
    val original: Boolean,
)

data class VideoStream(
    val url: String,
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,
    val videoOnly: List<VideoVariant> = emptyList(),
    val audioTracks: List<AudioTrackOption> = emptyList(),
)
```

`StreamOptions.kt`:

```kotlin
package com.ioi.youtube.domain.model

import java.util.Locale

/**
 * Одна якість на висоту (у YouTube 1080p буває avc1 і vp9 — лишаємо жирніший бітрейт),
 * за спаданням висоти.
 */
fun chooseVideoVariants(variants: List<VideoVariant>): List<VideoVariant> =
    variants.filter { it.url.isNotBlank() }
        .groupBy { it.height }
        .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<VideoVariant> { it.height }.thenByDescending { it.bitrate })

/** Найвища якість не вище 1080p; якщо такої немає — найвища наявна. */
fun defaultVideo(variants: List<VideoVariant>): VideoVariant? =
    variants.filter { it.height in 1..1080 }.maxByOrNull { it.height }
        ?: variants.maxByOrNull { it.height }

/** Одна доріжка на audioTrackId (без id — на URL), оригінал першим. */
fun chooseAudioTracks(tracks: List<AudioTrackOption>): List<AudioTrackOption> =
    tracks.filter { it.url.isNotBlank() }
        .groupBy { it.id.ifBlank { it.url } }
        .map { (_, sameTrack) -> sameTrack.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<AudioTrackOption> { it.original }.thenBy { it.label })

fun defaultAudio(tracks: List<AudioTrackOption>): AudioTrackOption? =
    tracks.firstOrNull { it.original } ?: tracks.firstOrNull()

/**
 * Підпис доріжки: мова з великої літери → назва від YouTube → «Аудіо N».
 * Суфікс показує, чи це оригінал, чи дубляж.
 */
fun audioLabel(
    language: String?,
    name: String?,
    original: Boolean,
    index: Int = 1,
): String {
    val languageName = language
        ?.takeIf { it.isNotBlank() }
        ?.let { Locale.forLanguageTag(it.replace('_', '-')).getDisplayName(Locale("uk")) }
        ?.takeIf { it.isNotBlank() }
    val base = languageName ?: name?.takeIf { it.isNotBlank() } ?: "Аудіо $index"
    val suffix = if (original) "оригінал" else "дубляж"
    return "${base.replaceFirstChar { it.uppercase() }} · $suffix"
}

fun formatVideoLabel(height: Int, bitrate: Int): String = "${height}p · ${formatBitrate(bitrate)}"

private fun formatBitrate(bitrate: Int): String = when {
    bitrate <= 0 -> "—"
    bitrate < 1_000_000 -> "${bitrate / 1000} Кбіт/с"
    else -> "%.1f Мбіт/с".format(Locale.US, bitrate / 1_000_000.0)
}
```

- [ ] **Step 3: Сирі моделі + мапер**

`RemoteModels.kt` — додати:

```kotlin
data class RawVideoVariant(
    val url: String,
    val height: Int,
    val bitrate: Int,
)

data class RawAudioTrack(
    val id: String,
    val name: String?,
    val language: String?,
    val url: String,
    val bitrate: Int,
    val original: Boolean,
)
```

`RawVideoStream` — нові поля в кінці:

```kotlin
    val hlsUrl: String? = null,
    val videoOnly: List<RawVideoVariant> = emptyList(),
    val audioTracks: List<RawAudioTrack> = emptyList(),
```

`RemoteMappers.kt` — замінити `RawVideoStream.toDomain()`:

```kotlin
fun RawVideoStream.toDomain(): VideoStream = VideoStream(
    url = url,
    resolution = resolution,
    format = format,
    hlsUrl = hlsUrl,
    videoOnly = chooseVideoVariants(videoOnly.map { it.toDomain() }),
    audioTracks = chooseAudioTracks(
        audioTracks.mapIndexed { index, track -> track.toDomain(index + 1) },
    ),
)

fun RawVideoVariant.toDomain(): VideoVariant = VideoVariant(
    url = url,
    height = height,
    bitrate = bitrate,
)

fun RawAudioTrack.toDomain(index: Int = 1): AudioTrackOption = AudioTrackOption(
    id = id,
    label = audioLabel(language, name, original, index),
    language = language,
    url = url,
    bitrate = bitrate,
    original = original,
)
```

Імпорти: `AudioTrackOption`, `VideoVariant`, `audioLabel`, `chooseAudioTracks`, `chooseVideoVariants`.

- [ ] **Step 4: Мапінг у NewPipeRemoteDataSource**

Замінити `fetchVideoStream`:

```kotlin
    override suspend fun fetchVideoStream(videoId: String): RawVideoStream =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            val muxed = pickBestVideoStream(info)
            RawVideoStream(
                url = muxed.url.orEmpty(),
                resolution = muxed.resolution.orEmpty(),
                format = muxed.format?.name.orEmpty(),
                hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
                videoOnly = info.videoOnlyStreams.mapNotNull { stream ->
                    val url = stream.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    RawVideoVariant(
                        url = url,
                        height = stream.resolutionPixels(),
                        bitrate = stream.bitrate,
                    )
                },
                audioTracks = info.audioStreams.mapNotNull { stream ->
                    val url = stream.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    RawAudioTrack(
                        id = stream.audioTrackId.orEmpty(),
                        name = stream.audioTrackName,
                        language = stream.audioLocale?.language,
                        url = url,
                        bitrate = stream.averageBitrate,
                        original = stream.audioTrackType == AudioTrackType.ORIGINAL,
                    )
                },
            )
        }
```

Імпорти: `RawAudioTrack`, `RawVideoVariant`, `org.schabi.newpipe.extractor.stream.AudioTrackType`.

- [ ] **Step 5: Тести мапінгу (спочатку додати, потім GREEN)**

`RemoteMappersTest` — додати:

```kotlin
    @Test
    fun `maps video-only variants and audio tracks`() {
        val raw = RawVideoStream(
            url = "https://v/360.mp4",
            resolution = "360p",
            format = "MPEG-4",
            hlsUrl = "https://hls",
            videoOnly = listOf(RawVideoVariant("https://v/1080.mp4", 1080, 2_000_000)),
            audioTracks = listOf(
                RawAudioTrack("uk.1", "Ukrainian", "uk", "https://a/uk", 128_000, false),
            ),
        )

        val domain = raw.toDomain()

        assertThat(domain.videoOnly.single().height).isEqualTo(1080)
        assertThat(domain.audioTracks.single().label).isEqualTo("Українська · дубляж")
        assertThat(domain.audioTracks.single().original).isFalse()
    }
```

`NewPipeRemoteDataSourceTest` — додати хелпери:

```kotlin
    private fun videoOnlyStream(url: String, resolution: String): VideoStream {
        val stream = mockk<VideoStream>(relaxed = true)
        every { stream.url } returns url
        stream.setFinalField("resolution", resolution)
        stream.setFinalField("isVideoOnly", true)
        every { stream.bitrate } returns 2_000_000
        return stream
    }

    private fun audioStream(
        id: String,
        name: String,
        locale: Locale,
        type: AudioTrackType,
    ): AudioStream {
        val stream = mockk<AudioStream>(relaxed = true)
        every { stream.url } returns "https://a/$id"
        every { stream.averageBitrate } returns 128_000
        every { stream.audioTrackId } returns id
        every { stream.audioTrackName } returns name
        every { stream.audioLocale } returns locale
        every { stream.audioTrackType } returns type
        return stream
    }
```

І тест:

```kotlin
    @Test
    fun `video stream maps video-only variants and audio tracks`() = runTest {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.videoStreams } returns listOf(videoStream("https://p/360.mp4", "360p"))
        every { info.videoOnlyStreams } returns listOf(videoOnlyStream("https://p/1080.mp4", "1080p"))
        every { info.audioStreams } returns listOf(
            audioStream("uk.1", "Ukrainian", Locale("uk"), AudioTrackType.DUBBED),
            audioStream("en.1", "English", Locale("en"), AudioTrackType.ORIGINAL),
        )
        every { info.hlsUrl } returns "https://hls_variant"
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val stream = NewPipeRemoteDataSource().fetchVideoStream("dQw4w9WgXcQ")

        assertThat(stream.videoOnly.map { it.height }).containsExactly(1080)
        assertThat(stream.audioTracks.map { it.id }).containsExactly("en.1", "uk.1").inOrder()
        assertThat(stream.audioTracks.first { it.id == "uk.1" }.original).isFalse()
        assertThat(stream.audioTracks.first { it.id == "en.1" }.original).isTrue()
    }
```

Імпорти: `AudioStream`, `AudioTrackType`, `java.util.Locale`.

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(player): expose video-only variants and audio tracks"
```

---

## Task 2: `PlaybackPlan` (чиста логіка вибору джерела)

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlaybackPlan.kt`
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/PlaybackPlanTest.kt`

- [ ] **Step 1: Тест (RED)**

```kotlin
package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import org.junit.Test

class PlaybackPlanTest {

    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val audio = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)

    private fun stream(
        url: String = "https://v/360.mp4",
        hlsUrl: String? = "https://hls",
    ) = VideoStream(
        url = url,
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = hlsUrl,
        videoOnly = listOf(video),
        audioTracks = listOf(audio),
    )

    @Test
    fun `merged plan wins when video and audio are selected`() {
        assertThat(buildPlaybackPlan(stream(), video, audio))
            .isEqualTo(PlaybackPlan.Merged("https://v/1080.mp4", "https://a/uk"))
    }

    @Test
    fun `progressive is used when nothing is selected`() {
        assertThat(buildPlaybackPlan(stream(), null, null))
            .isEqualTo(PlaybackPlan.Progressive("https://v/360.mp4"))
    }

    @Test
    fun `hls is the last fallback`() {
        assertThat(buildPlaybackPlan(stream(url = ""), null, null))
            .isEqualTo(PlaybackPlan.Hls("https://hls"))
    }

    @Test
    fun `auto mode prefers hls`() {
        assertThat(buildPlaybackPlan(stream(), null, null, auto = true))
            .isEqualTo(PlaybackPlan.Hls("https://hls"))
    }

    @Test
    fun `no playable source returns null`() {
        assertThat(buildPlaybackPlan(VideoStream("", "0p", ""), null, null)).isNull()
        assertThat(buildPlaybackPlan(null, video, audio)).isNull()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlaybackPlanTest"`
Expected: FAIL — unresolved `buildPlaybackPlan`.

- [ ] **Step 2: Реалізація**

`PlaybackPlan.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant

sealed interface PlaybackPlan {
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackPlan
    data class Progressive(val url: String) : PlaybackPlan
    data class Hls(val url: String) : PlaybackPlan
}

/**
 * Пріоритет: merged (обрана якість + обрана мова) → progressive (муксований) → HLS.
 * `auto = true` — пункт «Авто (HLS)»: HLS першим, progressive запасним.
 */
fun buildPlaybackPlan(
    stream: VideoStream?,
    video: VideoVariant?,
    audio: AudioTrackOption?,
    auto: Boolean = false,
): PlaybackPlan? {
    if (stream == null) return null
    if (!auto && video != null && audio != null &&
        video.url.isNotBlank() && audio.url.isNotBlank()
    ) {
        return PlaybackPlan.Merged(video.url, audio.url)
    }
    if (auto) {
        stream.hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Hls(it) }
    }
    stream.url.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Progressive(it) }
    stream.hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackPlan.Hls(it) }
    return null
}
```

- [ ] **Step 3: GREEN + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlaybackPlanTest"
git add -A
git commit -m "feat(player): add playback plan selection"
```

---

## Task 3: `PlayerHolder` — один плеєр, збереження позиції, fallback

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerHolder.kt`
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerHolderTest.kt`

- [ ] **Step 1: Тест (RED)**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerHolderTest {

    private lateinit var holder: PlayerHolder

    @Before
    fun setUp() {
        holder = PlayerHolder(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        holder.release()
    }

    private val audio = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)

    private fun stream(vararg variants: VideoVariant) = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = "https://hls",
        videoOnly = variants.toList(),
        audioTracks = listOf(audio),
    )

    @Test
    fun `switching video keeps the current position`() {
        val first = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
        val second = VideoVariant("https://v/720.mp4", 720, 1_000_000)
        holder.update(stream(first, second), first, audio, auto = false)
        holder.player.seekTo(5_000)

        holder.update(stream(first, second), second, audio, auto = false)

        assertThat(holder.player.currentPosition).isAtLeast(4_000)
    }

    @Test
    fun `switching audio keeps the current position`() {
        val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
        val english = AudioTrackOption("en.1", "English · оригінал", "en", "https://a/en", 128_000, true)
        val source = stream(video).copy(audioTracks = listOf(audio, english))
        holder.update(source, video, audio, auto = false)
        holder.player.seekTo(3_000)

        holder.update(source, video, english, auto = false)

        assertThat(holder.player.currentPosition).isAtLeast(2_000)
    }

    @Test
    fun `stream without any playable url reports an error instead of crashing`() {
        holder.update(VideoStream("", "0p", ""), null, null, auto = true)

        assertThat(holder.error).isEqualTo(PlayerHolder.ERROR_PLAYBACK)
    }

    @Test
    fun `release is idempotent`() {
        holder.release()
        holder.release()

        assertThat(holder.error).isNull()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerHolderTest"`
Expected: FAIL — unresolved `PlayerHolder`.

- [ ] **Step 2: Реалізація**

`PlayerHolder.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant

/**
 * Володіє єдиним ExoPlayer на екран відео.
 *
 * Створюється в [PlayerScreen] **до** розгалуження портрет/ландшафт, тому поворот
 * не перестворює плеєр і не скидає позицію. Перемикання якості/мови перебудовує
 * джерело з `setMediaSource(source, currentPosition)`.
 */
class PlayerHolder(
    context: Context,
    private val sourceFactory: (Context, PlaybackPlan) -> MediaSource = ::mediaSourceFor,
) {

    private val appContext = context.applicationContext
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var stream: VideoStream? = null
    private var video: VideoVariant? = null
    private var audio: AudioTrackOption? = null
    private var auto = false
    private var currentPlan: PlaybackPlan? = null
    private var loaded = false
    private var released = false

    var error: String? by mutableStateOf(null)
        private set

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    recover()
                }
            },
        )
    }

    /** Перше завантаження або зміна вибору; позиція зберігається. */
    fun update(
        stream: VideoStream,
        video: VideoVariant?,
        audio: AudioTrackOption?,
        auto: Boolean,
    ) {
        if (released) return
        this.stream = stream
        this.video = video
        this.audio = audio
        this.auto = auto

        val plan = buildPlaybackPlan(stream, video, audio, auto)
        if (plan == null) {
            error = ERROR_PLAYBACK
            return
        }
        if (loaded && plan == currentPlan) return
        apply(plan, if (loaded) player.currentPosition else 0L)
    }

    fun release() {
        if (released) return
        released = true
        player.release()
    }

    private fun apply(plan: PlaybackPlan, positionMs: Long) {
        error = null
        currentPlan = plan
        val playWhenReady = if (loaded) player.playWhenReady else true
        player.setMediaSource(sourceFactory(appContext, plan), positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        loaded = true
    }

    /**
     * Merged → HLS → progressive. Якщо впав останній — показуємо помилку в UI.
     * Позиція зберігається на кожному кроці.
     */
    private fun recover() {
        val source = stream ?: return
        val fallback = when (currentPlan) {
            is PlaybackPlan.Merged -> source.hlsUrl?.takeIf { it.isNotBlank() }
                ?.let(PlaybackPlan::Hls)
                ?: source.url.takeIf { it.isNotBlank() }?.let(PlaybackPlan::Progressive)

            is PlaybackPlan.Hls -> source.url.takeIf { it.isNotBlank() }
                ?.let(PlaybackPlan::Progressive)

            else -> null
        }
        if (fallback == null) {
            error = ERROR_PLAYBACK
            return
        }
        apply(fallback, player.currentPosition)
    }

    companion object {
        const val ERROR_PLAYBACK = "Не вдалося відтворити відео"
    }
}

internal fun mediaSourceFor(context: Context, plan: PlaybackPlan): MediaSource = when (plan) {
    is PlaybackPlan.Merged -> MergingMediaSource(
        progressiveSource(context, plan.videoUrl),
        progressiveSource(context, plan.audioUrl),
    )

    is PlaybackPlan.Progressive -> progressiveSource(context, plan.url)

    is PlaybackPlan.Hls -> HlsMediaSource.Factory(DefaultDataSource.Factory(context))
        .createMediaSource(MediaItem.fromUri(plan.url))
}

private fun progressiveSource(context: Context, url: String): MediaSource =
    ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        .createMediaSource(MediaItem.fromUri(url))
```

`DefaultDataSource` живе в `androidx.media3.datasource` (модуль `media3-datasource`, транзитивний до exoplayer).

- [ ] **Step 3: GREEN + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerHolderTest"
git add -A
git commit -m "feat(player): add single-player holder with position preservation"
```

---

## Task 4: `VideoPlayer` без власного ExoPlayer + меню якості/аудіо/масштабу

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt` (повна заміна)
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/VideoScale.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/player/VideoPlayerTest.kt` (повна заміна)

- [ ] **Step 1: Тести (RED)**

`VideoScale` + `resizeModeFor` тест можна покласти в `VideoPlayerTest`:

```kotlin
    @Test
    fun `fit and fill map to the right resize mode`() {
        assertThat(resizeModeFor(VideoScale.FIT))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        assertThat(resizeModeFor(VideoScale.FILL))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    }
```

Новий `VideoPlayerTest.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer

    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val ukrainian = AudioTrackOption("uk.1", "Українська · дубляж", "uk", "https://a/uk", 128_000, false)
    private val english = AudioTrackOption("en.1", "English · оригінал", "en", "https://a/en", 128_000, true)
    private val stream = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        hlsUrl = "https://hls",
        videoOnly = listOf(video),
        audioTracks = listOf(english, ukrainian),
    )

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    private fun show(
        stream: VideoStream? = this.stream,
        player: ExoPlayer? = this.player,
        audioOptions: List<AudioTrackOption> = this.stream.audioTracks,
        onSelectAudio: (AudioTrackOption) -> Unit = {},
        onScaleChange: (VideoScale) -> Unit = {},
        selectedVideo: VideoVariant? = video,
        selectedAudio: AudioTrackOption? = english,
        isFullscreen: Boolean = false,
        onToggleFullscreen: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(
                    player = player,
                    stream = stream,
                    videoOptions = this.stream.videoOnly,
                    audioOptions = audioOptions,
                    selectedVideo = selectedVideo,
                    selectedAudio = selectedAudio,
                    onSelectAuto = {},
                    onSelectVideo = {},
                    onSelectAudio = onSelectAudio,
                    scale = VideoScale.FIT,
                    onScaleChange = onScaleChange,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
        }
    }

    @Test
    fun `missing stream shows unavailable message instead of player`() {
        show(stream = null)

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `available stream hides the unavailable message`() {
        show()

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `audio section is shown only with more than one track`() {
        show(audioOptions = listOf(english, ukrainian))

        composeRule.onNodeWithContentDescription("Налаштування").performClick()
        composeRule.onNodeWithText("Аудіо").assertExists()
        composeRule.onNodeWithText("Українська · дубляж").assertExists()
    }

    @Test
    fun `single audio track hides the audio section`() {
        show(audioOptions = listOf(ukrainian), selectedAudio = ukrainian)

        composeRule.onNodeWithContentDescription("Налаштування").performClick()
        composeRule.onNodeWithText("Аудіо").assertDoesNotExist()
    }

    @Test
    fun `selecting an audio track reports it`() {
        var selected: AudioTrackOption? = null
        show(onSelectAudio = { selected = it })

        composeRule.onNodeWithContentDescription("Налаштування").performClick()
        composeRule.onNodeWithText("Українська · дубляж").performClick()

        assertThat(selected).isEqualTo(ukrainian)
    }

    @Test
    fun `scale item reports fill`() {
        var scale: VideoScale? = null
        show(onScaleChange = { scale = it })

        composeRule.onNodeWithContentDescription("Налаштування").performClick()
        composeRule.onNodeWithText("Заповнити").performClick()

        assertThat(scale).isEqualTo(VideoScale.FILL)
    }

    @Test
    fun `fit and fill map to the right resize mode`() {
        assertThat(resizeModeFor(VideoScale.FIT))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        assertThat(resizeModeFor(VideoScale.FILL))
            .isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    }

    @Test
    fun `fullscreen button is hidden without a callback`() {
        show()

        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
    }

    @Test
    fun `unavailable fullscreen still offers the exit button`() {
        show(stream = null, isFullscreen = true, onToggleFullscreen = {})

        composeRule.onNodeWithText("Відео недоступне").assertExists()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.VideoPlayerTest"`
Expected: FAIL — сигнатура `VideoPlayer` не збігається.

- [ ] **Step 2: `VideoScale.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.media3.ui.AspectRatioFrameLayout

enum class VideoScale { FIT, FILL }

internal fun resizeModeFor(scale: VideoScale): Int = when (scale) {
    VideoScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
```

- [ ] **Step 3: Повністю замінити `VideoPlayer.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.domain.model.formatVideoLabel

/**
 * Показує готовий плеєр. `ExoPlayer` створює й володіє ним [PlayerHolder];
 * тут лише PlayerView, меню та індикація помилки.
 */
@Composable
fun VideoPlayer(
    player: ExoPlayer?,
    stream: VideoStream?,
    videoOptions: List<VideoVariant>,
    audioOptions: List<AudioTrackOption>,
    selectedVideo: VideoVariant?,
    selectedAudio: AudioTrackOption?,
    onSelectAuto: () -> Unit,
    onSelectVideo: (VideoVariant) -> Unit,
    onSelectAudio: (AudioTrackOption) -> Unit,
    scale: VideoScale,
    onScaleChange: (VideoScale) -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    error: String? = null,
    onRetry: () -> Unit = {},
    unavailableText: String = "Відео недоступне",
) {
    val auto = selectedVideo == null && selectedAudio == null
    val exoPlayer = player
    val videoStream = stream
    if (exoPlayer == null || videoStream == null ||
        buildPlaybackPlan(videoStream, selectedVideo, selectedAudio, auto) == null
    ) {
        UnavailableBox(
            modifier = modifier,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            text = unavailableText,
        )
        return
    }

    var menuOpen by remember(videoStream) { mutableStateOf(false) }
    var hlsOptions by remember(videoStream) { mutableStateOf(emptyList<QualityOption>()) }

    DisposableEffect(exoPlayer, videoStream) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                hlsOptions = chooseQualityOptions(tracks.videoTrackInfos())
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        modifier = modifier.then(
            if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = exoPlayer
                    useController = true
                    resizeMode = resizeModeFor(scale)
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeModeFor(scale)
            },
            onRelease = { view -> view.player = null },
        )

        if (error != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text("Спробувати знову", color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Налаштування",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (videoStream.hlsUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Авто (HLS)") },
                            onClick = {
                                onSelectAuto()
                                menuOpen = false
                            },
                        )
                    }
                    videoOptions.forEach { variant ->
                        DropdownMenuItem(
                            text = { Text(formatVideoLabel(variant.height, variant.bitrate)) },
                            onClick = {
                                onSelectVideo(variant)
                                menuOpen = false
                            },
                        )
                    }
                    if (auto && hlsOptions.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Авто") },
                            onClick = {
                                exoPlayer.trackSelectionParameters =
                                    qualityParameters(exoPlayer, null)
                                menuOpen = false
                            },
                        )
                        hlsOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatQualityLabel(option.height, option.bitrate)) },
                                onClick = {
                                    exoPlayer.trackSelectionParameters =
                                        qualityParameters(exoPlayer, option)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                    if (audioOptions.size > 1) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Аудіо") },
                            onClick = {},
                            enabled = false,
                        )
                        audioOptions.forEach { track ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (track == selectedAudio) "✓ ${track.label}" else track.label,
                                    )
                                },
                                onClick = {
                                    onSelectAudio(track)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (scale == VideoScale.FIT) "Заповнити" else "Вписати") },
                        onClick = {
                            onScaleChange(
                                if (scale == VideoScale.FIT) VideoScale.FILL else VideoScale.FIT,
                            )
                            menuOpen = false
                        },
                    )
                }
            }

            if (onToggleFullscreen != null) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Вийти з повного екрана" else "На весь екран",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnavailableBox(
    modifier: Modifier,
    isFullscreen: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    text: String,
) {
    Box(
        modifier = modifier.then(
            if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        if (onToggleFullscreen != null) {
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onToggleFullscreen,
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "Вийти з повного екрана" else "На весь екран",
                    tint = Color.White,
                )
            }
        }
    }
}
```

- [ ] **Step 4: GREEN + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.VideoPlayerTest"
git add -A
git commit -m "feat(player): render injected player with quality, audio and scale menu"
```

---

## Task 5: `PlayerScreen` — закріплений плеєр, вертикальні рекомендації, збереження holder

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt`
- Delete: `app/src/main/java/com/ioi/youtube/presentation/player/RecommendationsSection.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsScreen.kt` — **не чіпати** (перевірити, що `toPlayerSource` лишився)

- [ ] **Step 1: Тести (RED)**

Додати в `PlayerScreenTest`:

```kotlin
    @Test
    fun `player stays pinned while the page scrolls`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        val before = composeRule.onNodeWithTag("player-container")
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.onRoot().performGesture {
            swipeUp(startY = 400f, endY = 50f, durationMillis = 300)
        }

        val after = composeRule.onNodeWithTag("player-container")
            .fetchSemanticsNode().boundsInRoot.top

        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `fullscreen toggle keeps the same player holder`() {
        var fullscreen by mutableStateOf(false)
        var created = 0
        val holders = mutableListOf<PlayerHolder>()

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    isFullscreen = fullscreen,
                    onToggleFullscreen = {},
                    holderFactory = { context ->
                        PlayerHolder(context).also {
                            created++
                            holders += it
                        }
                    },
                )
            }
        }

        composeRule.runOnIdle { fullscreen = true }
        composeRule.waitForIdle()

        assertThat(created).isEqualTo(1)
        holders.forEach { it.release() }
    }

    @Test
    fun `recommendations open a video`() {
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = { clicked = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Video aaaaaaaaaaa"))
        composeRule.onNodeWithText("Video aaaaaaaaaaa").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }
```

Імпорти: `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.test.fetchSemanticsNode`, `androidx.compose.ui.test.hasScrollAction`, `androidx.compose.ui.test.hasText`, `androidx.compose.ui.test.onNode`, `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.performScrollToNode`.

Run: `./gradlew :app:testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerScreenTest"`
Expected: FAIL — немає `holderFactory`, `player-container`.

- [ ] **Step 2: Переписати `PlayerScreen`**

Структура (повний код нижче keys; зберегти `VideoMetaSection`, `StreamErrorRow`, `buildMetaLine` без змін):

```kotlin
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    holderFactory: (Context) -> PlayerHolder = { PlayerHolder(it) },
) {
    when (val videoState = uiState.video) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = videoState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            val details = videoState.data
            val stream = (uiState.stream as? UiState.Success)?.data
            val context = LocalContext.current

            // Holder створюється ДО гілки портрет/ландшафт і не залежить від неї.
            val holder = remember(stream, context) { stream?.let { holderFactory(context) } }
            val videoOptions = remember(stream) { chooseVideoVariants(stream?.videoOnly.orEmpty()) }
            val audioOptions = remember(stream) { chooseAudioTracks(stream?.audioTracks.orEmpty()) }
            val initialVideo = remember(stream) { defaultVideo(videoOptions) }
            val initialAudio = remember(stream) { defaultAudio(audioOptions) }
            var selectedVideo by remember(stream) { mutableStateOf(initialVideo) }
            var selectedAudio by remember(stream) { mutableStateOf(initialAudio) }
            var scale by rememberSaveable(stream?.url) { mutableStateOf(VideoScale.FIT) }

            LaunchedEffect(holder, stream, selectedVideo, selectedAudio) {
                if (holder == null || stream == null) return@LaunchedEffect
                holder.update(
                    stream = stream,
                    video = selectedVideo,
                    audio = selectedAudio,
                    auto = selectedVideo == null && selectedAudio == null,
                )
            }

            DisposableEffect(holder) {
                onDispose { holder?.release() }
            }

            val player: @Composable (Modifier) -> Unit = { playerModifier ->
                VideoPlayer(
                    player = holder?.player,
                    stream = stream,
                    videoOptions = videoOptions,
                    audioOptions = audioOptions,
                    selectedVideo = selectedVideo,
                    selectedAudio = selectedAudio,
                    onSelectAuto = {
                        selectedVideo = null
                        selectedAudio = null
                    },
                    onSelectVideo = { selectedVideo = it },
                    onSelectAudio = { track ->
                        selectedAudio = track
                        // З HLS-режиму вибір мови має одразу перевести на merged:
                        // без video-only плану нема з чим склеювати аудіо.
                        if (selectedVideo == null) selectedVideo = defaultVideo(videoOptions)
                    },
                    scale = scale,
                    onScaleChange = { scale = it },
                    error = holder?.error,
                    onRetry = onRetry,
                    modifier = playerModifier,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }

            if (isFullscreen) {
                player(modifier.fillMaxSize().testTag("player-container"))
            } else {
                Column(modifier = modifier.fillMaxSize()) {
                    player(Modifier.fillMaxWidth().testTag("player-container"))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item(key = "stream-error") {
                            StreamErrorRow(state = uiState.stream, onRetry = onRetry)
                        }
                        item(key = "meta") {
                            VideoMetaSection(
                                title = details.title,
                                uploaderName = details.uploaderName,
                                metaLine = buildMetaLine(details),
                                description = details.description,
                                fromCache = videoState.fromCache,
                            )
                        }
                        item(key = "comments") {
                            CommentsSection(state = uiState.comments, onRetry = onRetry)
                        }
                        item(key = "recommendations-header") {
                            Text(
                                text = "Рекомендації",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            items = details.recommendedVideos,
                            key = { it.id },
                        ) { video ->
                            VideoCard(video = video, onClick = onVideoClick)
                        }
                    }
                }
            }
        }
    }
}
```

Нові імпорти: `androidx.compose.foundation.layout.Column`, `fillMaxSize`, `LazyColumn`, `items`, `rememberSaveable`, `testTag`, `LocalContext`, `com.ioi.youtube.domain.model.chooseAudioTracks`, `chooseVideoVariants`, `defaultAudio`, `defaultVideo`, `com.ioi.youtube.presentation.components.VideoCard`.

Видалити: `toPlayerSource`, `VideoPlayer(source = ...)`, `RecommendationsSection(...)` і файл `RecommendationsSection.kt` (`git rm`).

- [ ] **Step 3: GREEN**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Якщо `PlayerScreenTest` «player stays pinned» падає через те, що `VideoPlayer` не має семантики (`AndroidView` без testTag), переконайтеся, що `testTag` стоїть на `Box` у `VideoPlayer` через переданий modifier — він має зберегтися.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(player): pin player above scrolling details and recommendations"
```

---

## Task 6: Живий смоук + фінальна збірка

**Files:**
- Modify: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceSmokeTest.kt`

- [ ] **Step 1: Оновити живий тест**

Замінити `fetchHomeFeed()` на:

```kotlin
        val feed = dataSource.fetchFeedPage(FeedTopic.ALL)
```

(це ж стосується фази 7 — якщо ще не зроблено, зробіть тут.)

Додати в кінець тесту «feed search stream details and comments return data»:

```kotlin
        check(stream.videoOnly.isNotEmpty()) { "немає video-only доріжок" }
        check(stream.audioTracks.isNotEmpty()) { "немає аудіодоріжок" }
```

І окремий тест для дубляжу:

```kotlin
    @Test
    fun `video with dubs exposes original and translated audio tracks`() = runBlocking {
        assumeTrue("IOITUBE_LIVE_TESTS=1 не встановлено — живий тест пропущено", live)

        ExtractorInitializer(OkHttpDownloader(client())).init()
        val stream = NewPipeRemoteDataSource().fetchVideoStream("FjcO19YTvD8")

        check(stream.audioTracks.size > 1) { "очікували кілька доріжок, отримали ${stream.audioTracks.size}" }
        check(stream.audioTracks.any { it.original }) { "немає оригінальної доріжки" }
        check(stream.videoOnly.any { it.height >= 720 }) { "немає video-only ≥720p" }
    }
```

- [ ] **Step 2: Прогнати живий смоук**

Run:

```bash
IOITUBE_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests "*SmokeTest*"
```

Expected: PASS. Якщо `FjcO19YTvD8` недоступний — замінити id на інший відео-з-дубляжем із реальної видачі (перевірити через `audioTracks.size > 1`).

- [ ] **Step 3: Повний прогін**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 4: Збірка APK**

```bash
./gradlew :app:assembleDebug :app:assembleRelease
cp app/build/outputs/apk/debug/app-debug.apk dist/IOITube-1.0-debug.apk
cp app/build/outputs/apk/release/app-release.apk dist/IOITube-1.0-release.apk
md5sum dist/IOITube-1.0-debug.apk dist/IOITube-1.0-release.apk
```

- [ ] **Step 5: Ручний смоук (обовʼязково)**

1. Відкрити відео, дочекатися відтворення, повернути телефон — відео продовжується з тієї ж позиції (±2 с), без рестарту.
2. Прокрутити опис/коментарі донизу — плеєр лишається зверху; рекомендації — великі вертикальні картки.
3. Меню → «Заповнити»: чорні смуги зникають; поворот зберігає режим; «Вписати» повертає.
4. Відео з дубляжем: меню → «Аудіо» → «Українська · дубляж»; мова змінюється без закриття, позиція зберігається.
5. Відео без дубляжу: пункту «Аудіо» немає.
6. «Авто (HLS)» грає; вибір конкретної якості перемикає джерело з тією ж позицією.
7. Авіарежим під час відтворення: зʼявляється «Не вдалося відтворити відео» + «Спробувати знову».
8. Вкладка Shorts не змінилась (HLS, свайп, довантаження).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore(release): rebuild apks for player phase"
```
