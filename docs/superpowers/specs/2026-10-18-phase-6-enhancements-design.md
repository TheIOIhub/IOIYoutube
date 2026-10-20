# IOITube — Фаза 6: меню, плеєр, шортси (Design Spec)

**Date:** 2026-10-18
**Status:** Затверджено, реалізовано (6.1–6.11)
**Батьківська спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`
**Стан:** фази 1–5 реалізовано, застосунок працює на телефоні власника

Ця спека **доповнює** батьківську і скасовує в ній рівно два положення:

- §5.5 «Три основні екрани: Головна → Пошук → Відео» → екранів чотири: **Головна → Shorts → Пошук → Відео**.
- Неявне «застосунок завжди портретний» (жорсткий `android:screenOrientation="portrait"` у маніфесті) → **екран відео вільно обертається**, решта екранів лишається портретною.

---

## 1. Мета і скоуп

Власник проєкту після тестування на телефоні попросив 5 змін:

| № | Запит власника | Що буде зроблено |
|---|---|---|
| 1 | «прибрати написи кнопок в меню (головна і Пошук), збільшимо трохи цим місце. лишити іконки» | Нижнє меню без підписів: лише іконки + `contentDescription` для доступності |
| 2 | «Коментарі під відео зробити згорнутими, коли користувач захоче то розгорне і почитає» | Блок «Коментарі (N)» з кнопкою «Показати коментарі»; список не рендериться, доки не розгорнуто |
| 3 | «в плеєрі відео треба додати змогу обирати качество бітрейту» | Меню якості: «Авто» + 144p…1080p з бітрейтом; джерело — HLS-манифест YouTube |
| 4 | «Плеєр з відео має реагувати на повороти телефону і повертати відео і на весь екран» | Екран відео обертається сам, кнопка «на весь екран», у ландшафті відео займає весь екран |
| 5 | «Додати знизу до меню кнопку "шортс", шоб мона було гортати і дивитись шортси» | Третій таб Shorts: вертикальний свайп, автоплей, цикл, потік справжніх шортсів |

**Поза скоупом фази 6** (свідомо не робимо):

- підписки, акаунт, історія переглядів;
- коментарі/лайки під шортсами;
- кешування шортсів у Room (стрічка шортсів тягнеться заново — як коментарі в фазі 4);
- вибір якості поза плеєром відео (у шортсах якість адаптивна, меню немає);
- Picture-in-Picture, фонове відтворення, вимкнення звуку в шортсах;
- пагінація всередині вкладки Shorts одного каналу.

---

## 2. Дослідження (перевірено на реальній мережі 2026-10-18)

Дані зібрано тимчасовими діагностичними тестами (видалені перед комітом) + `javap` по jar-никах бібліотек.

### 2.1 Якості відео

| Факт | Значення |
|---|---|
| Progressive-потоки (відео+аудіо одним файлом) | у популярного відео (`dQw4w9WgXcQ`) рівно **один — 360p**, 444 226 біт/с, `avc1.42001E, mp4a.40.2` |
| Video-only потоки | 14 штук: 720p (vp9 та avc1), 1080p (vp9 1.5 Мбіт/с і avc1 4.3 Мбіт/с), 1440p (8.9 Мбіт/с), 2160p (18 Мбіт/с) |
| Аудіо | 5 доріжок: opus 160k, opus 70k, opus 50k, m4a 40 128k |
| `StreamInfo.getHlsUrl()` | **робочий манифест**: `https://manifest.googlevideo.com/api/manifest/hls_variant/...`, HTTP 200, `Content-Type: application/vnd.apple.mpegurl`, 17 варіантів 144p…1080p + окремі аудіодоріжки (`#EXT-X-MEDIA ... TYPE=AUDIO`) |
| `StreamInfo.getDashMpdUrl()` | **порожній** — DASH-шлях відпадає |

**Висновок:** меню якості можливе лише через HLS. Альтернатива (склеювання video-only + audio через `MergingMediaSource`) гірша: більше коду, немає адаптивного перемикання, і ламається на зміні бітрейту мережі.

Обмеження HLS: URL привʼязаний до IP клієнта та має `expire` (~6 год) — кешувати не можна, завжди беремо свіжий із `StreamInfo`.

### 2.2 Шортси

| Джерело | Результат |
|---|---|
| `browseId=FEshorts` (innertube) | HTTP 400 — як `FEtrending`/`FEexplore` |
| Кіоски бібліотеки | єдиний робочий — `live`; шортс-кіоска немає |
| Пошук (`SearchExtractor`) | `isShortFormContent` = `false` у всіх 40 перевірених елементів — пошук шортсів не віддає |
| Хештег-сторінки | класів `*hashtag*` у jar NewPipeExtractor 0.26.5 **немає** |
| Рекомендації шортса (`relatedItems`) | 0 шортсів із 20 — звичайні довгі відео |
| **Вкладка Shorts каналу** | `getChannelExtractor(url)` → `tabs` містить `.../shorts` → `getChannelTabExtractor(tab)` → `fetchPage()` → `initialPage.items`: **48 елементів, усі `isShortFormContent = true`** |
| Формат шортса | вертикальне відео 360×640 (9:16); `StreamInfo.getInfo("https://www.youtube.com/shorts/<id>")` працює |
| Тривалість елемента вкладки | `-1` (невідома) → у UI шортсів тривалість не показуємо |
| Канал без вкладки Shorts | вкладки просто немає в `tabs` (перевірено на 2 каналах із 6) → канал пропускаємо |

**Висновок:** анонімної стрічки Shorts у YouTube не існує; єдине реальне джерело — вкладки Shorts конкретних каналів. Оскільки власник хоче «як на YouTube, рандомні шортси», канали беремо **динамічно зі стрічки «Головна»** і перемішуємо результат.

### 2.3 Поворот

У `AndroidManifest.xml` активність зафіксована як `android:screenOrientation="portrait"` → поворот неможливий у принципі. `MainActivity` не має `configChanges`, тож без нього поворот перестворює Activity і плеєр починає відео з нуля.

### 2.4 Доступні API Media3 1.5.1 (перевірено `javap`)

- `TrackSelectionParameters.Builder`: `setOverrideForType(TrackSelectionOverride)`, `clearOverridesOfType(int)`, `build()`.
- `Player`: `getCurrentTracks()`, `getTrackSelectionParameters()` / `setTrackSelectionParameters(...)`, `setRepeatMode(int)`.
- `Tracks.getGroups(): ImmutableList<Tracks.Group>`; `Group`: `getType()`, `isSupported()`, `length`, `getTrackFormat(int)`, `getMediaTrackGroup()`.
- `TrackSelectionOverride(TrackGroup, int)`; `Format` має публічні поля `height`, `width`, `bitrate`, `codecs`.
- `MimeTypes.APPLICATION_M3U8`, `C.TRACK_TYPE_VIDEO`.
- Іконки (уже підключений `material-icons-extended`): `SmartDisplay`, `Tune`, `Fullscreen`, `FullscreenExit`.

---

## 3. Зміна 1: нижнє меню — лише іконки

- Нижнє меню виноситься в окремий компонент `IOITubeBottomBar(currentRoute, onNavigate)` (`presentation/navigation/IOITubeBottomBar.kt`) — щоб його можна було тестувати без Hilt/NavHost.
- У `NavigationBarItem` **немає** `label`; залишається `icon = { Icon(..., contentDescription = "Головна") }`. Підпис для TalkBack зберігається, візуально підписів немає.
- Висота меню стає меншою (Material3 `NavigationBar` без підписів використовує лише іконку) — те, що просив власник («збільшимо цим місце»).
- Третій пункт (Shorts) додається в межах зміни 5 (той самий файл).

**Критерій:** у меню видно 2 іконки (3 після зміни 5), тексту «Головна»/«Пошук» на екрані немає, але `onNodeWithContentDescription("Головна")` знаходить елемент.

---

## 4. Зміна 2: коментарі згорнуті за замовчуванням

`CommentsSection(state, onRetry, modifier)`:

- Заголовок-рядок: `Коментарі (N)` — `N` лише при `UiState.Success` (кількість коментарів); інакше просто `Коментарі`.
- Праворуч — `TextButton`: `Показати коментарі`, коли згорнуто; `Згорнути`, коли розгорнуто.
- Стан розгортання — `rememberSaveable` (переживає поворот екрана).
- Згорнуто → вміст **не** рендериться взагалі (ні спінера, ні списку, ні помилки, ні «Коментарів немає»).
- Розгорнуто → поточна поведінка без змін: `Loading` → «Завантаження…» + спінер; `Error` → текст помилки + «Спробувати знову»; `Success(порожньо)` → «Коментарі немає»; `Success` → список `CommentItem`.
- Завантаження коментарів *не* змінюється (репозиторій і ViewModel ті самі) — згортання суто візуальне.

**Критерій:** одразу після відкриття відео тексту коментарів на екрані немає; після натискання «Показати коментарі» — зʼявляється; після «Згорнути» — зникає.

---

## 5. Зміна 3: вибір якості (HLS)

### 5.1 Дані

`RawVideoStream` (data) і `VideoStream` (domain) отримують поле `hlsUrl: String? = null`:

```kotlin
data class RawVideoStream(
    val url: String,            // progressive (відео+аудіо) — запасний варіант
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null, // HLS-манифест (усі якості) або null
)
```

`NewPipeRemoteDataSource.fetchVideoStream` заповнює `hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() }`.
Маппер `RawVideoStream.toDomain()` переносить поле.

Залежність: `androidx.media3:media3-exoplayer-hls:1.5.1` (перевірено — артефакт доступний на Google Maven). HLS-модуль потрібен, бо без нього `DefaultMediaSourceFactory` не здатен створити джерело для `application/x-mpegURL`.

### 5.2 Вибір джерела відтворення

```kotlin
data class PlayerSource(
    val url: String,          // що граємо: HLS, якщо є, інакше progressive
    val isHls: Boolean,
    val fallbackUrl: String?, // progressive-URL для відкату; null, якщо його немає
)

fun VideoStream?.toPlayerSource(): PlayerSource?
```

Правила:

1. `hlsUrl` не порожній → `PlayerSource(url = hlsUrl, isHls = true, fallbackUrl = url)`.
2. інакше `url` не порожній → `PlayerSource(url = url, isHls = false, fallbackUrl = null)`.
3. нічого немає → `null` (плеєр показує «Відео недоступне»).

### 5.3 Відтворення

- `MediaItem.Builder().setUri(source.url).setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null).build()`.
- **Відкат:** на `Player.Listener.onPlayerError`, якщо `source.isHls` і є `fallbackUrl` — перемикаємось на `fallbackUrl` (новий `MediaItem`, `prepare()`), пишемо `Log.e("IOITube", "HLS failed, falling back to progressive", error)` і більше не показуємо меню якості для цього відео. Якщо відкату немає — наявна поведінка (`PlayerView` показує помилку).
- HLS-манифест не кешуємо.

### 5.4 Меню якості

Чиста логіка (тестована без Android):

```kotlin
data class VideoTrackInfo(val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int)
data class QualityOption(val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int)

fun chooseQualityOptions(tracks: List<VideoTrackInfo>): List<QualityOption>
fun formatQualityLabel(height: Int, bitrate: Int): String
```

Правила `chooseQualityOptions`:

- треки з `height <= 0` ігноруються;
- на кожну висоту — **один** варіант із найбільшим бітрейтом (у YouTube на 1080p буває avc1 4.3 Мбіт/с і vp9 1.5 Мбіт/с — у меню показуємо один рядок на якість);
- сортування за спаданням висоти, при однаковій висоті — за спаданням бітрейту;
- порожній список → порожній результат (меню не показується).

`formatQualityLabel`:

- `bitrate < 1_000_000` → `"444 Кбіт/с"` (ціле, без дробів);
- інакше → `"4.3 Мбіт/с"` (один знак після коми; `Locale.US`, як у `Formatters`);
- разом із висотою: `"1080p · 4.3 Мбіт/с"`.

UI:

- Кнопка в правому верхньому куті відео, іконка `Icons.Filled.Tune`, підпис поточного вибору: `Авто` або `1080p`.
- Натискання → `DropdownMenu`: `Авто` + рядки якостей; поточний вибір позначено.
- Вибір якості: `player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()`
  `.setOverrideForType(TrackSelectionOverride(group, index)).build()`; `Авто` → `.clearOverridesOfType(C.TRACK_TYPE_VIDEO)`.
- Джерело списку — `player.currentTracks` (реальні доріжки манифеста). Якщо `onTracksChanged` ще не прийшов або відео доріжок немає — кнопки немає.
- Меню доступне лише коли грає HLS (`source.isHls && !usingFallback`).

---

## 6. Зміна 4: поворот і повний екран

### 6.1 Маніфест

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden">
```

`android:screenOrientation="portrait"` прибираємо.
`configChanges` обовʼязковий: без нього Activity перестворюється на повороті, `ExoPlayer` гине й відео починається з нуля.

### 6.2 Орієнтація за маршрутом

```kotlin
fun orientationForRoute(route: String?): Int =
    if (route == Routes.PLAYER) ActivityInfo.SCREEN_ORIENTATION_SENSOR
    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
```

`IOITubeApp` у `LaunchedEffect(currentRoute)` виставляє `activity.requestedOrientation`.
Екран відео — вільний поворот (`SENSOR`), решта застосунку — портрет.

### 6.3 Повний екран

- Стан «повний екран» — це **ландшафтна орієнтація** (`LocalConfiguration.orientation == ORIENTATION_LANDSCAPE`), окремого булєвого прапорця немає. Тобто поворот телефона і кнопка працюють однаково.
- Кнопка в правому верхньому куті відео: `Icons.Filled.Fullscreen` / `Icons.Filled.FullscreenExit`. Натискання:
  - увійти: `activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE`;
  - вийти: `activity.requestedOrientation = SCREEN_ORIENTATION_SENSOR` (далі систему вирівняє портрет, якщо телефон вертикально).
- Системні бари: у ландшафті — `WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())`, у портреті — `show(...)`. Після виходу з екрана — завжди `show` (`DisposableEffect`).
- У ландшафті рендеримо **лише плеєр** на весь екран (`fillMaxSize`), без заголовка, опису, коментарів і рекомендацій. У портреті — як зараз (16:9 + `LazyColumn`).
- `PlayerView`: `resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT`, `keepScreenOn = true` (екран не гасне під час відтворення).

### 6.4 Відтворення не перезапускається

Оскільки Activity не перестворюється (`configChanges`), а `VideoPlayer` тримає плеєр у `remember(videoUrl)`, після повороту відео продовжує грати з тієї самої позиції.

---

## 7. Зміна 5: вкладка Shorts

### 7.1 Дані

`RawVideoSummary` отримує `uploaderUrl: String = ""` (заповнюється з `StreamInfoItem.getUploaderUrl()`).
Поле **не** потрапляє ні в домен (`VideoSummary`), ні в Room — міграції бази немає.

`YouTubeRemoteDataSource` отримує два методи:

```kotlin
suspend fun fetchShorts(): List<RawVideoSummary>      // канали зі стрічки «Головна»
suspend fun fetchMoreShorts(): List<RawVideoSummary>  // канали зі випадкової теми (для «гортати далі»)
```

`fetchShorts()`:

1. `fetchHomeFeed()` → унікальні непорожні `uploaderUrl`, максимум `SHORTS_CHANNEL_LIMIT = 6`;
2. паралельно (`async`) по кожному каналу — `shortsOfChannel(url)`;
3. помилка окремого каналу не валить усю стрічку (`runCatching`), канал без вкладки Shorts дає порожній список;
4. якщо **всі** канали впали з помилкою — кидаємо першу помилку (репозиторій покаже `Error`);
5. обʼєднання → `distinctBy { it.id }` → `shuffled()` (порядок випадковий, «як у YouTube»).

`shortsOfChannel(channelUrl)`:

```text
getChannelExtractor(channelUrl).fetchPage()
tabs.firstOrNull { it.url.orEmpty().contains("/shorts") }   // немає → emptyList()
getChannelTabExtractor(tab).fetchPage()
initialPage.items.filterIsInstance<StreamInfoItem>()
    .filter { it.isShortFormContent }
    .map { it.toRawSummary() }
```

`fetchMoreShorts()` — та сама схема, але канали беруться з `search(випадкова тема з SHORTS_QUERIES)`
(`SHORTS_QUERIES` = список тем українською: музика, гумор, тварини, спорт, ігри, кулінарія, авто, подорожі).
Потрібен, бо повторний `fetchShorts()` дав би ті самі канали → ті самі шортси → «гортати далі» нічого б не додало.

### 7.2 Репозиторій і Use Cases

```kotlin
interface ShortsRepository {
    fun getShorts(): Flow<UiState<List<VideoSummary>>>
    fun getMoreShorts(): Flow<UiState<List<VideoSummary>>>
}
```

Кешу немає (як коментарі): `emit(Loading)` → `Success`/`Error`. Помилка: `Не вдалося завантажити шортси`.
`CancellationException` перекидається далі, `try` вузький (лише мережевий виклик), лог через `Log.e("IOITube", ...)` — як в інших репозиторіях.

Use Cases: `GetShortsUseCase`, `GetMoreShortsUseCase` (один Use Case — одна дія).

### 7.3 ViewModel

```kotlin
data class ShortsUiState(
    val shorts: UiState<List<VideoSummary>> = UiState.Loading,
    val streams: Map<String, UiState<VideoStream>> = emptyMap(),
    val loadingMore: Boolean = false,
)

class ShortsViewModel(getShorts, getMoreShorts, getVideoStream) : ViewModel() {
    val uiState: StateFlow<ShortsUiState>
    fun retry()                   // перезавантажити стрічку
    fun loadStream(videoId: String)   // ліниво, ідемпотентно (повторний виклик нічого не робить)
    fun retryStream(videoId: String)  // скинути помилку й завантажити знову
    fun loadMore()                // дописати нові унікальні шортси в кінець
}
```

- `streams` — `Map<String, UiState<VideoStream>>` у межах сесії ViewModel (повторно не вантажимо).
- `loadMore()`: `loadingMore = true` → `getMoreShorts()`; на `Success` — дописати лише ті, чий `id` ще не зустрічався (порядок наявних не змінюється); на `Error` — список лишається як був (помилка «гортання далі» не руйнує екран).

### 7.4 Екран

- Маршрут `shorts` (`Routes.SHORTS = "shorts"`), третій пункт нижнього меню з іконкою `Icons.Filled.SmartDisplay` і `contentDescription = "Shorts"`.
- `VerticalPager` (`rememberPagerState(pageCount = { shorts.size })`) — одна сторінка = один шортс, на весь екран.
- **Активна** сторінка: плеєр грає автоматично з `RepeatMode.REPEAT_MODE_ONE` (цикл), тап по відео — пауза/відтворення.
- **Неактивні** сторінки: прев'ю (Coil `AsyncImage`) + іконка відтворення, без плеєра. Плеєр існує рівно один (у активної сторінки) — жодних 5 плеєрів у памʼяті.
- Накладений текст знизу: назва (до 2 рядків), канал, `Formatters.formatViews(viewCount)`. Тривалість не показуємо (`duration == -1` у вкладці Shorts).
- Потік вантажиться ліниво: `LaunchedEffect(page)` → `loadStream(id)` для сторінок `page` і `page + 1` (наступна вже готова до свайпу).
- Помилка потоку на сторінці: текст + «Спробувати знову» → `retryStream(id)`.
- Коли користувач на передостанній сторінці (`page >= size - 2`) і `!loadingMore` → `loadMore()`.
- Стани стрічки: `Loading` → спінер; `Error` → `ErrorBox` («Не вдалося завантажити шортси» + «Спробувати знову»); порожньо → «Тут поки що порожньо».

---

## 8. Зведення змін файлів

| Файл | Зміна |
|---|---|
| `app/build.gradle.kts` | + `androidx.media3:media3-exoplayer-hls:1.5.1` |
| `app/src/main/AndroidManifest.xml` | − `screenOrientation="portrait"`, + `configChanges` |
| `data/model/RemoteModels.kt` | `RawVideoStream.hlsUrl`, `RawVideoSummary.uploaderUrl` |
| `data/remote/YouTubeRemoteDataSource.kt` | + `fetchShorts()`, `fetchMoreShorts()` |
| `data/remote/NewPipeRemoteDataSource.kt` | HLS-URL у потоці; шортс-стрічка з вкладок каналів |
| `data/mapper/RemoteMappers.kt` | перенести `hlsUrl` |
| `data/repository/ShortsRepositoryImpl.kt` (новий) | стрічка шортсів без кешу |
| `di/RepositoryModule.kt` | біндинг `ShortsRepository` |
| `domain/model/VideoStream.kt` | + `hlsUrl` |
| `domain/repository/ShortsRepository.kt` (новий) | інтерфейс |
| `domain/usecase/GetShortsUseCase.kt`, `GetMoreShortsUseCase.kt` (нові) | Use Cases |
| `presentation/navigation/Routes.kt` | + `SHORTS` |
| `presentation/navigation/IOITubeBottomBar.kt` (новий) | меню без підписів + 3 пункти |
| `presentation/navigation/IOITubeApp.kt` | меню-компонент, маршрут `shorts`, орієнтація за маршрутом |
| `presentation/player/PlayerSource.kt` (новий) | `PlayerSource`, `toPlayerSource()` |
| `presentation/player/QualityOptions.kt` (новий) | `VideoTrackInfo`, `QualityOption`, `chooseQualityOptions`, `formatQualityLabel` |
| `presentation/player/VideoPlayer.kt` | HLS + відкат + меню якості + кнопка повного екрана |
| `presentation/player/PlayerScreen.kt` | повний екран (ландшафт), передача `PlayerSource` |
| `presentation/player/CommentsSection.kt` | згортання |
| `presentation/shorts/ShortsScreen.kt`, `ShortsViewModel.kt`, `ShortVideoPlayer.kt` (нові) | екран шортсів |
| `docs/manual-test-checklist.md` | нові сценарії (меню, коментарі, якість, поворот, шортси) |

Domain не залежить від Android/NewPipe/Media3 (крім чисел `ActivityInfo` — ні, вони лише в presentation) — правило фази 1 зберігається.

---

## 9. Обробка помилок

| Ситуація | Поведінка |
|---|---|
| HLS не запустився (`onPlayerError`) | відкат на progressive-URL, `Log.e`, меню якості ховається; відео продовжує грати |
| HLS немає, progressive є | грає progressive, меню якості немає (одна якість) |
| Потоку немає взагалі | «Відео недоступне» (без змін) |
| Канал без вкладки Shorts | канал тихо пропускається |
| Усі канали шортсів впали | `Error` → «Не вдалося завантажити шортси» + «Спробувати знову» |
| Канали є, але шортсів немає | `Success(порожньо)` → «Тут поки що порожньо» |
| Потік конкретного шортса не завантажився | помилка на цій сторінці + «Спробувати знову» (решта стрічки працює) |
| Немає інтернету на екрані шортсів | `Error` + «Спробувати знову» (кешу шортсів немає — свідомо) |

---

## 10. Стратегія тестування

| Рівень | Що покриваємо |
|---|---|
| JVM (JUnit + MockK + Turbine) | `chooseQualityOptions` (дедуплікація за висотою, сортування, порожній вхід, `height <= 0`), `formatQualityLabel` (Кбіт/Мбіт), `VideoStream.toPlayerSource()` (HLS → пріоритет, відкат, порожній), `fetchShorts`/`fetchMoreShorts` (фільтр `isShortFormContent`, канал без вкладки, усі канали впали → throw, дедуплікація), `ShortsRepositoryImpl` (Loading→Success, Error, без кешу), `ShortsViewModel` (стрічка, `loadStream` ідемпотентний, `retryStream`, `loadMore` дописує унікальні, помилка `loadMore` не руйнує список) |
| Robolectric + Compose | `IOITubeBottomBar` (іконки є, підписів немає), `CommentsSection` (згорнуто/розгорнуто/повторне згортання), `VideoPlayer` («Відео недоступне», немає меню без доріжок), `PlayerScreen` (у ландшафті немає коментарів і заголовка, кнопка повного екрана викликає колбек), `ShortsScreen` (накладення з назвою, прев'ю неактивної сторінки) |
| Живий смоук (env-gated `IOITUBE_LIVE_TESTS=1`) | `fetchShorts()` на реальній мережі: непорожньо, у кожного елемента є 11-символьний id; HLS-манифест: HTTP 200 + `#EXTM3U` + ≥ 2 варіанти якості |
| Збірка | `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` (+ підписаний APK) |
| Ручне (власник на телефоні) | 5 пунктів зі скоупу + що відео не перезапускається після повороту |

Базова лінія: 103 тести / 0 падінь / 1 skip (env-gated). Після фази 6 — більше, без нових падінь.
`:app:testReleaseUnitTest` має 15 передаварійних Robolectric-падінь на чистому дереві — це не регресія.

---

## 11. Ризики та обмеження

1. **HLS на пристрої не перевірено** (немає ні емулятора, ні телефону) — саме тому є відкат на progressive і ховання меню. Перевірятиме власник на телефоні.
2. **Поворот/повний екран** перевіряються наближено (Robolectric qualifiers), справжній поворот — лише на телефоні.
3. **Шортси залежні від розмітки YouTube**: якщо канал не має вкладки Shorts або Library втратить `shortsLockupViewModel`, стрічка буде коротшою/порожньою. Порожня стрічка — штатний стан, не креш.
4. **Порядок шортсів випадковий** (`shuffled()`) — тести не мають залежати від порядку, лише від складу.
5. **HLS-манифест** привʼязаний до IP та `expire` ~6 год → не кешуємо, завжди свіжий.
6. **`fetchShorts` робить 5 пошукових запитів + 6 вкладок каналів** — перше завантаження ~2–4 с. Прийнятно для окремої вкладки; показуємо спінер.
7. Стрічка шортсів без кешу: без інтернету екран шортсів показує помилку (свідоме рішення, як коментарі).

---

## 12. Definition of Done (фаза 6)

- Усі 5 пунктів працюють на телефоні власника.
- `./gradlew testDebugUnitTest` — зелений, без нових падінь проти базової лінії.
- `./gradlew :app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease` — зелені.
- Живий смоук-тест шортсів і HLS — зелений (з мережею).
- `docs/manual-test-checklist.md` оновлено новими сценаріями.
- `dist/` містить свіжі release + debug APK, MD5 повідомлено власнику.
