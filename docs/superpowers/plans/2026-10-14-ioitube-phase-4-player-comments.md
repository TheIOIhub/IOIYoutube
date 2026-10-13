# IOITube — Фаза 4: Плеєр, деталі, коментарі

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`

**Goal фази:** сторінка відео (спека §5.3): плеєр зверху, назва/канал/опис/перегляди, коментарі знизу, рекомендації знизу. Помилки: відео недоступне → повідомлення замість плеєра; помилка потоку → рядок із «Спробувати знову»; офлайн → банер кешу.

**Architecture:** `PlayerViewModel` з`combine` трьох потоків (details, stream, comments) у `PlayerUiState`; `VideoPlayer` — Stateless Composable з ExoPlayer, який паузиться на `ON_STOP` і звільняється в `onDispose`.

**Tech Stack:** Media3 ExoPlayer 1.5.1, Compose, Hilt, Coil, Turbine, Robolectric.

---

### Task 4.1: PlayerViewModel + PlayerUiState

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerViewModel.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: `GetVideoDetailsUseCase` (Task 1.12), `GetVideoStreamUseCase` (Task 1.14), `GetCommentsUseCase` (Task 1.13), `FakeVideoRepository`/`FakeCommentRepository` (Task 1.9), `Routes.ARG_VIDEO_ID` (Task 1.4), `TestData` (Task 1.9).
- Produces: `data class PlayerUiState(video: UiState<VideoDetails> = UiState.Loading, stream: UiState<VideoStream> = UiState.Loading, comments: UiState<List<Comment>> = UiState.Loading)`; `@HiltViewModel class PlayerViewModel @Inject constructor(savedStateHandle: SavedStateHandle, getVideoDetails, getVideoStream, getComments)` з `val videoId: String`, `val uiState: StateFlow<PlayerUiState>`, `fun retry()`.

- [ ] **Step 1: Напиши падаючий тест `PlayerViewModelTest.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetCommentsUseCase
import com.ioi.youtube.domain.usecase.GetVideoDetailsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.testutil.FakeCommentRepository
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val videoRepository = FakeVideoRepository()
    private val commentRepository = FakeCommentRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(videoId: String = "dQw4w9WgXcQ") = PlayerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("videoId" to videoId)),
        getVideoDetails = GetVideoDetailsUseCase(videoRepository),
        getVideoStream = GetVideoStreamUseCase(videoRepository),
        getComments = GetCommentsUseCase(commentRepository),
    )

    @Test
    fun `initial state has all three sections loading`() {
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value).isEqualTo(PlayerUiState())
        assertThat(viewModel.videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `combines details, stream and comments`() = runTest(testDispatcher) {
        videoRepository.setDetails("dQw4w9WgXcQ", UiState.Success(TestData.details("dQw4w9WgXcQ")))
        videoRepository.setStream("dQw4w9WgXcQ", UiState.Success(VideoStream("https://v/1", "720p", "MPEG-4")))
        commentRepository.setComments(UiState.Success(TestData.comments()))

        createViewModel().uiState.test {
            val state = awaitItem()
            assertThat((state.video as UiState.Success).data.title).isEqualTo("Video dQw4w9WgXcQ")
            assertThat((state.stream as UiState.Success).data.url).isEqualTo("https://v/1")
            assertThat((state.comments as UiState.Success).data).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `comments error does not affect details and stream`() = runTest(testDispatcher) {
        videoRepository.setDetails("dQw4w9WgXcQ", UiState.Success(TestData.details("dQw4w9WgXcQ")))
        videoRepository.setStream("dQw4w9WgXcQ", UiState.Error("Не вдалося отримати відеопотік"))
        commentRepository.setComments(UiState.Error("Не вдалося завантажити коментарі"))

        createViewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.video).isInstanceOf(UiState.Success::class.java)
            assertThat((state.stream as UiState.Error).message).isEqualTo("Не вдалося отримати відеопотік")
            assertThat((state.comments as UiState.Error).message).isEqualTo("Не вдалося завантажити коментарі")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry restarts all three flows`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        val job = kotlinx.coroutines.launch { viewModel.uiState.collect {} }
        kotlinx.coroutines.test.advanceUntilIdle()
        assertThat(videoRepository.detailsCalls).isEqualTo(1)
        assertThat(commentRepository.commentsCalls).isEqualTo(1)

        viewModel.retry()
        kotlinx.coroutines.test.advanceUntilIdle()

        assertThat(videoRepository.detailsCalls).isEqualTo(2)
        assertThat(commentRepository.commentsCalls).isEqualTo(2)
        job.cancel()
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerViewModelTest"`

Expected: FAIL (`Unresolved reference: PlayerViewModel`).

- [ ] **Step 3: Створи `PlayerViewModel.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetCommentsUseCase
import com.ioi.youtube.domain.usecase.GetVideoDetailsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PlayerUiState(
    val video: UiState<VideoDetails> = UiState.Loading,
    val stream: UiState<VideoStream> = UiState.Loading,
    val comments: UiState<List<Comment>> = UiState.Loading,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoDetails: GetVideoDetailsUseCase,
    private val getVideoStream: GetVideoStreamUseCase,
    private val getComments: GetCommentsUseCase,
) : ViewModel() {

    val videoId: String = savedStateHandle.get<String>(Routes.ARG_VIDEO_ID).orEmpty()

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<PlayerUiState> = refreshTrigger
        .flatMapLatest {
            combine(
                getVideoDetails(videoId),
                getVideoStream(videoId),
                getComments(videoId),
            ) { video, stream, comments ->
                PlayerUiState(video = video, stream = stream, comments = comments)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState(),
        )

    fun retry() {
        refreshTrigger.update { it + 1 }
    }
}
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerViewModelTest"`

Expected: PASS (4 тести).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add player view model combining details stream comments"
```

---

### Task 4.2: VideoPlayer (ExoPlayer)

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt`

**Interfaces:**
- Consumes: Media3 (підключений у Task 1.2).
- Produces: `VideoPlayer(videoUrl: String?, modifier: Modifier = Modifier, unavailableText: String = "Відео недоступне")`. `null`/пустий url → сірий блок із текстом, замість плеєра (спека §7: «Video unavailable → show message instead of player»).

- [ ] **Step 1: Створи `VideoPlayer.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(
    videoUrl: String?,
    modifier: Modifier = Modifier,
    unavailableText: String = "Відео недоступне",
) {
    if (videoUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = unavailableText, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
    )
}
```

- [ ] **Step 2: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коміт**

```bash
git add -A
git commit -m "feat: add media3 video player composable"
```

---

### Task 4.3: PlayerRoute + PlayerScreen (плеєр + метадані + опис)

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Delete: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreenPlaceholder.kt`

**Interfaces:**
- Consumes: `PlayerViewModel`/`PlayerUiState` (Task 4.1), `VideoPlayer` (Task 4.2), компоненти (Task 3.1).
- Produces: `PlayerRoute(onVideoClick: (String) -> Unit, viewModel: PlayerViewModel = hiltViewModel())`; `PlayerScreen(uiState: PlayerUiState, onVideoClick: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Створи `PlayerScreen.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner

@Composable
fun PlayerRoute(
    onVideoClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
    )
}

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "player") {
                    VideoPlayer(
                        videoUrl = (uiState.stream as? UiState.Success)?.data?.url,
                    )
                }

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

                item(key = "recommendations") {
                    RecommendationsSection(
                        videos = details.recommendedVideos,
                        onVideoClick = onVideoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoMetaSection(
    title: String,
    uploaderName: String,
    metaLine: String,
    description: String,
    fromCache: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(12.dp)) {
        if (fromCache) {
            OfflineBanner()
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = metaLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = uploaderName, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = description.ifBlank { "Без опису" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun StreamErrorRow(
    state: UiState<VideoStream>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is UiState.Error) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Text("Спробувати знову")
        }
    }
}

private fun buildMetaLine(details: VideoDetails): String = listOfNotNull(
    Formatters.formatViews(details.viewCount),
    Formatters.formatCount(details.likeCount).let { "$it лайкiв" },
    details.uploadDate,
).joinToString(" · ")
```

- [ ] **Step 2: Видали placeholder**

```bash
rm app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreenPlaceholder.kt
```

- [ ] **Step 3: Онови `IOITubeApp.kt` — пiдключи `PlayerRoute`**

Заміни блок плеєра в `composable(Routes.PLAYER) { ... }`:

```kotlin
            composable(Routes.PLAYER) {
                PlayerRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
```

і додай iмпорт:

```kotlin
import com.ioi.youtube.presentation.player.PlayerRoute
```

(Рядок `import com.ioi.youtube.presentation.player.PlayerScreenPlaceholder` видали.)

- [ ] **Step 4: Перевір збірку — очiкуємо помилку про CommentsSection**

Run: `./gradlew :app:compileDebugKotlin`

Expected: FAIL з `Unresolved reference: CommentsSection` — це очiкувано, Task 4.4 його створює. Якщо iнша помилка — виправ перед продовженням.

- [ ] **Step 5: Коміт (тимчасово з возможливим чернетковим станом не комiтимо — виконай одразу Task 4.4)**

---

### Task 4.4: Секцiя коментарiв

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/CommentsSection.kt`

**Interfaces:**
- Consumes: `Comment` (Task 1.6), `Formatters.formatCount` (Task 1.6), `UiState`.
- Produces: `CommentsSection(state: UiState<List<Comment>>, onRetry: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Створи `CommentsSection.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.util.Formatters

@Composable
fun CommentsSection(
    state: UiState<List<Comment>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(12.dp)) {
        Text(text = "Коментарі", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        when (state) {
            is UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Завантаження…", style = MaterialTheme.typography.bodySmall)
            }

            is UiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text("Спробувати знову")
                }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        text = "Коментарів немає",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.data.forEach { comment ->
                        CommentItem(comment = comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = comment.authorName, style = MaterialTheme.typography.labelLarge)
                if (comment.pinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Закріплено",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Formatters.formatCount(comment.likeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.heartedByUploader) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "♥ автором",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: FAIL з `Unresolved reference: RecommendationsSection` — Task 4.5 створює його. Iнших помилок бути не повинно.

- [ ] **Step 3: Коміт чернетки разом із Task 4.5 (не комiти тут)**

---

### Task 4.5: Секцiя рекомендацiй

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/RecommendationsSection.kt`

**Interfaces:**
- Consumes: `VideoSummary` (Task 1.6), Coil.
- Produces: `RecommendationsSection(videos: List<VideoSummary>, onVideoClick: (String) -> Unit, modifier: Modifier = Modifier)` — горизонтальна стрiчка; порожнiй список → нiчого не рендерить.

- [ ] **Step 1: Створи `RecommendationsSection.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.VideoSummary

@Composable
fun RecommendationsSection(
    videos: List<VideoSummary>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Рекомендації",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyRow {
            items(items = videos, key = { it.id }) { video ->
                RecommendationCard(
                    video = video,
                    onClick = onVideoClick,
                    modifier = Modifier
                        .width(168.dp)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable { onClick(video.id) }) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = video.uploaderName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

- [ ] **Step 2: Перевір збірку**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` — екран відео збирається повнiстю.

- [ ] **Step 3: Коміт (Tasks 4.3–4.5)**

```bash
git add -A
git commit -m "feat: add player screen with comments and recommendations"
```

---

### Task 4.6: Перевiрка екрана вiдео

**Files:**
- Create: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt`

**Interfaces:**
- Consumes: усе з Tasks 4.1–4.5.
- Produces: Robolectric Compose-тести, якi фiксують поведiнку станів екрана вiдео (спека §7: відео недоступне → повiдомлення замість плеєра; помилка потоку → retry).

- [ ] **Step 1: Напиши тест `PlayerScreenTest.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `video error replaces player with message and retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(video = UiState.Error("Не вдалося завантажити відео")),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити відео").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `shows title, meta line, comments and recommendations`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Loading,
                        comments = UiState.Success(
                            listOf(
                                Comment("c1", "Alice", null, "First!", 150, "1 day ago", true, true),
                            ),
                        ),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Video dQw4w9WgXcQ").assertExists()
        composeRule.onNodeWithText("Test Channel").assertExists()
        composeRule.onNodeWithText("1.5M views", substring = true).assertExists()
        composeRule.onNodeWithText("Коментарі").assertExists()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Закріплено").assertExists()
        composeRule.onNodeWithText("Рекомендації").assertExists()
    }

    @Test
    fun `offline cached video shows banner`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ"), fromCache = true),
                        stream = UiState.Loading,
                        comments = UiState.Success(emptyList()),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `stream error shows row with retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Error("Не вдалося отримати відеопотік"),
                        comments = UiState.Loading,
                    ),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося отримати відеопотік").assertExists()
        // На екранi двi кнопки «Спробувати знову» (потiк + коментарi не в Error),
        // беремо першу — вона належитьStreamErrorRow.
        composeRule.onAllNodesWithText("Спробувати знову")[0].performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `stream success renders player with url`() {
        // Robolectric не грає вiдео, але перевiряємо, що VideoPlayer отримав url
        // — вiн рендерить AndroidView без креша.
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(videoUrl = "https://rr3---sn-abc.googlevideo.com/videoplayback?id=1")
            }
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun `null url shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(videoUrl = null)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }
}
```

- [ ] **Step 2: Додай iмпорт `onAllNodesWithText`**

У файлi тесту додай iмпорт (якщо компiлятор його вимагає):

```kotlin
import androidx.compose.ui.test.onAllNodesWithText
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.player.PlayerScreenTest"`

Expected: PASS (6 тестiв).

- [ ] **Step 4: Ручна перевiрка на пристрої**

1. Вiдкрий будь-яке вiдео з головної — плеєр стартує, звук i картинка йдуть, ситуативнi кнопки медiа3 працюють.
2. Згорни/розгорни app — на `ON_STOP` плеєр паузиться, на повторне вiдкриття продовжує.
3. Торкнись опису — розгортається повний текст.
4. Прокрути вниз — коментарi та рекомендацiї; тапни рекомендацiю — вiдкриється iнше вiдео, «Назад» повертає.
5. Увiмкни авiарежим i вiдкрий ранiше переглянуте вiдео — бачиш банер «Немає з'єднання — показано кеш», опис/дата з кешу; плеєр показує «Відео недоступне» або помилку потоку з кнопкою повтору.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "test: add player screen tests and manual verification"
```

---

## Review Focus фази 4

1. **Потік відсутнiй/мертвий** (`stream = Error`) → плеєр залишається «Відео недоступне», зверху рядок помилки з «Спробувати знову», без креша → Tasks 4.2, 4.3.
2. **Коментарі вимкненi** → `Success(emptyList())` → «Коментарів немає» → Task 4.4.
3. **Рекомендацiй немає** → секцiя не рендериться взагалi → Task 4.5.
4. **Кеш-деталi** → `fromCache` банер, рекомендацiй у кешi немає (порожнiй список) → Tasks 2.9, 4.3.
5. **Довгий опис** → 3 рядки + розгортання по тапу → Task 4.3.