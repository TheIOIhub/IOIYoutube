# IOITube — Фаза 3: Головна і пошук

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`

**Goal фази:** робочий головний екран із стрічкою відео та екран пошуку з сіткою результатів, обидва — зі станами Loading/Success/Error (кнопка «Спробувати знову»), переходом на сторінку відео та кеш-банером офлайн.

**Architecture:** Route (збирає стан із ViewModel + `collectAsStateWithLifecycle`) → Screen (stateless Composable) → components. ViewModel тримає `StateFlow<UiState<…>>` через `stateIn`; повторний запит — через `refreshTrigger` + `flatMapLatest`.

**Tech Stack:** Jetpack Compose (BOM 2024.12.01), Navigation Compose 2.8.5, Hilt `hiltViewModel()`, Coil 2.7.0, Turbine, Robolectric (Compose-тести).

---

### Task 3.1: Перевикористовувані компоненти

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/components/VideoCard.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/components/CommonUi.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/components/VideoGridCard.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/components/VideoCardTest.kt`

**Interfaces:**
- Consumes: `VideoSummary` + `Formatters` (Task 1.6), `IOITubeTheme` (Task 1.3), `TestData` (Task 1.9).
- Produces: `VideoCard(video: VideoSummary, onClick: (String) -> Unit, modifier: Modifier = Modifier)`; `VideoGridCard(video: VideoSummary, onClick: (String) -> Unit, modifier: Modifier = Modifier)`; `LoadingBox(modifier)`, `ErrorBox(message: String, onRetry: () -> Unit, modifier)`, `EmptyBox(text: String, modifier)`, `OfflineBanner(modifier)` (текст «Немає з'єднання — показано кеш»).

- [ ] **Step 1: Створи `VideoCard.kt`**

```kotlin
package com.ioi.youtube.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters

@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(video.id) },
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.uploaderName} · ${Formatters.formatViews(video.viewCount)} · ${Formatters.formatDuration(video.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!video.uploadDate.isNullOrBlank()) {
                Text(
                    text = video.uploadDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Створи `CommonUi.kt`**

```kotlin
package com.ioi.youtube.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Спробувати знову")
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    text: String = "Немає з'єднання — показано кеш",
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(8.dp),
        )
    }
}
```

- [ ] **Step 3: Створи `VideoGridCard.kt`**

```kotlin
package com.ioi.youtube.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Щільна картка для сітки результатiв пошуку (спека §5.2). */
@Composable
fun VideoGridCard(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(video.id) }
            .padding(8.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small),
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
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

- [ ] **Step 4: Напиши тест `VideoCardTest.kt`**

```kotlin
package com.ioi.youtube.presentation.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `video card renders meta line and handles click`() {
        val video = TestData.video(id = "dQw4w9WgXcQ", title = "Never Gonna Give You Up")
        var clickedId: String? = null

        composeRule.setContent {
            IOITubeTheme {
                VideoCard(video = video, onClick = { clickedId = it })
            }
        }

        composeRule.onNodeWithText("Never Gonna Give You Up").assertExists()
        composeRule.onNodeWithText(
            "Test Channel · 1.5M views · 3:32",
        ).assertExists()
        composeRule.onNodeWithText("Never Gonna Give You Up").performClick()

        assertThat(clickedId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `offline banner renders its message`() {
        composeRule.setContent {
            IOITubeTheme { OfflineBanner() }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error box renders message and retry button`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                ErrorBox(message = "Не вдалося завантажити стрічку", onRetry = { retried = true })
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }
}
```

- [ ] **Step 5: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.components.VideoCardTest"`

Expected: PASS (4 тести). Robolectric виконує Compose у LEGACY-режимi — картинки не завантажуються, тексти працють.

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add video card, grid card and common ui components"
```

---

### Task 3.2: HomeViewModel

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `GetHomeFeedUseCase` (Task 1.10), `FakeVideoRepository` (Task 1.9), `TestData` (Task 1.9).
- Produces: `@HiltViewModel class HomeViewModel @Inject constructor(getHomeFeed: GetHomeFeedUseCase)` з `val uiState: StateFlow<UiState<List<VideoSummary>>>` i `fun retry()`.

- [ ] **Step 1: Створи `HomeViewModel.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.usecase.GetHomeFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    getHomeFeed: GetHomeFeedUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<UiState<List<VideoSummary>>> = refreshTrigger
        .flatMapLatest { getHomeFeed() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    fun retry() {
        refreshTrigger.update { it + 1 }
    }
}
```

- [ ] **Step 2: Напиши тест `HomeViewModelTest.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.GetHomeFeedUseCase
import com.ioi.youtube.testutil.FakeVideoRepository
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
    private val repository = FakeVideoRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(GetHomeFeedUseCase(repository))

    @Test
    fun `starts with loading then emits success`() = runTest(testDispatcher) {
        val videos = listOf(TestData.video(), TestData.video("abcdefghijk"))
        repository.setFeed(UiState.Success(videos))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            assertThat(awaitItem()).isEqualTo(UiState.Success(videos))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits error state from repository`() = runTest(testDispatcher) {
        repository.setFeed(UiState.Error("Не вдалося завантажити стрічку"))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            assertThat(awaitItem()).isEqualTo(UiState.Error("Не вдалося завантажити стрічку"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-subscribes to the use case`() = runTest(testDispatcher) {
        repository.setFeed(UiState.Success(listOf(TestData.video())))
        val viewModel = createViewModel()

        val job = kotlinx.coroutines.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(repository.feedCalls).isEqualTo(1)

        viewModel.retry()
        advanceUntilIdle()

        assertThat(repository.feedCalls).isEqualTo(2)
        job.cancel()
    }

    @Test
    fun `loading initial value is exposed before subscription`() {
        // Ще до старту колекції stateIn вiддає initialValue.
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value).isEqualTo(UiState.Loading)
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.home.HomeViewModelTest"`

Expected: PASS (5 тестiв).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add home view model with tests"
```

---

### Task 3.3: HomeRoute + HomeScreen

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreen.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/home/HomeScreenTest.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreenPlaceholder.kt` — залишити файл, але вiн бiльше не використовується пiсля Task 3.6 (видалять там).

**Interfaces:**
- Consumes: `HomeViewModel` (Task 3.2), компоненти (Task 3.1).
- Produces: `HomeRoute(onVideoClick: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel())`; `HomeScreen(uiState: UiState<List<VideoSummary>>, onVideoClick: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Створи `HomeScreen.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoCard

@Composable
fun HomeRoute(
    onVideoClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
    )
}

@Composable
fun HomeScreen(
    uiState: UiState<List<VideoSummary>>,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = uiState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            if (uiState.data.isEmpty()) {
                EmptyBox(text = "Тут поки що порожньо", modifier = modifier)
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    if (uiState.fromCache) {
                        item(key = "offline-banner") { OfflineBanner() }
                    }
                    items(items = uiState.data, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = onVideoClick)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Напиши тест `HomeScreenTest.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
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

    @Test
    fun `success state shows video cards and propagates clicks`() {
        val videos = listOf(TestData.video(id = "aaaaaaaaaaa", title = "First video"))
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Success(videos),
                    onVideoClick = { clicked = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("First video").assertExists()
        composeRule.onNodeWithText("First video").performClick()

        assertThat(clicked).isEqualTo("aaaaaaaaaaa")
    }

    @Test
    fun `cached success state shows offline banner`() {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Success(
                        data = listOf(TestData.video(title = "Cached video")),
                        fromCache = true,
                    ),
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Cached video").assertExists()
        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error state shows message and retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(
                    uiState = UiState.Error("Не вдалося завантажити стрічку"),
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося завантажити стрічку").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `loading state shows progress indicator without crash`() {
        composeRule.setContent {
            IOITubeTheme {
                HomeScreen(uiState = UiState.Loading, onVideoClick = {}, onRetry = {})
            }
        }

        composeRule.onRoot().assertExists()
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.home.HomeScreenTest"`

Expected: PASS (4 тести).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add home route and screen with tests"
```

---

### Task 3.4: SearchViewModel (debounce)

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/search/SearchViewModel.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `SearchVideosUseCase` (Task 1.11), `FakeSearchRepository` (Task 1.9), `SearchResults` (Task 1.6).
- Produces: `@HiltViewModel class SearchViewModel @Inject constructor(searchVideos: SearchVideosUseCase)` з `val uiState: StateFlow<UiState<SearchResults>>`, `fun onQueryChange(query: String)`, `fun retry()`.

- [ ] **Step 1: Створи `SearchViewModel.kt`**

```kotlin
package com.ioi.youtube.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.SearchVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    searchVideos: SearchVideosUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<UiState<SearchResults>> = refreshTrigger
        .flatMapLatest {
            query
                .debounce(300)
                .flatMapLatest { current ->
                    if (current.isBlank()) {
                        flowOf(UiState.Success(SearchResults(current, emptyList())))
                    } else {
                        searchVideos(current)
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery.trim()
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
    }
}
```

- [ ] **Step 2: Напиши тест `SearchViewModelTest.kt`**

```kotlin
package com.ioi.youtube.presentation.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.usecase.SearchVideosUseCase
import com.ioi.youtube.testutil.FakeSearchRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeSearchRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(SearchVideosUseCase(repository))

    @Test
    fun `initial state is loading`() {
        assertThat(createViewModel().uiState.value).isEqualTo(UiState.Loading)
    }

    @Test
    fun `blank query returns empty results without network call`() = runTest(testDispatcher) {
        repository.setResults(UiState.Success(SearchResults("x", listOf(TestData.video()))))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            advanceTimeBy(400)

            val state = awaitItem() as UiState.Success
            assertThat(state.data.videos).isEmpty()
            assertThat(repository.searchCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query is debounced and searched once`() = runTest(testDispatcher) {
        val video = TestData.video(title = "Kotlin tutorial")
        repository.setResults(UiState.Success(SearchResults("kotlin", listOf(video))))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            viewModel.onQueryChange("ko")
            advanceTimeBy(100)
            viewModel.onQueryChange("kotlin")
            advanceTimeBy(100)
            expectNoEvents() // debounce ще тримає запит

            advanceTimeBy(300)
            val state = awaitItem() as UiState.Success
            assertThat(state.data.videos).hasSize(1)
            assertThat(repository.searchCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-runs last query`() = runTest(testDispatcher) {
        repository.setResults(UiState.Success(SearchResults("android", emptyList())))
        val viewModel = createViewModel()

        val job = kotlinx.coroutines.launch { viewModel.uiState.collect {} }
        advanceTimeBy(500)
        viewModel.onQueryChange("android")
        advanceTimeBy(500)
        assertThat(repository.searchCalls).isEqualTo(1)

        viewModel.retry()
        advanceTimeBy(500)

        assertThat(repository.searchCalls).isEqualTo(2)
        job.cancel()
    }

    @Test
    fun `error state is forwarded`() = runTest(testDispatcher) {
        repository.setResults(UiState.Error("Не вдалося виконати пошук"))

        createViewModel().uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            advanceTimeBy(400)
            assertThat(awaitItem()).isEqualTo(UiState.Error("Не вдалося виконати пошук"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.search.SearchViewModelTest"`

Expected: PASS (6 тестiв). Покриває Review Focus №2 (порожній запит).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add search view model with debounce and tests"
```

---

### Task 3.5: SearchRoute + SearchScreen

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/search/SearchScreen.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/search/SearchScreenTest.kt`

**Interfaces:**
- Consumes: `SearchViewModel` (Task 3.4), `VideoGridCard` (Task 3.1).
- Produces: `SearchRoute(onVideoClick: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel())`; `SearchScreen(uiState, onQueryChange, onVideoClick, onRetry, modifier)`.

- [ ] **Step 1: Створи `SearchScreen.kt`**

```kotlin
package com.ioi.youtube.presentation.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.components.OfflineBanner
import com.ioi.youtube.presentation.components.VideoGridCard

@Composable
fun SearchRoute(
    onVideoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
    )
}

@Composable
fun SearchScreen(
    uiState: UiState<SearchResults>,
    onQueryChange: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Пошук відео") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        when (uiState) {
            is UiState.Loading -> LoadingBox()

            is UiState.Error -> ErrorBox(message = uiState.message, onRetry = onRetry)

            is UiState.Success -> {
                if (uiState.data.videos.isEmpty()) {
                    EmptyBox(text = "Нічого не знайдено")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (uiState.fromCache) {
                            item(key = "offline-banner") { OfflineBanner() }
                        }
                        items(items = uiState.data.videos, key = { it.id }) { video ->
                            VideoGridCard(video = video, onClick = onVideoClick)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Напиши тест `SearchScreenTest.kt`**

```kotlin
package com.ioi.youtube.presentation.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.presentation.theme.IOITubeTheme
import com.ioi.youtube.testutil.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `query typing calls onQueryChange`() {
        var query: String? = null

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("", emptyList())),
                    onQueryChange = { query = it },
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Пошук відео").performTextInput("kot")
        assertThat(query).isEqualTo("kot")
    }

    @Test
    fun `results grid shows videos`() {
        val videos = listOf(TestData.video(id = "aaaaaaaaaaa", title = "Grid result"))

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("grid", videos)),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Grid result").assertExists()
    }

    @Test
    fun `cached search shows offline banner`() {
        val videos = listOf(TestData.video(title = "Cached result"))

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Success(SearchResults("cache", videos), fromCache = true),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Немає з'єднання — показано кеш").assertExists()
    }

    @Test
    fun `error shows retry`() {
        var retried = false

        composeRule.setContent {
            IOITubeTheme {
                SearchScreen(
                    uiState = UiState.Error("Не вдалося виконати пошук"),
                    onQueryChange = {},
                    onVideoClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Не вдалося виконати пошук").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()
        assertThat(retried).isTrue()
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.search.SearchScreenTest"`

Expected: PASS (4 тести).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add search route and grid screen with tests"
```

---

### Task 3.6: Пiдключення навiгацiї мiж екранами

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Delete: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreenPlaceholder.kt`
- Delete: `app/src/main/java/com/ioi/youtube/presentation/search/SearchScreenPlaceholder.kt`

**Interfaces:**
- Consumes: `HomeRoute`/`SearchRoute` (Tasks 3.3, 3.5), `PlayerScreenPlaceholder` (Task 1.4 — заміниться у Фазi 4).
- Produces: граф навiгацiї: `home` → `player/{videoId}`, `search` → `player/{videoId}`; нижня панель з пiдсвiткою активного табу.

- [ ] **Step 1: Заміни `IOITubeApp.kt`**

```kotlin
package com.ioi.youtube.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ioi.youtube.presentation.home.HomeRoute
import com.ioi.youtube.presentation.player.PlayerScreenPlaceholder
import com.ioi.youtube.presentation.search.SearchRoute

@Composable
fun IOITubeApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.SEARCH

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigateTopLevel(Routes.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SEARCH,
                        onClick = { navController.navigateTopLevel(Routes.SEARCH) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
            composable(Routes.PLAYER) { entry ->
                val videoId = entry.arguments?.getString(Routes.ARG_VIDEO_ID).orEmpty()
                PlayerScreenPlaceholder(videoId = videoId)
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 2: Видали placeholder-файли**

```bash
rm app/src/main/java/com/ioi/youtube/presentation/home/HomeScreenPlaceholder.kt
rm app/src/main/java/com/ioi/youtube/presentation/search/SearchScreenPlaceholder.kt
```

- [ ] **Step 3: Перевiр збірку**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Ручна перевiрка на пристрої/емуляторi (з iнтернетом)**

1. Запусти `app`. Головна стрiчка показує реальнi вiдео YouTube (прев'ю, назва, канал, перегляди, тривалість).
2. Тапни вiдео → екран `Player: <id>`.
3. Системна кнопка «Назад» повертає до стрiчки.
4. Таб `Search` → введи «kotlin» → через ~0.3 с з'являється сiтка результатiв.
5. Вимкни iнтернет, повернись на головну та потягни вниз (виконає Task 5.1) або перезапусти app — має з'явитися банер «Немає з'єднання — показано кеш» та кешованi вiдео.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: wire navigation between home, search and player routes"
```

---

## Review Focus фази 3

1. **Порожній пошуковий запит** — одразу `Success` з порожнім списком, нуль мережевих викликів → Task 3.4.
2. **Кеш + офлайн** — банер видно, `Error` не показується → Tasks 3.3, 3.5.
3. **Retry** — кнопка «Спробувати знову» перезапускає потік (лічильник `feedCalls/searchCalls`) → Tasks 3.2, 3.4.
4. **Довгий текст** — назва обрiзається в 2 рядки, мета — в 1, без креша → Task 3.1 (`maxLines` + `overflow`).
5. **Compose-тести під Robolectric** — AsyncImage не завантажує картинки, але тексти й клiки працють → Tasks 3.1, 3.3, 3.5.