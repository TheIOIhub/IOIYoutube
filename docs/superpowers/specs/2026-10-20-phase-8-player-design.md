# IOITube — Фаза 8: плеєр, аудіодоріжки, масштаб, рекомендації (Design Spec)

**Date:** 2026-10-20
**Status:** На ревʼю власника
**Батьківська спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`
**Спека фази 6:** `docs/superpowers/specs/2026-10-18-phase-6-enhancements-design.md`

Ця спека **скасовує** висновок фази 6 §2.1:

> «Меню якості можливе лише через HLS. Альтернатива (склеювання video-only + audio через `MergingMediaSource`) гірша…»

Причина перегляду: вибір аудіодоріжки (перекладення) через HLS **неможливий** — манифест містить одну мовну групу. Єдиний шлях — `MergingMediaSource`, і він стає основним; HLS лишається запасним.

Також скасовується «рекомендації — горизонтальний ряд під коментарями»: тепер це вертикальний список, а плеєр закріплений зверху.

---

## 1. Мета і скоуп

| № | Запит власника | Що робиться |
|---|---|---|
| 1 | «рекомендовані знизу — зробити більшими і прокручувати вниз, відео закріплене» | Плеєр фіксований зверху, рекомендації — великі вертикальні картки |
| 2 | «включив і перевернув телефон — відео починає грати спочатку» | Плеєр піднято вище гілки портрет/ландшафт → позиція зберігається |
| 3 | «обирати/розтягувати відео на весь екран, бо є полоски по краях» | Перемикач `Fit` / `Fill` (`RESIZE_MODE_ZOOM`) |
| 4 | «вибір звукової доріжки (перекладення)» | `MergingMediaSource`: video-only обраної якості + аудіо обраної мови |

**Поза скоупом фази 8:**

- вибір аудіодоріжки в шортсах (там HLS і без меню);
- субтитри, швидкість відтворення, Picture-in-Picture, фонове відтворення;
- запамʼятовування мови/якості між різними відео та між запусками;
- адаптивне перемикання якості в merged-режимі;
- вибір аудіо для відео, де екстрактор не віддав доріжок.

---

## 2. Дослідження (реальна мережа, 2026-10-20)

| Відео | Аудіодоріжки | Video-only | Муксований progressive | HLS |
|---|---|---|---|---|
| `Af6i6ChAVTw` (MrBeast) | **22** (`ar.3`…`en.4`), ORIGINAL + DUBBED | 12, 144p…1080p | 1 × 360p | є |
| `FjcO19YTvD8` (документалка) | **21**, серед них **`uk.10` — Ukrainian (DUBBED)** | 14, 144p…2160p | 1 × 360p | є |
| `dQw4w9WgXcQ` | 1 (`null` — без метаданих) | 14, 144p…2160p | 1 × 360p | є |

- На кожну доріжку — 5 аудіопотоків, максимум **160 кбіт/с**.
- `AudioStream` містить `audioTrackId`, `audioTrackName`, `audioLocale`, `audioTrackType` (`ORIGINAL` / `DUBBED`).
- `StreamInfo.getVideoOnlyStreams()` — окремий список; `getVideoStreams()` містить один муксований 360p.
- HLS-манифест має 2 `EXT-X-MEDIA:TYPE=AUDIO`, але це два бітрейти однієї доріжки (`NAME="Default"`), не мови.
- Причина рестарту при повороті: `PlayerScreen` має дві гілки (портрет/ландшафт), у кожній свій `VideoPlayer`; `ExoPlayer` створюється в `remember(source.url)` всередині гілки, тож перемикання гілки його релізить. Activity не перестворюється (у маніфесті є `configChanges`).

---

## 3. Архітектура

### 3.1 Домен

```kotlin
data class VideoVariant(
    val url: String,
    val height: Int,        // 0, якщо невідомо
    val bitrate: Int,
)

data class AudioTrackOption(
    val id: String,         // audioTrackId; "" якщо невідомий
    val label: String,      // "Українська · дубляж", "English · оригінал"
    val language: String?,  // "uk", "en"
    val url: String,        // найкращий бітрейт цієї доріжки
    val bitrate: Int,
    val original: Boolean,
)

data class VideoStream(
    val url: String,                    // муксований progressive (запасний)
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,         // адаптивний запасний
    val videoOnly: List<VideoVariant> = emptyList(),
    val audioTracks: List<AudioTrackOption> = emptyList(),
)
```

Правила побудови (чисті функції, без Media3):

- `chooseVideoVariants(videoOnly)` — дедуп за висотою, лишаємо найвищий бітрейт, сортування за спаданням висоти.
- `chooseAudioTracks(audioStreams)` — групування за `audioTrackId`, найвищий бітрейт на доріжку, `original = type == ORIGINAL`, сортування: оригінал першим, далі за назвою.
- `audioLabel(locale, name, type)` — пріоритет: `locale.getDisplayName(Locale("uk"))` з великої літери (напр. «Українська»), інакше `audioTrackName`, інакше `"Аудіо N"`; суфікс ` · оригінал` / ` · дубляж`.
- `defaultVideo(variants)` — найвища якість **не вище 1080p**; якщо такої немає — найвища наявна.
- `defaultAudio(tracks)` — `original`, інакше перша.

### 3.2 План відтворення

```kotlin
sealed interface PlaybackPlan {
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackPlan
    data class Progressive(val url: String) : PlaybackPlan
    data class Hls(val url: String) : PlaybackPlan
}
```

Пріоритет: `Merged` (є video-only і аудіодоріжка) → `Progressive` (муксований) → `Hls`.
`buildPlaybackPlan(source, video, audio)` — чиста функція, покрита тестами.

### 3.3 PlayerHolder

```kotlin
class PlayerHolder(context: Context) {
    val player: ExoPlayer
    fun load(source: VideoStream, video: VideoVariant?, audio: AudioTrackOption?)
    fun switchVideo(variant: VideoVariant)   // зберігає позицію і playWhenReady
    fun switchAudio(track: AudioTrackOption)
    fun release()
}
```

- `switchVideo` / `switchAudio`: запамʼятати `currentPosition` і `playWhenReady`, `setMediaSource(newSource, positionMs)`, `prepare()`.
- Помилка відтворення: один крок назад по ланцюжку `Merged → Hls (оригінальна мова) → Progressive`, позиція зберігається; якщо впав і останній — повідомлення в UI (як зараз).
- `PlayerHolder` створюється в `PlayerScreen` через `remember(source)` — **до** розгалуження портрет/ландшафт, тому поворот не перестворює плеєр.
- `release()` — у `DisposableEffect` на рівні `PlayerScreen`; пауза на `ON_STOP` лишається.

### 3.4 PlayerScreen

```kotlin
Column {
    VideoPlayer(player = holder.player, ...)          // фіксований, не скролиться
    LazyColumn(Modifier.weight(1f)) {
        item { VideoMetaSection(...) }
        item { CommentsSection(...) }
        item { RecommendationsHeader() }
        items(details.recommendedVideos) { VideoCard(...) }
    }
}
```

- Ландшафт: окрема гілка «лише відео на весь екран», але з **тим самим** `holder`.
- `VideoPlayer` більше не створює `ExoPlayer`; приймає готовий `player` + `source` + вибране відео/аудіо + колбеки.
- `ShortVideoPlayer` (шортси) не змінюється — там HLS через `toPlayerSource()`.

### 3.5 Меню

Одна кнопка (іконка `Tune`) у правому верхньому куті плеєра → `DropdownMenu` з розділами:

1. **Якість** — пункти з `videoVariants`: `Авто (HLS)` (лише якщо є `hlsUrl`), `1080p · 4.3 Мбіт/с`, `720p …`. У merged-режимі вибір перебудовує джерело зі збереженням позиції.
2. **Аудіо** — лише якщо `audioTracks.size > 1`: `English · оригінал`, `Українська · дубляж`, …
3. **Масштаб** — `Вписати` / `Заповнити`.

У HLS-режимі (fallback) меню якості працює як зараз — через `Tracks` (`QualityOptions`).

### 3.6 Масштаб

```kotlin
enum class VideoScale { FIT, FILL }
```

`FIT` → `AspectRatioFrameLayout.RESIZE_MODE_FIT`, `FILL` → `RESIZE_MODE_ZOOM`.
Стан зберігається в `rememberSaveable` (переживає поворот), скидається при відкритті іншого відео.

### 3.7 Залежності

`fetchVideoStream` у `NewPipeRemoteDataSource` розширюється:
читає `info.videoOnlyStreams`, `info.audioStreams`, `info.videoStreams` (перший муксований), `info.hlsUrl` → `RawVideoStream` з новими полями. Кеш деталей відео (`CachedVideoDetailsEntity`) не змінюється: потік ніколи не кешувався.

---

## 4. Тести

| Рівень | Що перевіряємо |
|---|---|
| `VideoVariantOptionsTest` | дедуп за висотою, найвищий бітрейт, спадний порядок, `defaultVideo` ≤1080p |
| `AudioTrackOptionsTest` | дедуп за `audioTrackId`, найкращий бітрейт, ORIGINAL першим, підписи «оригінал»/«дубляж», fallback-назва |
| `PlaybackPlanTest` | пріоритет merged → progressive → hls; порожні списки; відсутність усіх джерел → null |
| `PlayerHolderTest` (Robolectric) | `switchVideo`/`switchAudio` зберігають позицію; `release` викликається один раз |
| `PlayerScreenTest` | плеєр не всередині `LazyColumn`; рекомендації — вертикальний список; клік відкриває відео; при `isFullscreen` використовується той самий holder (фабрика-сеам) |
| `VideoPlayerTest` | `FIT`/`FILL` → правильний `resizeMode`; меню показує «Аудіо» лише за >1 доріжки; виклики `switchVideo`/`switchAudio` |
| `NewPipeRemoteDataSourceTest` (фікстури) | мапінг `videoOnly`/`audioTracks`; муксований 360p як `url`; `hlsUrl` збережено |

Живий смоук (`IOITUBE_LIVE_TESTS=1`) доповнюється перевіркою: для відео з дубляжем `audioTracks.size > 1` і є `original`.

---

## 5. Ризики й обмеження

- **Дві мережеві сесії** (відео + аудіо) замість HLS: трохи більше запитів, але менше ризику розсинхрону, ніж з ручним mux.
- **Перемикання якості** = перезавантаження джерела (~0.5–1 с), позиція зберігається з точністю до секунди.
- **Втрата адаптивності:** у merged-режимі якість фіксована до вибору користувача; «Авто (HLS)» лишається як опція.
- **URL живуть ~6 годин** і привʼязані до IP: потік беремо при кожному відкритті, не кешуємо. 403/помилка → fallback + «Спробувати знову».
- **Екстрактор іноді не віддає метадані доріжок** (`id=null`) → меню «Аудіо» не показуємо.

---

## 6. Критерії приймання

1. Поворот під час відтворення продовжує відео з тієї ж позиції (±2 с), без рестарту й повторного буферизування з нуля.
2. Плеєр лишається зверху при прокручуванні опису, коментарів і рекомендацій; рекомендації — великі вертикальні картки.
3. Перемикач `Fit`/`Fill` прибирає чорні смуги; стан зберігається після повороту.
4. На відео з дубляжем меню показує «Українська · дубляж» (та інші) і перемикання змінює мову без закриття відео, з тією ж позицією.
5. На відео без дубляжу пункт «Аудіо» не показується.
6. Якщо merged-потік не запускається, відео все одно грає через HLS/муксований fallback.
