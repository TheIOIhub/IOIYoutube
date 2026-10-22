# Міні-плеєр IOITube — план реалізації

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Додати YouTube-подібний міні-плеєр: свайп відео вниз або Back у портреті згортає його в плаваючу картку на Home/Search, не перериваючи звук.

**Architecture:** Спільним `PlayerHolder` володіє activity-scoped `PlaybackViewModel` (Hilt). `PlayerScreen` лише показує переданий holder; `MiniPlayer` приєднує той самий ExoPlayer до маленького `PlayerView`. `IOITubeApp` тримає overlay над NavHost і передає сесію в `PlayerRoute`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Compose 2.8.5, Hilt 2.54, Media3 ExoPlayer/UI 1.5.1, ViewModel Compose, Robolectric + Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-10-22-mini-player-design.md`

## Global Constraints

- Версія релізу: `versionCode = 6`, `versionName = "1.2.2"`.
- Не додавати нових dependencies: усе потрібне вже є в `app/build.gradle.kts`.
- Міні-плеєр видно лише на Home і Search; при вході в Shorts відтворення зупиняється.
- Back у портреті згортає відео; Back у fullscreen спершу виходить із fullscreen.
- Один ExoPlayer — лише один активний PlayerView; player-route має `exitTransition`/`popExitTransition = None`.
- `PlayerScreen` не звільняє переданий `PlayerHolder`; це робить лише `PlaybackViewModel.onCleared()`.
- Усі нові UI-рядки — українською.
- GitHub-реліз не публікувати без підтвердження користувача після тесту на TCL 306.
- Робоча директорія: `/home/the101hub/Projects/IOIYoutube`.

## Review Focus

Ці ризики не покриваються простим “щасливим шляхом” і мають бути перевірені в задачах нижче:

1. **Два PlayerView на одному ExoPlayer** під час згортання/розгортання можуть дати чорне відео або вкрасти surface. Покриття: Task 2 lifecycle-тести, Task 6 `exitTransition = None`, ручний тест Task 8.
2. **Back під час завантаження потоку** може залишити невидимий звук. Покриття: Task 3 `minimize requires ready playback`, Task 6 stop-and-close гілка, ручний тест Task 8.
3. **Розгортання скидає якість/мову або перезапускає відео з нуля.** Покриття: Task 3 `begin same id keeps cache and selection`, Task 4 `selectionInitialized`, ручний тест Task 8.
4. **Жест згортання конфліктує з тапами, pinch або fullscreen-жестами.** Покриття: Task 7 `shouldMinimize`, PlayerScreen swipe-тести, тест кнопки “Якість”, наявні fullscreen-тести.
5. **Вхід у Shorts залишає звук довгого відео.** Покриття: Task 3 `stop clears the session`, Task 6 `LaunchedEffect(currentRoute)`, ручний тест Task 8.
6. **Activity destruction не звільняє ExoPlayer.** Покриття: Task 3 `clearing the view model releases the holder`.

---

### Task 1: Зберегти вже зроблену fullscreen-роботу окремим комітом

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerGestures.kt`
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerGesturesTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: нічого.
- Produces: чистий робочий стан гілки перед початком міні-плеєра; версія 1.2.1 уже в build-файлі.

- [ ] **Step 1: Перевірити, що в робочому дереві лише очікувані fullscreen-зміни**

Run:

```bash
git status --short
git diff --stat
```

Expected: modified `PlayerScreen.kt`, `VideoPlayer.kt`, `app/build.gradle.kts`; untracked `PlayerGestures.kt`, `PlayerGesturesTest.kt`. Нічого зайвого.

- [ ] **Step 2: Прогнати цільові тести перед комітом**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerGesturesTest' \
  --tests 'com.ioi.youtube.presentation.player.VideoPlayerTest' \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest'
```

Expected: `BUILD SUCCESSFUL`. Якщо тест падає — не комітити, спершу виправити.

- [ ] **Step 3: Закомітити наявну роботу**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt \
        app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt \
        app/src/main/java/com/ioi/youtube/presentation/player/PlayerGestures.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlayerGesturesTest.kt \
        app/build.gradle.kts
git commit -m "feat(player): add fullscreen gestures and zoom entry animation"
```

---

### Task 2: Додати `stop()`, `isReady` та `isReleased` у PlayerHolder

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerHolder.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerHolderTest.kt`

**Interfaces:**
- Consumes: наявний `PlayerHolder.update(...)`, `release()`.
- Produces:
  - `internal val isReady: Boolean` — `loaded && !released`.
  - `internal val isReleased: Boolean` — `released`.
  - `fun stop()` — зупиняє й очищає джерело, не звільняючи ExoPlayer.

- [ ] **Step 1: Додати тести, що описують нову поведінку**

У `PlayerHolderTest.kt` додати після тесту `stream without any playable url reports an error instead of crashing`:

```kotlin
@Test
fun `stop clears readiness and allows reloading the same plan`() {
    val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    val source = stream(video)

    holder.update(source, video, audio, auto = false)
    assertThat(holder.isReady).isTrue()

    holder.stop()

    assertThat(holder.isReady).isFalse()
    assertThat(holder.isReleased).isFalse()

    holder.update(source, video, audio, auto = false)
    assertThat(holder.isReady).isTrue()
}
```

І розширити наявний тест `release is idempotent`:

```kotlin
@Test
fun `release is idempotent`() {
    holder.release()
    holder.release()

    assertThat(holder.error).isNull()
    assertThat(holder.isReleased).isTrue()
}
```

- [ ] **Step 2: Запустити тести й переконатися, що вони падають**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerHolderTest'
```

Expected: FAIL — unresolved reference `isReady` / `isReleased`.

- [ ] **Step 3: Додати мінімальну реалізацію**

У `PlayerHolder.kt` після `var error` додати:

```kotlin
internal val isReady: Boolean
    get() = loaded && !released

internal val isReleased: Boolean
    get() = released
```

Після `release()` додати:

```kotlin
/**
 * Зупиняє поточне джерело, але лишає ExoPlayer живим для міні-плеєра.
 * Наступний [update] з тим самим планом має завантажити його заново з нуля.
 */
fun stop() {
    if (released) return
    player.stop()
    player.clearMediaItems()
    currentPlan = null
    loaded = false
    stream = null
    video = null
    audio = null
    auto = false
    error = null
}
```

- [ ] **Step 4: Запустити тести й переконатися, що вони проходять**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerHolderTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/PlayerHolder.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlayerHolderTest.kt
git commit -m "feat(player): allow stopping holder without releasing it"
```

---

### Task 3: Створити activity-scoped PlaybackViewModel

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlaybackViewModel.kt`
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/PlaybackViewModelTest.kt`

**Interfaces:**
- Consumes: `PlayerHolder`, `VideoDetails`, `VideoStream`, `VideoVariant`, `AudioTrackOption`.
- Produces:
  - `holder: PlayerHolder` (lazy).
  - `videoId: String?`, `minimized: Boolean`, `cachedDetails`, `cachedStream`, `selectedVideo`, `selectedAudio`, `selectionInitialized`, `canMinimize`.
  - `begin(videoId: String)`, `cache(videoId, details, stream)`, `updateSelection(video, audio)`, `minimize(): Boolean`, `expand()`, `stop()`.

- [ ] **Step 1: Написати тестовий файл**

Створити `app/src/test/java/com/ioi/youtube/presentation/player/PlaybackViewModelTest.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.testutil.TestData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: PlaybackViewModel

    private val details = TestData.details("dQw4w9WgXcQ")
    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val audio = AudioTrackOption(
        "uk.1",
        "Українська · дубляж",
        "uk",
        "https://a/uk",
        128_000,
        false,
    )
    private val stream = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        videoOnly = listOf(video),
        audioTracks = listOf(audio),
    )

    @Before
    fun setUp() {
        store = ViewModelStore()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    private fun createViewModel(): PlaybackViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaybackViewModel(context) as T
        }
        return ViewModelProvider(store, factory)[PlaybackViewModel::class.java]
    }

    @Test
    fun `begin with a new id clears cache and selection`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)

        viewModel.begin("b")

        assertThat(viewModel.videoId).isEqualTo("b")
        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.cachedStream).isNull()
        assertThat(viewModel.selectedVideo).isNull()
        assertThat(viewModel.selectedAudio).isNull()
        assertThat(viewModel.selectionInitialized).isFalse()
    }

    @Test
    fun `begin with the same id keeps cache and selection`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)
        val holder = viewModel.holder

        viewModel.begin("a")

        assertThat(viewModel.cachedDetails).isSameInstanceAs(details)
        assertThat(viewModel.selectedVideo).isEqualTo(video)
        assertThat(viewModel.selectedAudio).isEqualTo(audio)
        assertThat(viewModel.selectionInitialized).isTrue()
        assertThat(viewModel.holder).isSameInstanceAs(holder)
    }

    @Test
    fun `cache ignores a different video id`() {
        viewModel.begin("a")

        viewModel.cache("b", details, stream)

        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.cachedStream).isNull()
    }

    @Test
    fun `minimize requires ready playback`() {
        viewModel.begin("a")
        assertThat(viewModel.minimize()).isFalse()
        assertThat(viewModel.minimized).isFalse()

        viewModel.holder.update(stream, video, audio, auto = false)
        assertThat(viewModel.minimize()).isTrue()
        assertThat(viewModel.minimized).isTrue()

        viewModel.expand()
        assertThat(viewModel.minimized).isFalse()
    }

    @Test
    fun `stop clears the session and readiness`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)
        viewModel.holder.update(stream, video, audio, auto = false)
        assertThat(viewModel.minimize()).isTrue()

        viewModel.stop()

        assertThat(viewModel.videoId).isNull()
        assertThat(viewModel.minimized).isFalse()
        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.selectedVideo).isNull()
        assertThat(viewModel.selectionInitialized).isFalse()
        assertThat(viewModel.canMinimize).isFalse()
    }

    @Test
    fun `clearing the view model releases the holder`() {
        viewModel.begin("a")
        val holder = viewModel.holder

        store.clear()

        assertThat(holder.isReleased).isTrue()
    }
}
```

- [ ] **Step 2: Запустити тест і переконатися, що він падає**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlaybackViewModelTest'
```

Expected: FAIL — unresolved reference `PlaybackViewModel`.

- [ ] **Step 3: Створити PlaybackViewModel**

Створити `app/src/main/java/com/ioi/youtube/presentation/player/PlaybackViewModel.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Activity-scoped сесія відтворення: переживає навігацію між Home/Search/Player.
 * Володіє єдиним [PlayerHolder]; звільняє його лише в [onCleared].
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var holderRef: PlayerHolder? = null

    val holder: PlayerHolder
        get() = holderRef ?: PlayerHolder(context).also { holderRef = it }

    var videoId: String? by mutableStateOf(null)
        private set

    var minimized: Boolean by mutableStateOf(false)
        private set

    var cachedDetails: VideoDetails? by mutableStateOf(null)
        private set

    var cachedStream: VideoStream? by mutableStateOf(null)
        private set

    var selectedVideo: VideoVariant? by mutableStateOf(null)
        private set

    var selectedAudio: AudioTrackOption? by mutableStateOf(null)
        private set

    var selectionInitialized: Boolean by mutableStateOf(false)
        private set

    val canMinimize: Boolean
        get() = videoId != null && holderRef?.isReady == true

    /** Перше відкриття відео або перехід на інше; те саме відео — шлях розгортання. */
    fun begin(videoId: String) {
        if (this.videoId != videoId) {
            holder.stop()
            this.videoId = videoId
            cachedDetails = null
            cachedStream = null
            selectedVideo = null
            selectedAudio = null
            selectionInitialized = false
        }
        minimized = false
    }

    fun cache(videoId: String, details: VideoDetails, stream: VideoStream) {
        if (this.videoId != videoId) return
        cachedDetails = details
        cachedStream = stream
    }

    fun updateSelection(video: VideoVariant?, audio: AudioTrackOption?) {
        selectedVideo = video
        selectedAudio = audio
        selectionInitialized = true
    }

    fun minimize(): Boolean {
        if (!canMinimize) return false
        minimized = true
        return true
    }

    fun expand() {
        minimized = false
    }

    fun stop() {
        holderRef?.stop()
        videoId = null
        minimized = false
        cachedDetails = null
        cachedStream = null
        selectedVideo = null
        selectedAudio = null
        selectionInitialized = false
    }

    override fun onCleared() {
        holderRef?.release()
        holderRef = null
        super.onCleared()
    }
}
```

- [ ] **Step 4: Запустити тест і переконатися, що він проходить**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlaybackViewModelTest'
```

Expected: `BUILD SUCCESSFUL`, 6 tests.

- [ ] **Step 5: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/PlaybackViewModel.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlaybackViewModelTest.kt
git commit -m "feat(player): add activity-scoped playback session"
```

---

### Task 4: Перевести PlayerScreen і PlayerRoute на спільний holder

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt`

**Interfaces:**
- Consumes: `PlaybackViewModel` (Task 3).
- Produces:
  - `PlayerScreen(holder: PlayerHolder?, initialVideo, initialAudio, selectionInitialized, onSelectionChanged)`.
  - `PlayerRoute(playback: PlaybackViewModel, onVideoClick, onBack, viewModel)`.
  - `IOITubeApp` тримає `PlaybackViewModel` і передає його в route.

- [ ] **Step 1: Оновити PlayerScreenTest під спільний holder**

Додати imports:

```kotlin
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
```

Додати поля й teardown у клас:

```kotlin
private val holders = mutableListOf<PlayerHolder>()

@After
fun tearDown() {
    holders.forEach { it.release() }
}

private fun holder(): PlayerHolder =
    PlayerHolder(ApplicationProvider.getApplicationContext<Context>()).also { holders += it }

private fun successState() = PlayerUiState(
    video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
    stream = UiState.Success(
        VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
    ),
    comments = UiState.Success(emptyList()),
)
```

У цих тестах додати `holder = holder(),` одразу після `onRetry = ...`:

- `stream success renders player instead of unavailable message`
- `fullscreen renders only the player`
- `fullscreen button reports the toggle`
- `player stays pinned while the page scrolls`
- `recommendations open a video`

Test `video error replaces player with message and retry`, `shows title, meta line, comments and recommendations`, `offline cached video shows banner`, `stream error shows row with retry` не потребують holder: там stream ще Loading/Error.

Замінити тест `fullscreen toggle keeps the same player holder` на:

```kotlin
@Test
fun `disposing the screen does not release the shared holder`() {
    var show by mutableStateOf(true)
    val sharedHolder = holder()

    composeRule.setContent {
        IOITubeTheme {
            if (show) {
                PlayerScreen(
                    uiState = successState(),
                    onVideoClick = {},
                    onRetry = {},
                    holder = sharedHolder,
                )
            }
        }
    }

    composeRule.runOnIdle { show = false }
    composeRule.waitForIdle()

    assertThat(sharedHolder.isReleased).isFalse()
}
```

- [ ] **Step 2: Запустити тести й переконатися, що вони падають**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest'
```

Expected: FAIL — немає параметра `holder` у `PlayerScreen`.

- [ ] **Step 3: Змінити PlayerScreen**

У `PlayerScreen.kt` видалити **лише** import `android.content.Context` (він був потрібен `holderFactory`). `androidx.compose.ui.platform.LocalContext` **лишити** — він використовується в `PlayerRoute` у цьому ж файлі. Додати:

```kotlin
import com.ioi.youtube.domain.model.AudioTrackOption
```

Замінити сигнатуру `PlayerScreen` на:

```kotlin
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    holder: PlayerHolder? = null,
    initialVideo: VideoVariant? = null,
    initialAudio: AudioTrackOption? = null,
    selectionInitialized: Boolean = false,
    onSelectionChanged: (VideoVariant?, AudioTrackOption?) -> Unit = { _, _ -> },
) {
```

У гілці `is UiState.Success ->` є такий блок (він починається після `is UiState.Success -> {` і закінчується перед `val player:`):

```kotlin
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

// У повному екрані відео одразу заповнює краї; у портреті — вписується.
LaunchedEffect(isFullscreen) {
    scale = if (isFullscreen) VideoScale.FILL else VideoScale.FIT
}

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
```

Замінити його на:

```kotlin
val details = videoState.data
val stream = (uiState.stream as? UiState.Success)?.data
val videoOptions = remember(stream) { chooseVideoVariants(stream?.videoOnly.orEmpty()) }
val audioOptions = remember(stream) { chooseAudioTracks(stream?.audioTracks.orEmpty()) }
val fallbackVideo = remember(videoOptions) { defaultVideo(videoOptions) }
val fallbackAudio = remember(audioOptions) { defaultAudio(audioOptions) }
val startVideo = if (selectionInitialized) initialVideo else fallbackVideo
val startAudio = if (selectionInitialized) initialAudio else fallbackAudio
var selectedVideo by remember(stream) { mutableStateOf(startVideo) }
var selectedAudio by remember(stream) { mutableStateOf(startAudio) }
var scale by rememberSaveable(stream?.url) { mutableStateOf(VideoScale.FIT) }

// Перший показ: запам'ятати default-вибір у сесії, щоб розгортання його не скинуло.
LaunchedEffect(stream, selectionInitialized) {
    if (stream != null && !selectionInitialized) {
        onSelectionChanged(selectedVideo, selectedAudio)
    }
}

// У повному екрані відео одразу заповнює краї; у портреті — вписується.
LaunchedEffect(isFullscreen) {
    scale = if (isFullscreen) VideoScale.FILL else VideoScale.FIT
}

LaunchedEffect(holder, stream, selectedVideo, selectedAudio) {
    if (holder == null || stream == null) return@LaunchedEffect
    holder.update(
        stream = stream,
        video = selectedVideo,
        audio = selectedAudio,
        auto = selectedVideo == null && selectedAudio == null,
    )
}
```

У виклику `VideoPlayer` замінити `player = holder?.player` (уже так) і `error = holder?.error` (уже так); додати `holder` не потрібно. Перевірити, що `holder` більше ніде не створюється через `remember`/`holderFactory`.

У `VideoPlayer` callbacks замінити:

```kotlin
onSelectAuto = {
    selectedVideo = null
    selectedAudio = null
    onSelectionChanged(null, null)
},
onSelectVideo = {
    selectedVideo = it
    onSelectionChanged(it, selectedAudio)
},
onSelectAudio = { track ->
    selectedAudio = track
    if (selectedVideo == null) selectedVideo = defaultVideo(videoOptions)
    onSelectionChanged(selectedVideo, track)
},
```

- [ ] **Step 4: Змінити PlayerRoute**

У `PlayerScreen.kt` замінити `PlayerRoute` на:

```kotlin
@Composable
fun PlayerRoute(
    playback: PlaybackViewModel,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isFullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(viewModel.videoId) {
        playback.begin(viewModel.videoId)
    }

    val cachedDetails = playback.cachedDetails
    val cachedStream = playback.cachedStream
    LaunchedEffect(uiState.video, uiState.stream) {
        val details = (uiState.video as? UiState.Success)?.data ?: return@LaunchedEffect
        val stream = (uiState.stream as? UiState.Success)?.data ?: return@LaunchedEffect
        playback.cache(viewModel.videoId, details, stream)
    }

    val displayedState = remember(uiState, cachedDetails, cachedStream) {
        uiState.copy(
            video = if (uiState.video is UiState.Loading && cachedDetails != null) {
                UiState.Success(cachedDetails)
            } else {
                uiState.video
            },
            stream = if (uiState.stream is UiState.Loading && cachedStream != null) {
                UiState.Success(cachedStream)
            } else {
                uiState.stream
            },
        )
    }

    // Вихiд ставить портрет i лишає його: автоматично повертати SENSOR
    // не можна — на телефонi, який тримають боком, вiн одразу поверне
    // ландшафт, i користувач знову опиниться в повному екранi.
    // Вільний поворот повертається при наступному входi на екран вiдео
    // (LaunchedEffect у IOITubeApp).
    val toggleFullscreen: () -> Unit = {
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            playback.stop()
            onBack()
        }
    }

    LaunchedEffect(isFullscreen, activity) {
        val current = activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(current.window, current.window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        val current = activity
        onDispose {
            if (current != null) {
                WindowInsetsControllerCompat(current.window, current.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    PlayerScreen(
        uiState = displayedState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
        isFullscreen = isFullscreen,
        onToggleFullscreen = toggleFullscreen,
        holder = playback.holder,
        initialVideo = playback.selectedVideo,
        initialAudio = playback.selectedAudio,
        selectionInitialized = playback.selectionInitialized,
        onSelectionChanged = playback::updateSelection,
    )
}
```

Додати import:

```kotlin
import androidx.activity.compose.BackHandler
```

- [ ] **Step 5: Підключити PlaybackViewModel у IOITubeApp**

Додати imports:

```kotlin
import androidx.hilt.navigation.compose.hiltViewModel
import com.ioi.youtube.presentation.player.PlaybackViewModel
```

Після `val activity = remember(context) { context.findActivity() }` додати:

```kotlin
val playback: PlaybackViewModel = hiltViewModel()
```

Замінити `composable(Routes.PLAYER)` на:

```kotlin
composable(Routes.PLAYER) {
    PlayerRoute(
        playback = playback,
        onVideoClick = { videoId ->
            navController.navigate(Routes.player(videoId)) {
                popUpTo(Routes.PLAYER) { inclusive = true }
            }
        },
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 6: Запустити тести й переконатися, що вони проходять**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest' \
  --tests 'com.ioi.youtube.presentation.player.PlaybackViewModelTest' \
  --tests 'com.ioi.youtube.presentation.player.PlayerHolderTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt \
        app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt
git commit -m "refactor(player): share player holder across navigation"
```

---

### Task 5: Створити MiniPlayer composable

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/MiniPlayer.kt`
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/MiniPlayerTest.kt`

**Interfaces:**
- Consumes: `androidx.media3.common.Player`.
- Produces:
  - `MiniPlayer(player: Player, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)`.
  - test tag `mini-player`.
  - content descriptions «Пауза», «Відтворити», «Закрити міні-плеєр», «Розгорнути відео».

- [ ] **Step 1: Написати MiniPlayerTest**

Створити `app/src/test/java/com/ioi/youtube/presentation/player/MiniPlayerTest.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    private fun show(
        onExpand: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                MiniPlayer(
                    player = player,
                    onExpand = onExpand,
                    onClose = onClose,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `mini player is visible`() {
        show()

        composeRule.onNodeWithTag("mini-player").assertExists()
    }

    @Test
    fun `close button reports close`() {
        var closed = false
        show(onClose = { closed = true })

        composeRule.onNodeWithContentDescription("Закрити міні-плеєр").performClick()

        assertThat(closed).isTrue()
    }

    @Test
    fun `video tap reports expand`() {
        var expanded = false
        show(onExpand = { expanded = true })

        composeRule.onNodeWithContentDescription("Розгорнути відео").performClick()

        assertThat(expanded).isTrue()
    }

    @Test
    fun `play pause toggles play when ready`() {
        show()
        assertThat(player.playWhenReady).isFalse()

        composeRule.onNodeWithContentDescription("Відтворити").performClick()
        assertThat(player.playWhenReady).isTrue()

        composeRule.onNodeWithContentDescription("Пауза").performClick()
        assertThat(player.playWhenReady).isFalse()
    }
}
```

- [ ] **Step 2: Запустити тест і переконатися, що він падає**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.MiniPlayerTest'
```

Expected: FAIL — unresolved reference `MiniPlayer`.

- [ ] **Step 3: Створити MiniPlayer**

Створити `app/src/main/java/com/ioi/youtube/presentation/player/MiniPlayer.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Маленький плеєр над нижньою навігацією. Не володіє [Player] і не звільняє його.
 */
@Composable
fun MiniPlayer(
    player: Player,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playing by remember(player) { mutableStateOf(player.playWhenReady) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(176.dp)
                .aspectRatio(16f / 9f)
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .testTag("mini-player"),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                onRelease = { view -> view.player = null },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onExpand)
                    .semantics { contentDescription = "Розгорнути відео" },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        if (playing) player.pause() else player.play()
                    },
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Відтворити",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClose),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Закрити міні-плеєр",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Запустити тест і переконатися, що він проходить**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.MiniPlayerTest'
```

Expected: `BUILD SUCCESSFUL`, 4 tests.

- [ ] **Step 5: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/MiniPlayer.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/MiniPlayerTest.kt
git commit -m "feat(player): add floating mini player"
```

---

### Task 6: Підключити міні-плеєр, Back-згортання та зупинку в Shorts

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`

**Interfaces:**
- Consumes: `MiniPlayer` (Task 5), `PlaybackViewModel.minimize()/expand()/stop()` (Task 3).
- Produces:
  - Міні-плеєр на Home/Search.
  - Back у портреті згортає; fullscreen Back виходить із fullscreen.
  - Shorts зупиняє сесію.
  - Рекомендація замінює поточний player-route.

- [ ] **Step 1: Оновити BackHandler у PlayerRoute**

У `PlayerRoute` замінити:

```kotlin
BackHandler {
    if (isFullscreen) {
        toggleFullscreen()
    } else {
        playback.stop()
        onBack()
    }
}
```

на:

```kotlin
val minimizeAndBack: () -> Unit = {
    if (playback.minimize()) {
        onBack()
    } else {
        playback.stop()
        onBack()
    }
}

BackHandler {
    if (isFullscreen) {
        toggleFullscreen()
    } else {
        minimizeAndBack()
    }
}
```

- [ ] **Step 2: Оновити IOITubeApp**

Додати imports:

```kotlin
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.ioi.youtube.presentation.player.MiniPlayer
```

У `HomeRoute` і `SearchRoute` замінити `onVideoClick` на:

```kotlin
onVideoClick = { videoId ->
    playback.expand()
    navController.navigate(Routes.player(videoId))
},
```

У `PlayerRoute` замінити `onVideoClick` на:

```kotlin
onVideoClick = { videoId ->
    playback.expand()
    navController.navigate(Routes.player(videoId)) {
        popUpTo(Routes.PLAYER) { inclusive = true }
    }
},
```

Додати ефект зупинки в Shorts після `LaunchedEffect(currentRoute, activity)`:

```kotlin
LaunchedEffect(currentRoute) {
    if (currentRoute?.startsWith(Routes.SHORTS) == true) {
        playback.stop()
    }
}
```

Замінити тіло `Scaffold` content на:

```kotlin
) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onVideoClick = { videoId ->
                        playback.expand()
                        navController.navigate(Routes.player(videoId))
                    },
                    onShortClick = { videoId ->
                        navController.navigate(Routes.shorts(videoId))
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchRoute(
                    onVideoClick = { videoId ->
                        playback.expand()
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
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
            composable(
                route = Routes.PLAYER,
                exitTransition = { ExitTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                PlayerRoute(
                    playback = playback,
                    onVideoClick = { videoId ->
                        playback.expand()
                        navController.navigate(Routes.player(videoId)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        val showMiniPlayer = playback.minimized &&
            playback.canMinimize &&
            (currentRoute == Routes.HOME || currentRoute == Routes.SEARCH)

        if (showMiniPlayer) {
            val videoId = playback.videoId
            if (videoId != null) {
                MiniPlayer(
                    player = playback.holder.player,
                    onExpand = {
                        playback.expand()
                        navController.navigate(Routes.player(videoId))
                    },
                    onClose = playback::stop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Прогнати тести та зібрати debug**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlaybackViewModelTest' \
  --tests 'com.ioi.youtube.presentation.player.MiniPlayerTest' \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest'
source .agent-env.sh && ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt \
        app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt
git commit -m "feat(player): show mini player over home and search"
```

---

### Task 7: Додати жест протягування відео вниз

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerGestures.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerGesturesTest.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt`

**Interfaces:**
- Consumes: `onMinimize` callback from `PlayerRoute` (Task 6).
- Produces:
  - `MINIMIZE_DRAG_FRACTION = 0.30f`.
  - `shouldMinimize(dragPx: Float, heightPx: Float): Boolean`.
  - `VideoPlayer(onMinimize: (() -> Unit)? = null)`.
  - `PlayerScreen(onMinimize: (() -> Unit)? = null)`.

- [ ] **Step 1: Додати тести чистого порогу**

У `PlayerGesturesTest.kt` додати:

```kotlin
@Test
fun `minimize requires a drag past thirty percent`() {
    assertThat(shouldMinimize(299f, 1000f)).isFalse()
    assertThat(shouldMinimize(300f, 1000f)).isTrue()
    assertThat(shouldMinimize(500f, 1000f)).isTrue()
    assertThat(shouldMinimize(500f, 0f)).isFalse()
}
```

- [ ] **Step 2: Додати тести жесту в PlayerScreenTest**

Додати imports:

```kotlin
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
```

Додати:

```kotlin
@Test
fun `swiping the video down asks to minimize`() {
    var minimized = false

    composeRule.setContent {
        IOITubeTheme {
            PlayerScreen(
                uiState = successState(),
                onVideoClick = {},
                onRetry = {},
                holder = holder(),
                onMinimize = { minimized = true },
            )
        }
    }

    composeRule.onNodeWithTag("player-container").performTouchInput {
        swipeDown(startY = height * 0.1f, endY = height * 0.9f, durationMillis = 300)
    }
    composeRule.waitForIdle()

    assertThat(minimized).isTrue()
}

@Test
fun `short swipe does not minimize`() {
    var minimized = false

    composeRule.setContent {
        IOITubeTheme {
            PlayerScreen(
                uiState = successState(),
                onVideoClick = {},
                onRetry = {},
                holder = holder(),
                onMinimize = { minimized = true },
            )
        }
    }

    composeRule.onNodeWithTag("player-container").performTouchInput {
        swipeDown(startY = height * 0.1f, endY = height * 0.25f, durationMillis = 300)
    }
    composeRule.waitForIdle()

    assertThat(minimized).isFalse()
}

@Test
fun `minimize gesture does not block player controls`() {
    composeRule.setContent {
        IOITubeTheme {
            PlayerScreen(
                uiState = PlayerUiState(
                    video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                    stream = UiState.Success(
                        VideoStream(
                            url = "https://example.com/v.mp4",
                            resolution = "720p",
                            format = "MPEG-4",
                            hlsUrl = "https://example.com/hls.m3u8",
                            videoOnly = listOf(
                                VideoVariant("https://example.com/v720.mp4", 720, 1_000_000),
                            ),
                        ),
                    ),
                    comments = UiState.Success(emptyList()),
                ),
                onVideoClick = {},
                onRetry = {},
                holder = holder(),
                onMinimize = {},
            )
        }
    }

    composeRule.onNodeWithContentDescription("Якість").performClick()
    composeRule.onNodeWithText("Авто (HLS)").assertExists()
}
```

- [ ] **Step 3: Запустити тести й переконатися, що вони падають**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerGesturesTest' \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest'
```

Expected: FAIL — unresolved `shouldMinimize` / `onMinimize`.

- [ ] **Step 4: Додати чистий поріг**

У `PlayerGestures.kt` додати:

```kotlin
/** Частка висоти відео, після якої відпускання згортає плеєр. */
internal const val MINIMIZE_DRAG_FRACTION = 0.30f

internal fun shouldMinimize(dragPx: Float, heightPx: Float): Boolean =
    heightPx > 0f && dragPx >= heightPx * MINIMIZE_DRAG_FRACTION
```

- [ ] **Step 5: Додати жест у VideoPlayer**

Додати imports:

```kotlin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
```

Додати параметр у сигнатуру `VideoPlayer` після `onToggleFullscreen`:

```kotlin
onMinimize: (() -> Unit)? = null,
```

Одразу після `val entryProgress = remember { Animatable(1f) }` додати:

```kotlin
val minimizeOffset = remember { Animatable(0f) }
val onMinimizeState = rememberUpdatedState(onMinimize)
val canMinimize = onMinimize != null
```

Замінити наявний `LaunchedEffect(isFullscreen)` (анімація входу) на:

```kotlin
LaunchedEffect(isFullscreen) {
    minimizeOffset.snapTo(0f)
    if (isFullscreen) {
        entryProgress.snapTo(0f)
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        )
    } else {
        entryProgress.snapTo(1f)
    }
}
```

Замінити початок `Box` (він зараз `modifier = modifier.then(if (isFullscreen) ... )`) на:

```kotlin
Box(
    modifier = modifier
        .then(
            if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
        )
        .zIndex(if (minimizeOffset.value > 0f) 1f else 0f)
        .graphicsLayer { translationY = minimizeOffset.value }
        .pointerInput(isFullscreen, canMinimize) {
            if (isFullscreen || !canMinimize) return@pointerInput
            val playerHeight = size.height.toFloat()
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                var tracking = false
                var lastDrag = 0f
                var canceled = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        canceled = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y

                    if (!tracking && dy > touchSlop && dy > abs(dx)) {
                        tracking = true
                    }
                    if (tracking) {
                        lastDrag = dy.coerceAtLeast(0f)
                        minimizeOffset.snapTo(lastDrag)
                        change.consume()
                    }
                    if (!change.pressed) break
                }

                if (tracking) {
                    if (!canceled && shouldMinimize(lastDrag, playerHeight)) {
                        onMinimizeState.value?.invoke()
                    } else {
                        minimizeOffset.animateTo(0f, tween(200))
                    }
                }
            }
        },
) {
```

- [ ] **Step 6: Прокинути onMinimize через PlayerScreen і PlayerRoute**

У сигнатуру `PlayerScreen` додати після `onSelectionChanged`:

```kotlin
onMinimize: (() -> Unit)? = null,
```

У виклику `VideoPlayer` всередині `PlayerScreen` додати:

```kotlin
onMinimize = onMinimize,
```

У `PlayerRoute` у виклику `PlayerScreen` додати:

```kotlin
onMinimize = minimizeAndBack,
```

- [ ] **Step 7: Запустити тести й переконатися, що вони проходять**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.PlayerGesturesTest' \
  --tests 'com.ioi.youtube.presentation.player.PlayerScreenTest' \
  --tests 'com.ioi.youtube.presentation.player.VideoPlayerTest' \
  --tests 'com.ioi.youtube.presentation.player.PinchScaleTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Закомітити**

```bash
git add app/src/main/java/com/ioi/youtube/presentation/player/PlayerGestures.kt \
        app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt \
        app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlayerGesturesTest.kt \
        app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt
git commit -m "feat(player): minimize video with a downward drag"
```

---

### Task 8: Версія, документація, release-збірка й ручна перевірка

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/manual-test-checklist.md`

**Interfaces:**
- Consumes: усі попередні задачі.
- Produces: `dist/IOITube-1.2.2-release.apk`, SHA-256, ручний чекліст; реліз на GitHub — окремим кроком після підтвердження.

- [ ] **Step 1: Підняти версію**

У `app/build.gradle.kts` замінити:

```kotlin
versionCode = 5
versionName = "1.2.1"
```

на:

```kotlin
versionCode = 6
versionName = "1.2.2"
```

- [ ] **Step 2: Оновити ручний чекліст**

У `docs/manual-test-checklist.md` замінити рядок сценарію 5:

Старий рядок:

```markdown
| 5 | Тапнути рекомендацію | Відкрилося інше відео, «Назад» повертає на попереднє |
```

Новий рядок:

```markdown
| 5 | Тапнути рекомендацію | Відкрилося інше відео; «Назад» повертає на Home/Search (рекомендація замінює поточне відео) |
```

Додати після таблиці основного чекліста новий розділ:

```markdown
## 2a. Міні-плеєр (1.2.2)

| # | Сценарій | Очікувано |
|---|---|---|
| 23 | На відео в портреті потягнути відео вниз | Відео згортається, звук не переривається, знизу справа з'являється міні-плеєр, видно Home/Search |
| 24 | Натиснути Back на відео в портреті | Те саме: міні-плеєр, звук грає |
| 25 | Натиснути Back у fullscreen | Спершу вихід із fullscreen, відео не зникає |
| 26 | Тап по міні-плеєру | Відео розгортається з тієї ж позиції, міні-плеєр зникає |
| 27 | Пауза/плей у міні-плеєрі | Відтворення зупиняється/продовжується |
| 28 | X у міні-плеєрі | Звук зупиняється, картка зникає |
| 29 | Перейти в Shorts із міні-плеєром | Міні-плеєр зникає, звук довгого відео зупиняється |
| 30 | Відкрити нове відео при міні-плеєрі | Старе зупиняється, відкривається нове |
| 31 | Змінити якість/мову, згорнути й розгорнути | Вибір зберігається |
| 32 | Тап по рекомендації у відео | Нове відео замінює поточне; Back повертає на Home/Search |
```

- [ ] **Step 3: Прогнати всі player-тести**

Run:

```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests 'com.ioi.youtube.presentation.player.*'
```

Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 4: Зібрати release і перевірити lint**

Run:

```bash
source .agent-env.sh && ./gradlew :app:assembleRelease
source .agent-env.sh && ./gradlew :app:lintVitalRelease
```

Expected: обидві команди `BUILD SUCCESSFUL`.

- [ ] **Step 5: Скопіювати APK і порахувати SHA-256**

Run:

```bash
cp app/build/outputs/apk/release/app-release.apk dist/IOITube-1.2.2-release.apk
sha256sum dist/IOITube-1.2.2-release.apk
```

Expected: файл існує, SHA-256 виводиться одним рядком.

- [ ] **Step 6: Надіслати APK на TCL 306**

Run:

```bash
kdeconnect-cli -d d157e507af444955b36c76c3c59e8900 --share dist/IOITube-1.2.2-release.apk
```

Expected: KDE Connect підтверджує надсилання. Якщо пристрій недоступний — повідомити користувача й зупинитися.

- [ ] **Step 7: Закомітити версію й документацію**

```bash
git add app/build.gradle.kts docs/manual-test-checklist.md
git commit -m "chore(release): prepare 1.2.2"
```

- [ ] **Step 8: Передати користувачу звіт і зупинитися до підтвердження**

Повідомити:

- шлях до APK;
- SHA-256;
- що версія 1.2.2 (6);
- які сценарії перевірити з розділу `2a` чекліста.

Не створювати GitHub-реліз, не ставити тег і не публікувати APK, доки користувач не підтвердить успішний тест на телефоні.
