# IOITube — Фаза 6: меню, плеєр, шортси (план реалізації)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека фази:** `docs/superpowers/specs/2026-10-18-phase-6-enhancements-design.md`

**Goal фази:** прибрати підписи в нижньому меню, згорнути коментарі за замовчуванням, дати вибір якості (HLS) і поворот/повний екран у плеєрі, додати вкладку Shorts із вертикальною стрічкою справжніх шортсів.

**Architecture:** `data` (NewPipe: HLS-URL + вкладки Shorts каналів) → `domain` (ShortsRepository + Use Cases) → `presentation` (Compose: меню, коментарі, плеєр, Shorts). Domain не знає про Android/NewPipe/Room/Media3. Room і його схема **не** змінюються.

**Tech Stack:** Kotlin 2.1.0 · Compose BOM 2024.12.01 · Media3 (ExoPlayer) 1.5.1 + `media3-exoplayer-hls` · Hilt 2.54 · Navigation Compose 2.8.5 · NewPipe Extractor 0.26.5 · Тести: JUnit4 + MockK + Turbine + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-18-phase-6-enhancements-design.md`

---

## Global Constraints

Кожна таска нижче неявно включає цей розділ.

- Мова UI — **українська**. Точні рядки: `Показати коментарі`, `Згорнути`, `Коментарі`, `Завантаження…`, `Коментарів немає`, `Закріплено`, `Спробувати знову`, `Тут поки що порожньо`, `Відео недоступне`, `Не вдалося завантажити шортси`, `Авто`, `На весь екран`, `Вийти з повного екрана`; `contentDescription` вкладок: `Головна`, `Пошук`, `Shorts`.
- Domain-шар НЕ залежить від Android, NewPipe, Room, Media3. Media3 живе лише в `presentation/player` і `presentation/shorts`.
- Мережеві виклики — на `Dispatchers.IO`; `CancellationException` перекидається далі; вузький `try` навколо самого мережевого виклику; помилка логується як `Log.e("IOITube", …)`.
- Стани: `UiState.Loading` → `Success` / `Error`. **Порожній список — це `Success(emptyList())`**, UI показує `Тут поки що порожньо`; `Error` — лише коли впала мережа/видобуток.
- Кеш: стрічка шортсів і коментарі **не** кешуються. Room не змінюється — жодних міграцій, жодних нових полів в entity.
- Числа/формати — `Locale.US` (як у `Formatters`).
- Версії не підвищуємо: Media3 1.5.1 (усі модулі однієї версії), minSdk 26, target/compileSdk 35, Java 17, `ksp.useKSP2=false`.
- Тести бізнес-логіки — JUnit4 + fakes/MockK без Robolectric; Robolectric — лише там, де в тасці прямо написано (`Log.e` в репозиторії, Compose-UI тести).
- Кожна таска = один коміт. Межі таски фіксовані: жодних «заодно поліпшу».

---

## Review Focus

Пʼять входів/станів, які спека має на увазі, але жодна таска не покриває «сама собою». Кожен закріплений тестом у тасці, що володіє кодом:

1. **HLS не запустився** (манифест протух/мережа) → автоматичний відкат на progressive-потік, меню якості зникає, відео продовжує грати, креша немає → Task 6.5.
2. **Відеодоріжок немає** (аудіо-only або `onTracksChanged` ще не прийшов) → меню якості не показується, `Авто` не «зависає» → Tasks 6.4, 6.5.
3. **Два канали віддали той самий шортс** → у стрічці він один (`distinctBy { id }`), а `loadMore` не дублює вже показане → Tasks 6.7, 6.9.
4. **Канал без вкладки Shorts; усі канали впали** → канал тихо пропускається, `Error` лише коли впали всі; порожній результат — не помилка → Task 6.7.
5. **Свайп назад-вперед у стрічці шортсів** → потік не вантажиться вдруге, плеєрів у памʼяті не більшає, порядок списку не змінюється → Task 6.9.

---

## Task index

| Task | Що робить | Спека |
|---|---|---|
| 6.1 | Меню без підписів (окремий компонент + тест) | §3 |
| 6.2 | Коментарі згорнуті за замовчуванням | §4 |
| 6.3 | HLS-джерело: `hlsUrl` крізь шари + `PlayerSource` | §5.1–5.2 |
| 6.4 | Чиста логіка вибору якості + застосування до плеєра | §5.4 |
| 6.5 | Плеєр: HLS, відкат, меню якості | §5.3–5.4 |
| 6.6 | Поворот екрана і повний екран | §6 |
| 6.7 | Стрічка шортсів у джерелі даних | §7.1 |
| 6.8 | `ShortsRepository` + Use Cases + DI | §7.2 |
| 6.9 | `ShortsViewModel` | §7.3 |
| 6.10 | Екран Shorts + навігація | §7.4 |
| 6.11 | Живий смоук, документація, фінальна верифікація, APK | §10, §12 |

Порядок суворий: Task N використовує те, що створили таски < N.

## Передумови (зробити один раз перед Task 6.1)

```bash
cd /home/the101hub/Projects/IOIYoutube
git checkout main            # main = 1518e15 (спека фази 6 уже в ній)
git checkout -b feat/ioitube-phase-6
source .agent-env.sh         # JAVA_HOME/ANDROID_HOME/PATH — потрібно ДЛЯ КОЖНОЇ gradle-команди
```

Гілка `feat/ioitube-phase-6` — робоча для всіх тасок; `main` не чіпаємо до Task 6.11 Step 7.
`dist/` у `.gitignore`, APK у неї не комітяться. Базова лiнiя перед стартом:
`:app:testDebugUnitTest` = 103 тести, 0 failures, 1 skip.

## File structure (що де лежить)

| Файл | Відповідальність |
|---|---|
| `presentation/navigation/IOITubeBottomBar.kt` (новий) | Нижнє меню без підписів; не знає про ViewModel/Hilt |
| `presentation/navigation/Orientation.kt` (новий) | Чисте правило: яка орієнтація для якого маршруту |
| `presentation/util/ActivityExt.kt` (новий) | `Context.findActivity()` — доступ до Activity з Compose |
| `presentation/player/PlayerSource.kt` (новий) | Що грати (HLS/progressive) + `MediaItem` для ExoPlayer |
| `presentation/player/QualityOptions.kt` (новий) | Вибір якості: чисті функції + Media3-адаптер |
| `presentation/player/VideoPlayer.kt` | Плеєр відео: HLS, відкат, меню якості, повний екран |
| `presentation/player/PlayerScreen.kt` | Сторінка відео: портрет (LazyColumn) і ландшафт (лише плеєр) |
| `presentation/player/CommentsSection.kt` | Згортання коментарів |
| `presentation/shorts/ShortsViewModel.kt` (новий) | Стрічка шортсів + ліниве завантаження потоків |
| `presentation/shorts/ShortsScreen.kt` (новий) | Вертикальний пейджер, накладення з назвою |
| `presentation/shorts/ShortVideoPlayer.kt` (новий) | Плеєр одного шортса (цикл, тап = пауза) |
| `data/remote/NewPipeRemoteDataSource.kt` | HLS-URL, вкладки Shorts каналів |
| `data/repository/ShortsRepositoryImpl.kt` (новий) | Стрічка шортсів без кешу |
| `domain/repository/ShortsRepository.kt`, `domain/usecase/GetShortsUseCase.kt`, `GetMoreShortsUseCase.kt` (нові) | Контракт і Use Cases |

---

### Task 6.1: Нижнє меню — лише іконки

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBar.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBarTest.kt`

**Interfaces:**
- Consumes: `Routes.HOME`, `Routes.SEARCH`.
- Produces: `@Composable fun IOITubeBottomBar(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Створи компонент меню з поточною поведінкою (з підписами) і підключи його**

`app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBar.kt`:

```kotlin
package com.ioi.youtube.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun IOITubeBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentRoute == Routes.HOME,
            onClick = { onNavigate(Routes.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Головна") },
            label = { Text("Головна") },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SEARCH,
            onClick = { onNavigate(Routes.SEARCH) },
            icon = { Icon(Icons.Filled.Search, contentDescription = "Пошук") },
            label = { Text("Пошук") },
        )
    }
}
```

У `IOITubeApp.kt` заміни тіло `bottomBar` на виклик компонента і прибери зайві імпорти
(`Icons`, `Home`, `Search`, `Icon`, `NavigationBar`, `NavigationBarItem`, `Text`):

```kotlin
        bottomBar = {
            if (showBottomBar) {
                IOITubeBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                )
            }
        },
```

- [ ] **Step 2: Перевір, що збірка не зламалась**

Run: `source .agent-env.sh && ./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Напиши тест, що падає (підписи не мають рендеритись)**

`app/src/test/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBarTest.kt`:

```kotlin
package com.ioi.youtube.presentation.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IOITubeBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `icons are shown without labels`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.HOME, onNavigate = {})
            }
        }

        composeRule.onNodeWithContentDescription("Головна").assertExists()
        composeRule.onNodeWithContentDescription("Пошук").assertExists()
        composeRule.onNodeWithText("Головна").assertDoesNotExist()
        composeRule.onNodeWithText("Пошук").assertDoesNotExist()
    }

    @Test
    fun `tapping an icon reports its route`() {
        var navigated: String? = null

        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.HOME, onNavigate = { navigated = it })
            }
        }

        composeRule.onNodeWithContentDescription("Пошук").performClick()

        assertThat(navigated).isEqualTo(Routes.SEARCH)
    }
}
```

- [ ] **Step 4: Запусти тест — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*IOITubeBottomBarTest*"`

Expected: FAIL — `icons are shown without labels` падає на `onNodeWithText("Головна")` (вузол існує, бо підпис ще є).

- [ ] **Step 5: Прибери підписи**

У `IOITubeBottomBar.kt` видали в обох `NavigationBarItem` рядок `label = { Text("…") }` і прибери імпорт `androidx.compose.material3.Text`.

- [ ] **Step 6: Запусти тест — має пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*IOITubeBottomBarTest*"`

Expected: `BUILD SUCCESSFUL`, 2 тести пройдено.

- [ ] **Step 7: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь (базова лінія 103 тести / 1 skip).

```bash
git add -A
git commit -m "feat: show bottom bar icons without labels"
```

---

### Task 6.2: Коментарі згорнуті за замовчуванням

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/CommentsSection.kt`
- Modify: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/CommentsSectionTest.kt` (новий)

**Interfaces:**
- Consumes: `UiState<List<Comment>>`, `Formatters` (без змін).
- Produces: публічний API `CommentsSection(state, onRetry, modifier)` **не змінюється** — змінюється лише вміст (стан згортання всередині).

- [ ] **Step 1: Напиши тест, що падає**

`app/src/test/java/com/ioi/youtube/presentation/player/CommentsSectionTest.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.Comment
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
class CommentsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: UiState<List<Comment>>, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            IOITubeTheme {
                CommentsSection(state = state, onRetry = onRetry)
            }
        }
    }

    @Test
    fun `collapsed by default shows count and hides comments`() {
        setContent(UiState.Success(TestData.comments()))

        composeRule.onNodeWithText("Коментарі (2)").assertExists()
        composeRule.onNodeWithText("Показати коментарі").assertExists()
        composeRule.onNodeWithText("First!").assertDoesNotExist()
        composeRule.onNodeWithText("Nice video").assertDoesNotExist()
    }

    @Test
    fun `expanding shows comments and collapsing hides them again`() {
        setContent(UiState.Success(TestData.comments()))

        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Показати коментарі").assertDoesNotExist()
        composeRule.onNodeWithText("Згорнути").assertExists()

        composeRule.onNodeWithText("Згорнути").performClick()
        composeRule.onNodeWithText("First!").assertDoesNotExist()
        composeRule.onNodeWithText("Показати коментарі").assertExists()
    }

    @Test
    fun `collapsed loading does not spin and expanded error offers retry`() {
        var retried = false
        setContent(UiState.Error("Не вдалося завантажити коментарі"), onRetry = { retried = true })

        composeRule.onNodeWithText("Коментарі").assertExists()
        composeRule.onNodeWithText("Спробувати знову").assertDoesNotExist()

        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("Не вдалося завантажити коментарі").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `loading spinner appears only after expanding`() {
        setContent(UiState.Loading)

        composeRule.onNodeWithText("Завантаження…").assertDoesNotExist()

        composeRule.onNodeWithText("Показати коментарі").performClick()

        composeRule.onNodeWithText("Завантаження…").assertExists()
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*CommentsSectionTest*"`

Expected: FAIL — коментарі зараз показуються одразу (`First!` існує, кнопки `Показати коментарі` немає).

- [ ] **Step 3: Реалізуй згортання**

`CommentsSection.kt` — повністю заміни функцію `CommentsSection` (решта файлу без змін):

```kotlin
@Composable
fun CommentsSection(
    state: UiState<List<Comment>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = when (state) {
        is UiState.Success -> "Коментарі (${state.data.size})"
        else -> "Коментарі"
    }

    Column(modifier = modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Згорнути" else "Показати коментарі")
            }
        }

        if (!expanded) return

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
```

Додай імпорти (решта наявних лишаються):

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
```

> `return` тут — звичайний `return` з функції `CommentsSection` (код після нього не композується при згорнутому стані).

- [ ] **Step 4: Запусти тест — має пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*CommentsSectionTest*"`

Expected: `BUILD SUCCESSFUL`, 4 тести пройдено.

- [ ] **Step 5: Онови `PlayerScreenTest`, який тепер показує згорнутий блок**

У тесті `shows title, meta line, comments and recommendations` заміни рядки 73–75:

```kotlin
        composeRule.onNodeWithText("Коментарі").assertExists()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Закріплено").assertExists()
```

на:

```kotlin
        composeRule.onNodeWithText("Коментарі (1)").assertExists()
        composeRule.onNodeWithText("Показати коментарі").performClick()
        composeRule.onNodeWithText("First!").assertExists()
        composeRule.onNodeWithText("Закріплено").assertExists()
```

Також у тесті `stream error shows row with retry` онови коментар (поведінку не чіпаємо) —
`comments = UiState.Loading` тепер нічого не рендерить, бо блок згорнуто:

```kotlin
        // Коментарі тут у стані Loading і згорнуті, тож retry-кнопку дає лише StreamErrorRow —
        // onNodeWithText вимагає рівно одного вузла, що еквівалентно перевірці count == 1.
```

- [ ] **Step 6: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без падінь.

```bash
git add -A
git commit -m "feat: collapse video comments by default"
```

---

### Task 6.3: HLS-джерело (`hlsUrl` крізь шари + `PlayerSource`)

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt`
- Modify: `app/src/main/java/com/ioi/youtube/domain/model/VideoStream.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/mapper/RemoteMappers.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerSource.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerSourceTest.kt` (новий)
- Test: `app/src/test/java/com/ioi/youtube/data/mapper/RemoteMappersTest.kt` (+1 тест)
- Test: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt` (+2 тести)

**Interfaces:**
- Consumes: `StreamInfo.getHlsUrl()` (NewPipe 0.26.5, перевірено: робочий манифест).
- Produces:
  - `RawVideoStream(url: String, resolution: String, format: String, hlsUrl: String? = null)`
  - `VideoStream(url: String, resolution: String, format: String, hlsUrl: String? = null)`
  - `data class PlayerSource(val url: String, val isHls: Boolean, val fallbackUrl: String?)`
  - `fun VideoStream?.toPlayerSource(): PlayerSource?`

- [ ] **Step 1: Додай HLS-модуль Media3**

`app/build.gradle.kts`, після рядка 98 (`implementation("androidx.media3:media3-common:1.5.1")`):

```kotlin
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
```

Run: `source .agent-env.sh && ./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` (артефакт тягнеться з Google Maven).

- [ ] **Step 2: Напиши тести, що падають**

`app/src/test/java/com/ioi/youtube/presentation/player/PlayerSourceTest.kt` (новий):

```kotlin
package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.VideoStream
import org.junit.Test

class PlayerSourceTest {

    @Test
    fun `no stream means no source`() {
        assertThat((null as VideoStream?).toPlayerSource()).isNull()
    }

    @Test
    fun `hls manifest wins and progressive stays as fallback`() {
        val stream = VideoStream(
            url = "https://p/360.mp4",
            resolution = "360p",
            format = "MPEG-4",
            hlsUrl = "https://m/hls",
        )

        val source = stream.toPlayerSource()

        assertThat(source).isEqualTo(
            PlayerSource(url = "https://m/hls", isHls = true, fallbackUrl = "https://p/360.mp4"),
        )
    }

    @Test
    fun `stream without hls plays progressive without fallback`() {
        val stream = VideoStream("https://p/360.mp4", "360p", "MPEG-4")

        val source = stream.toPlayerSource()

        assertThat(source).isEqualTo(
            PlayerSource(url = "https://p/360.mp4", isHls = false, fallbackUrl = null),
        )
    }

    @Test
    fun `blank hls url is ignored and blank progressive produces no source`() {
        val blankHls = VideoStream("https://p/360.mp4", "360p", "MPEG-4", hlsUrl = "   ")

        assertThat(blankHls.toPlayerSource()?.isHls).isFalse()
        assertThat(VideoStream("   ", "360p", "MPEG-4").toPlayerSource()).isNull()
    }
}
```

У `app/src/test/java/com/ioi/youtube/data/mapper/RemoteMappersTest.kt` додай тест (після тесту `maps raw stream to domain`):

```kotlin
    @Test
    fun `maps hls url to domain`() {
        val raw = RawVideoStream(
            url = "https://v/x",
            resolution = "720p",
            format = "MPEG-4",
            hlsUrl = "https://manifest.example/hls_variant",
        )

        val domain = raw.toDomain()

        assertThat(domain.hlsUrl).isEqualTo("https://manifest.example/hls_variant")
    }
```

У `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt` додай два тести в кінець класу:

```kotlin
    @Test
    fun `video stream keeps the progressive url and adds the hls manifest`() = runTest {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.videoStreams } returns listOf(videoStream("https://p/360.mp4", "360p"))
        every { info.hlsUrl } returns "https://manifest.googlevideo.com/hls_variant?x=1"
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val stream = NewPipeRemoteDataSource().fetchVideoStream("dQw4w9WgXcQ")

        assertThat(stream.url).isEqualTo("https://p/360.mp4")
        assertThat(stream.resolution).isEqualTo("360p")
        assertThat(stream.hlsUrl).isEqualTo("https://manifest.googlevideo.com/hls_variant?x=1")
    }

    @Test
    fun `video stream without hls manifest leaves hls url null`() = runTest {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.videoStreams } returns listOf(videoStream("https://p/360.mp4", "360p"))
        every { info.hlsUrl } returns null
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val stream = NewPipeRemoteDataSource().fetchVideoStream("dQw4w9WgXcQ")

        assertThat(stream.hlsUrl).isNull()
    }
```

Потрібні додаткові імпорти в тому ж файлі:

```kotlin
import org.schabi.newpipe.extractor.stream.VideoStream
```

і хелпер поруч із `searchExtractor`:

```kotlin
    /** NewPipe `VideoStream` не має публічного конструктора — тільки через ITag, тому мок. */
    private fun videoStream(url: String, resolution: String): VideoStream {
        val stream = mockk<VideoStream>(relaxed = true)
        every { stream.url } returns url
        every { stream.resolution } returns resolution
        every { stream.isVideoOnly } returns false
        every { stream.bitrate } returns 100_000
        return stream
    }
```

- [ ] **Step 3: Запусти тести — мають впасти**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests "*PlayerSourceTest*" --tests "*RemoteMappersTest*" --tests "*NewPipeRemoteDataSourceTest*"
```

Expected: FAIL з помилкою компіляції — немає `hlsUrl` у `RawVideoStream`/`VideoStream` і немає `PlayerSource`/`toPlayerSource`.

- [ ] **Step 4: Реалізуй**

`app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt` — `RawVideoStream` (рядки 42–46):

```kotlin
data class RawVideoStream(
    val url: String,
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,
)
```

`app/src/main/java/com/ioi/youtube/domain/model/VideoStream.kt` — весь файл:

```kotlin
package com.ioi.youtube.domain.model

data class VideoStream(
    val url: String,
    val resolution: String,
    val format: String,
    val hlsUrl: String? = null,
)
```

`app/src/main/java/com/ioi/youtube/data/mapper/RemoteMappers.kt` — `RawVideoStream.toDomain()` (рядки 47–51):

```kotlin
fun RawVideoStream.toDomain(): VideoStream = VideoStream(
    url = url,
    resolution = resolution,
    format = format,
    hlsUrl = hlsUrl,
)
```

`app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt` — `fetchVideoStream` (рядки 101–110):

```kotlin
    override suspend fun fetchVideoStream(videoId: String): RawVideoStream =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            val stream = pickBestVideoStream(info)
            RawVideoStream(
                url = stream.url.orEmpty(),
                resolution = stream.resolution.orEmpty(),
                format = stream.format?.name.orEmpty(),
                // HLS дає всі якості (144p…1080p) і адаптивне перемикання;
                // progressive лишається запасним варіантом.
                hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            )
        }
```

`app/src/main/java/com/ioi/youtube/presentation/player/PlayerSource.kt` (новий):

```kotlin
package com.ioi.youtube.presentation.player

import com.ioi.youtube.domain.model.VideoStream

/** Що саме грає плеєр: HLS-манифест (усі якості) або progressive-потік. */
data class PlayerSource(
    val url: String,
    val isHls: Boolean,
    val fallbackUrl: String?,
)

/**
 * Воліємо HLS: у ньому всі якості й адаптивне перемикання.
 * Progressive тримаємо як запасний варіант на випадок, коли HLS не запуститься.
 */
fun VideoStream?.toPlayerSource(): PlayerSource? {
    val stream = this ?: return null
    val hls = stream.hlsUrl?.takeIf { it.isNotBlank() }
    val progressive = stream.url.takeIf { it.isNotBlank() }
    return when {
        hls != null -> PlayerSource(url = hls, isHls = true, fallbackUrl = progressive)
        progressive != null -> PlayerSource(url = progressive, isHls = false, fallbackUrl = null)
        else -> null
    }
}
```

- [ ] **Step 5: Запусти тести — мають пройти**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests "*PlayerSourceTest*" --tests "*RemoteMappersTest*" --tests "*NewPipeRemoteDataSourceTest*"
```

Expected: `BUILD SUCCESSFUL` (4 + решта тестів пройдено).

- [ ] **Step 6: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: carry youtube hls manifest through data and domain layers"
```

---

### Task 6.4: Чиста логіка вибору якості

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/QualityOptions.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/QualityOptionsTest.kt` (новий, голий JVM)
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/QualityParametersTest.kt` (новий, Robolectric)

**Interfaces:**
- Consumes: `androidx.media3.common.{C, Player, Tracks, TrackSelectionOverride, TrackSelectionParameters}`.
- Produces:
  - `data class VideoTrackInfo(val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int)`
  - `data class QualityOption(val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int)`
  - `fun chooseQualityOptions(tracks: List<VideoTrackInfo>): List<QualityOption>`
  - `fun formatQualityLabel(height: Int, bitrate: Int): String`
  - `fun Tracks.videoTrackInfos(): List<VideoTrackInfo>`
  - `fun qualityParameters(player: Player, option: QualityOption?): TrackSelectionParameters`
- Це єдине місце, де живе логіка якості: UI (Task 6.5) лише кличе ці функції.

- [ ] **Step 1: Напиши тест чистої логіки**

`QualityOptionsTest.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Чиста логіка без Android: голий JVM, без Robolectric. */
class QualityOptionsTest {

    @Test
    fun `keeps one option per height with the highest bitrate sorted by height`() {
        val tracks = listOf(
            VideoTrackInfo(groupIndex = 0, trackIndex = 0, height = 360, bitrate = 444_000),
            VideoTrackInfo(groupIndex = 1, trackIndex = 0, height = 1080, bitrate = 1_500_000),
            VideoTrackInfo(groupIndex = 1, trackIndex = 1, height = 1080, bitrate = 4_300_000),
            VideoTrackInfo(groupIndex = 2, trackIndex = 0, height = 720, bitrate = 1_058_000),
        )

        val options = chooseQualityOptions(tracks)

        assertThat(options.map { it.height }).containsExactly(1080, 720, 360).inOrder()
        assertThat(options.map { it.groupIndex }).containsExactly(1, 2, 0).inOrder()
        assertThat(options.map { it.trackIndex }).containsExactly(1, 0, 0).inOrder()
        assertThat(options.first().bitrate).isEqualTo(4_300_000)
    }

    @Test
    fun `ignores tracks without height and empty input`() {
        assertThat(chooseQualityOptions(emptyList())).isEmpty()
        assertThat(
            chooseQualityOptions(listOf(VideoTrackInfo(0, 0, height = 0, bitrate = 100_000))),
        ).isEmpty()
    }

    @Test
    fun `formats labels in kilobits and megabits`() {
        assertThat(formatQualityLabel(360, 444_226)).isEqualTo("360p · 444 Кбіт/с")
        assertThat(formatQualityLabel(1080, 4_334_157)).isEqualTo("1080p · 4.3 Мбіт/с")
        assertThat(formatQualityLabel(2160, 18_076_636)).isEqualTo("2160p · 18.1 Мбіт/с")
        assertThat(formatQualityLabel(360, 0)).isEqualTo("360p · —")
        assertThat(formatQualityLabel(360, -1)).isEqualTo("360p · —")
    }

    @Test
    fun `labels keep the us decimal separator under a comma locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertThat(formatQualityLabel(1080, 4_334_157)).isEqualTo("1080p · 4.3 Мбіт/с")
        } finally {
            Locale.setDefault(previous)
        }
    }
}
```

`QualityParametersTest.kt` (Robolectric — Media3 створює свої обʼєкти, яким потрібен контекст Android):

```kotlin
package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QualityParametersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun tracks(vararg formats: Format): Tracks {
        val groups = formats.map { format ->
            Tracks.Group(
                TrackGroup(format),
                /* adaptiveSupported= */ true,
                /* trackSupport= */ intArrayOf(C.FORMAT_HANDLED),
                /* trackSelected= */ booleanArrayOf(true),
            )
        }
        return Tracks(groups)
    }

    private fun playerWith(
        tracks: Tracks,
        baseline: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build(),
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.currentTracks } returns tracks
        every { player.trackSelectionParameters } returns baseline
        return player
    }

    private fun videoFormat(height: Int, bitrate: Int = Format.NO_VALUE): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setHeight(height)
            .setAverageBitrate(bitrate)
            .build()

    private fun audioFormat(): Format =
        Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()

    /** Одна група з кiлькома дорiжками — щоб перевiрити, що `trackIndex` справдi вживається. */
    private fun trackGroup(vararg formats: Format): Tracks =
        Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(*formats),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ IntArray(formats.size) { C.FORMAT_HANDLED },
                    /* trackSelected= */ BooleanArray(formats.size) { true },
                ),
            ),
        )

    @Test
    fun `video track infos expose group and track indexes with height and bitrate`() {
        val tracks = tracks(
            videoFormat(1080, 4_300_000),
            videoFormat(720, 1_058_000),
        )

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.height }).containsExactly(1080, 720).inOrder()
        assertThat(infos.map { it.groupIndex }).containsExactly(0, 1).inOrder()
        assertThat(infos.map { it.trackIndex }).containsExactly(0, 0).inOrder()
        assertThat(infos.first().bitrate).isEqualTo(4_300_000)
    }

    @Test
    fun `auto clears the video override and keeps the audio one`() {
        val tracks = tracks(videoFormat(1080), audioFormat())
        val videoGroup = tracks.groups[0].mediaTrackGroup
        val audioGroup = tracks.groups[1].mediaTrackGroup
        val baseline = TrackSelectionParameters.Builder(context).build().buildUpon()
            .setOverrideForType(TrackSelectionOverride(videoGroup, 0))
            .setOverrideForType(TrackSelectionOverride(audioGroup, 0))
            .build()
        val player = playerWith(tracks, baseline)

        val expected = baseline.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()

        assertThat(qualityParameters(player, option = null)).isEqualTo(expected)
    }

    @Test
    fun `explicit option overrides exactly that track group`() {
        val player = playerWith(
            tracks(
                videoFormat(1080, 4_300_000),
                videoFormat(720, 1_058_000),
            ),
        )
        val group = player.currentTracks.groups[1].mediaTrackGroup
        val expected = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, 0))
            .build()

        val actual = qualityParameters(player, QualityOption(groupIndex = 1, trackIndex = 0, height = 720, bitrate = 1_058_000))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `option pointing outside the current tracks keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080)))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 7, trackIndex = 0, height = 480, bitrate = 500_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `stale option with an out-of-range track index keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080)))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 0, trackIndex = 5, height = 1080, bitrate = 4_300_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `video track infos ignore audio groups`() {
        val tracks = tracks(videoFormat(1080), audioFormat())

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.groupIndex }).containsExactly(0)
        assertThat(infos.map { it.height }).containsExactly(1080)
    }

    @Test
    fun `video track infos enumerate every track in a group`() {
        val tracks = trackGroup(videoFormat(1080, 1_500_000), videoFormat(1080, 4_300_000))

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.trackIndex }).containsExactly(0, 1)
        assertThat(infos.map { it.bitrate }).containsExactly(1_500_000, 4_300_000)
    }

    @Test
    fun `option pointing at a non-video group keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080), audioFormat()))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 1, trackIndex = 0, height = 720, bitrate = 1_058_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `explicit option uses the given track index`() {
        val tracks = trackGroup(videoFormat(1080, 1_500_000), videoFormat(1080, 4_300_000))
        val player = playerWith(tracks)
        val group = tracks.groups[0].mediaTrackGroup
        val expected = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, 1))
            .build()

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 0, trackIndex = 1, height = 1080, bitrate = 4_300_000),
        )

        assertThat(actual).isEqualTo(expected)
    }
}
```

- [ ] **Step 2: Запусти — має впасти**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*QualityOptionsTest*" --tests "*QualityParametersTest*"
```

Expected: FAIL з помилкою компіляції — немає `chooseQualityOptions`, `formatQualityLabel`, `videoTrackInfos`, `qualityParameters`.

- [ ] **Step 3: Реалізуй `QualityOptions.kt`**

`app/src/main/java/com/ioi/youtube/presentation/player/QualityOptions.kt`:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import java.util.Locale

/** Одна відеодоріжка манифеста. Без Media3 — легше тестувати. */
data class VideoTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int,
)

/** Пункт меню якості. */
data class QualityOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int,
)

/**
 * Одна якість на висоту (найвищий бітрейт: у YouTube 1080p буває і avc1, і vp9),
 * за спаданням висоти.
 */
fun chooseQualityOptions(tracks: List<VideoTrackInfo>): List<QualityOption> =
    tracks
        .filter { it.height > 0 }
        .groupBy { it.height }
        .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<VideoTrackInfo> { it.height }.thenByDescending { it.bitrate })
        .map { QualityOption(it.groupIndex, it.trackIndex, it.height, it.bitrate) }

fun formatQualityLabel(height: Int, bitrate: Int): String = "${height}p · ${formatBitrate(bitrate)}"

private fun formatBitrate(bitrate: Int): String = when {
    bitrate <= 0 -> "—"
    bitrate < 1_000_000 -> "${bitrate / 1000} Кбіт/с"
    else -> "%.1f Мбіт/с".format(Locale.US, bitrate / 1_000_000.0)
}

/**
 * Відеодоріжки зі стану плеєра (порядок груп зберігаємо — індекси йдуть у меню).
 *
 * Лише групи з типом [C.TRACK_TYPE_VIDEO]: Media3 зберiгає override за типом
 * групи, тож override для групи з типом UNKNOWN (mime невiдомий) нiколи не
 * застосується — краще не показувати такий пункт, нiж показати й зробити
 * його тихим no-op. Для HLS mime завжди є (з `CODECS`), тож меню не страждає.
 */
fun Tracks.videoTrackInfos(): List<VideoTrackInfo> =
    groups.flatMapIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_VIDEO) {
            emptyList()
        } else {
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                VideoTrackInfo(groupIndex, trackIndex, format.height, format.bitrate)
            }
        }
    }

/** Новi параметри вибору дорiжок; `option == null` — «Авто» (адаптивно). */
fun qualityParameters(player: Player, option: QualityOption?): TrackSelectionParameters {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        return builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }
    val group = player.currentTracks.groups.getOrNull(option.groupIndex)
    // Меню могло пережити змiну дорiжок: iндекс групи/дорiжки мiг застарiти,
    // а на цьому мiсцi тепер може бути не вiдео. Тодi лишаємо параметри як є —
    // краще грати як ранiше, нiж упасти чи перемкнути аудiо.
    if (group == null || option.trackIndex !in 0 until group.length) {
        return player.trackSelectionParameters
    }
    if (group.type != C.TRACK_TYPE_VIDEO) {
        return player.trackSelectionParameters
    }
    return builder
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}
```

- [ ] **Step 4: Запусти — має пройти**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*QualityOptionsTest*" --tests "*QualityParametersTest*"
```

Expected: `BUILD SUCCESSFUL`, 13 тестів пройдено (4 QualityOptionsTest + 9 QualityParametersTest).

Якщо `QualityParametersTest` падає саме на створенні `Tracks`/`TrackSelectionParameters` (не на логіці) — залишай Robolectric-анотацію, вона вже стоїть; знімати її не потрібно.

- [ ] **Step 5: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: add pure quality option logic for the player"
```

---

### Task 6.5: Плеєр: HLS, відкат на progressive, меню якості

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerSource.kt` (+`playerMediaItem`)
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt` (передати `PlayerSource`)
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/VideoPlayerTest.kt` (переписати виклики + 2 нові тести)
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt` (оновлений виклик)

**Interfaces:**
- Consumes: `PlayerSource`, `PlayerSource.toPlayerSource()`, `QualityOptions.kt` (Task 6.4).
- Produces: `@Composable fun VideoPlayer(source: PlayerSource?, modifier: Modifier = Modifier, isFullscreen: Boolean = false, onToggleFullscreen: (() -> Unit)? = null, unavailableText: String = "Відео недоступне")` — кнопка повного екрана лише за наявності `onToggleFullscreen` (Task 6.6). Сигнатура `VideoPlayer(videoUrl = …)` більше не існує.

- [ ] **Step 1: Онови тести плеєра (мають впасти)**

`VideoPlayerTest.kt` — повністю заміни файл:

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.presentation.theme.IOITubeTheme
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

    private val progressive = PlayerSource(
        url = "https://example.com/v.mp4",
        isHls = false,
        fallbackUrl = null,
    )

    @Test
    fun `missing source shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = null)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `blank url shows unavailable message instead of player`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = PlayerSource(url = "   ", isHls = false, fallbackUrl = null))
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
    }

    @Test
    fun `available source renders player without unavailable message`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive)
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertDoesNotExist()
    }

    @Test
    fun `quality menu is absent while hls manifest has no video tracks yet`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(
                    source = PlayerSource(
                        url = "https://manifest.example/hls_variant",
                        isHls = true,
                        fallbackUrl = "https://example.com/v.mp4",
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Авто").assertDoesNotExist()
    }

    @Test
    fun `fullscreen button is hidden without a callback`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive)
            }
        }

        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertDoesNotExist()
    }

    @Test
    fun `fullscreen button is shown and reflects the state with a callback`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = progressive, isFullscreen = true, onToggleFullscreen = {})
            }
        }

        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
        composeRule.onNodeWithContentDescription("На весь екран").assertDoesNotExist()
    }
}
```

`PlayerScreenTest.kt` — у тесті `stream success renders player instead of unavailable message` нічого змінювати не треба: `VideoStream(...)` далі проходить через `toPlayerSource()`. Якщо десь ще лишився виклик `VideoPlayer(videoUrl = …)` — заміни його на `VideoPlayer(source = …)`.

- [ ] **Step 2: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*VideoPlayerTest*"`

Expected: FAIL з помилкою компіляції — `VideoPlayer` не має параметра `source`.

- [ ] **Step 3: Додай `playerMediaItem` у `PlayerSource.kt`**

Допиши в кінець `PlayerSource.kt` (і додай імпорти `androidx.media3.common.MediaItem`,
`androidx.media3.common.MimeTypes`):

```kotlin
/** MediaItem для ExoPlayer: HLS-манифест вимагає явного MIME-типу (URL без розширення). */
internal fun playerMediaItem(source: PlayerSource): MediaItem =
    MediaItem.Builder()
        .setUri(source.url)
        .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()
```

- [ ] **Step 4: Перепиши `VideoPlayer.kt`**

`app/src/main/java/com/ioi/youtube/presentation/player/VideoPlayer.kt` — повністю:

```kotlin
package com.ioi.youtube.presentation.player

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Тег для logcat: фiльтр `adb logcat -s IOITube`. */
private const val TAG = "IOITube"

@Composable
fun VideoPlayer(
    source: PlayerSource?,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    unavailableText: String = "Відео недоступне",
) {
    if (source == null || source.url.isBlank()) {
        Box(
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = unavailableText, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var usingFallback by remember(source) { mutableStateOf(false) }
    var videoTracks by remember(source) { mutableStateOf(emptyList<VideoTrackInfo>()) }
    var selected by remember(source) { mutableStateOf<QualityOption?>(null) }
    var menuOpen by remember(source) { mutableStateOf(false) }
    var playerError by remember(source) { mutableStateOf<PlaybackException?>(null) }

    val exoPlayer = remember(source.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(playerMediaItem(source))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer, source) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                videoTracks = tracks.videoTrackInfos()
            }

            override fun onPlayerError(error: PlaybackException) {
                val fallback = source.fallbackUrl
                if (!source.isHls || usingFallback || fallback == null || fallback.isBlank()) {
                    playerError = error
                    Log.e(TAG, "player error", error)
                    return
                }
                Log.e(TAG, "HLS playback failed, falling back to progressive", error)
                usingFallback = true
                videoTracks = emptyList()
                selected = null
                exoPlayer.setMediaItem(playerMediaItem(PlayerSource(fallback, isHls = false, fallbackUrl = null)))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    val options = chooseQualityOptions(videoTracks)
    val showQuality = source.isHls && !usingFallback && playerError == null && options.isNotEmpty()

    Box(
        modifier = modifier.then(
            if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
        ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showQuality) {
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = Color.White)
                        Text(
                            text = selected?.let { "${it.height}p" } ?: "Авто",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Авто") },
                            onClick = {
                                exoPlayer.trackSelectionParameters = qualityParameters(exoPlayer, null)
                                selected = null
                                menuOpen = false
                            },
                        )
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatQualityLabel(option.height, option.bitrate)) },
                                onClick = {
                                    exoPlayer.trackSelectionParameters =
                                        qualityParameters(exoPlayer, option)
                                    selected = option
                                    menuOpen = false
                                },
                            )
                        }
                    }
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
```

Чому `playerError`: після невдалого HLS меню якості безглузде — ховаємо його, щоб підпис не показував якість, якої немає.

- [ ] **Step 5: Передай `PlayerSource` з `PlayerScreen`**

`PlayerScreen.kt`, у `item(key = "player")` (рядки 67–71):

```kotlin
                item(key = "player") {
                    VideoPlayer(
                        source = (uiState.stream as? UiState.Success)?.data.toPlayerSource(),
                    )
                }
```

- [ ] **Step 6: Запусти тести — мають пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*VideoPlayerTest*" --tests "*PlayerScreenTest*"`

Expected: `BUILD SUCCESSFUL` (6 VideoPlayerTest + 5 PlayerScreenTest).

- [ ] **Step 7: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь. `media3-exoplayer-hls` уже доданий у Task 6.3 — перевір, що `assembleDebug` не скаржиться на дублікати класів.

```bash
git add -A
git commit -m "feat: play youtube hls with progressive fallback and quality menu"
```

---

### Task 6.6: Поворот екрана і повний екран

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ioi/youtube/presentation/navigation/Orientation.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/util/ActivityExt.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreen.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/navigation/OrientationTest.kt` (новий)
- Test: `app/src/test/java/com/ioi/youtube/presentation/player/PlayerScreenTest.kt` (+2 тести)

**Interfaces:**
- Consumes: `Routes.PLAYER`, `PlayerSource`, `VideoPlayer` (Task 6.5).
- Produces:
  - `fun orientationForRoute(route: String?): Int`
  - `fun Context.findActivity(): Activity?`
  - `PlayerScreen(uiState, onVideoClick, onRetry, modifier, isFullscreen: Boolean = false, onToggleFullscreen: (() -> Unit)? = null)`

- [ ] **Step 1: Прибери жорсткий портрет і додай `configChanges`**

`app/src/main/AndroidManifest.xml` — блок `<activity>`:

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden">
```

`configChanges` обовʼязковий: без нього поворот перестворює Activity і плеєр починає відео з нуля.

- [ ] **Step 2: Напиши тести, що падають**

`app/src/test/java/com/ioi/youtube/presentation/navigation/OrientationTest.kt`:

```kotlin
package com.ioi.youtube.presentation.navigation

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrientationTest {

    @Test
    fun `player screen rotates freely`() {
        assertThat(orientationForRoute(Routes.PLAYER))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
    }

    @Test
    fun `every other route stays portrait`() {
        assertThat(orientationForRoute(Routes.HOME))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(orientationForRoute(Routes.SEARCH))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(orientationForRoute(null))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
}
```

У `PlayerScreenTest.kt` додай у кінець класу (і імпорт `androidx.compose.ui.test.onNodeWithContentDescription`):

```kotlin
    @Test
    fun `fullscreen renders only the player`() {
        composeRule.setContent {
            IOITubeTheme {
                PlayerScreen(
                    uiState = PlayerUiState(
                        video = UiState.Success(TestData.details("dQw4w9WgXcQ")),
                        stream = UiState.Success(
                            VideoStream("https://example.com/v.mp4", "720p", "MPEG-4"),
                        ),
                        comments = UiState.Success(
                            listOf(
                                Comment("c1", "Alice", null, "First!", 150, "1 day ago", true, true),
                            ),
                        ),
                    ),
                    onVideoClick = {},
                    onRetry = {},
                    isFullscreen = true,
                    onToggleFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithText("Video dQw4w9WgXcQ").assertDoesNotExist()
        composeRule.onNodeWithText("Коментарі (1)").assertDoesNotExist()
        composeRule.onNodeWithText("Рекомендації").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }

    @Test
    fun `fullscreen button reports the toggle`() {
        var toggled = false

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
                    onToggleFullscreen = { toggled = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("На весь екран").performClick()

        assertThat(toggled).isTrue()
    }
```

- [ ] **Step 3: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*OrientationTest*" --tests "*PlayerScreenTest*"`

Expected: FAIL з помилкою компіляції — немає `orientationForRoute` і параметрів `isFullscreen`/`onToggleFullscreen`.

- [ ] **Step 4: Реалізуй правило орієнтації та доступ до Activity**

`app/src/main/java/com/ioi/youtube/presentation/navigation/Orientation.kt`:

```kotlin
package com.ioi.youtube.presentation.navigation

import android.content.pm.ActivityInfo

/** Вільний поворот лише на екрані вiдео (як у YouTube); решта застосунку — портрет. */
fun orientationForRoute(route: String?): Int =
    if (route == Routes.PLAYER) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
```

`app/src/main/java/com/ioi/youtube/presentation/util/ActivityExt.kt`:

```kotlin
package com.ioi.youtube.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Activity з Compose-контексту (орієнтація і системні бари). */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
```

- [ ] **Step 5: Застосуй орієнтацію за маршрутом у `IOITubeApp`**

`IOITubeApp.kt` — додай імпорти:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ioi.youtube.presentation.util.findActivity
```

і одразу після `val showBottomBar = …`:

```kotlin
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Екран вiдео — вільний поворот, решта застосунку — портрет.
    LaunchedEffect(currentRoute, activity) {
        activity?.requestedOrientation = orientationForRoute(currentRoute)
    }
```

- [ ] **Step 6: Перепиши `PlayerRoute` і додай повний екран у `PlayerScreen`**

`PlayerScreen.kt` — додай імпорти:

```kotlin
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ioi.youtube.presentation.util.findActivity
```

Заміни `PlayerRoute` (рядки 35–46):

```kotlin
@Composable
fun PlayerRoute(
    onVideoClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isFullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
        uiState = uiState,
        onVideoClick = onVideoClick,
        onRetry = viewModel::retry,
        isFullscreen = isFullscreen,
        onToggleFullscreen = {
            // Вихiд ставить портрет i лишає його: автоматично повертати SENSOR
            // не можна — на телефонi, який тримають боком, вiн одразу поверне
            // ландшафт, i користувач знову опиниться в повному екранi.
            // Вільний поворот повертається при наступному входi на екран вiдео
            // (LaunchedEffect у IOITubeApp).
            activity?.requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        },
    )
}
```

Заміни `PlayerScreen` (рядки 48–100): додай два параметри і розгалуження на ландшафт:

```kotlin
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val source = (uiState.stream as? UiState.Success)?.data.toPlayerSource()

    when (val videoState = uiState.video) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = videoState.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> if (isFullscreen) {
            // Ландшафт = повний екран: лише вiдео, без заголовка, коментарiв i рекомендацiй.
            VideoPlayer(
                source = source,
                modifier = modifier.fillMaxSize(),
                isFullscreen = true,
                onToggleFullscreen = onToggleFullscreen,
            )
        } else {
            val details = videoState.data
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "player") {
                    VideoPlayer(
                        source = source,
                        onToggleFullscreen = onToggleFullscreen,
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
```

- [ ] **Step 7: Дві правки в `VideoPlayer` (борг із Task 6.5)**

`VideoPlayer.kt` — у `AndroidView` додай прив'язку плеєра до view, бо `factory` не виконується повторно, коли композиція вже має вузол:

```kotlin
            update = { view -> view.player = exoPlayer },
            onRelease = { view -> view.player = null },
```

`VideoPlayer.kt` — у гілці «немає джерела» поверни кнопку повного екрана, інакше в ландшафті без вiдео користувач не має як вийти:

```kotlin
    if (source == null || source.url.isBlank()) {
        Box(
            modifier = modifier.then(
                if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = unavailableText, style = MaterialTheme.typography.bodyLarge)

            if (onToggleFullscreen != null) {
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = onToggleFullscreen,
                ) {
                    Icon(
                        imageVector = if (isFullscreen) {
                            Icons.Filled.FullscreenExit
                        } else {
                            Icons.Filled.Fullscreen
                        },
                        contentDescription = if (isFullscreen) {
                            "Вийти з повного екрана"
                        } else {
                            "На весь екран"
                        },
                        tint = Color.White,
                    )
                }
            }
        }
        return
    }
```

У `VideoPlayerTest.kt` додай тест:

```kotlin
    @Test
    fun `unavailable fullscreen still offers the exit button`() {
        composeRule.setContent {
            IOITubeTheme {
                VideoPlayer(source = null, isFullscreen = true, onToggleFullscreen = {})
            }
        }

        composeRule.onNodeWithText("Відео недоступне").assertExists()
        composeRule.onNodeWithContentDescription("Вийти з повного екрана").assertExists()
    }
```

- [ ] **Step 8: Запусти тести — мають пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*OrientationTest*" --tests "*PlayerScreenTest*" --tests "*VideoPlayerTest*"`

Expected: `BUILD SUCCESSFUL` (2 OrientationTest + 7 PlayerScreenTest + 7 VideoPlayerTest).

- [ ] **Step 9: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: rotate the video player and add fullscreen mode"
```

---

### Task 6.7: Стрічка шортсів у джерелі даних

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/data/model/RemoteModels.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/YouTubeRemoteDataSource.kt`
- Modify: `app/src/main/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSource.kt`
- Modify: `app/src/test/java/com/ioi/youtube/testutil/FakeRemoteDataSource.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt` (+7 тестів)

**Interfaces:**
- Consumes: `fetchHomeFeed()`, `search(query)`, `ChannelExtractor.getTabs()`, `StreamingService.getChannelTabExtractor(ListLinkHandler)`, `StreamInfoItem.isShortFormContent`.
- Produces:
  - `RawVideoSummary(…, uploaderUrl: String = "")`
  - `suspend fun fetchShorts(): List<RawVideoSummary>`
  - `suspend fun fetchMoreShorts(): List<RawVideoSummary>`

- [ ] **Step 1: Напиши тести, що падають**

У `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceTest.kt` додай імпорти:

```kotlin
import io.mockk.slot
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
```

додай хелпери поруч із `item`:

```kotlin
    private fun linkHandler(url: String): ListLinkHandler {
        val handler = mockk<ListLinkHandler>(relaxed = true)
        every { handler.url } returns url
        return handler
    }

    private fun homeItem(id: String, uploaderUrl: String): StreamInfoItem =
        item(id, "Home $id").apply { this.uploaderUrl = uploaderUrl }

    private fun channelExtractor(vararg tabUrls: String): ChannelExtractor {
        val extractor = mockk<ChannelExtractor>(relaxed = true)
        every { extractor.tabs } returns tabUrls.map(::linkHandler)
        return extractor
    }

    private fun shortsTab(items: List<StreamInfoItem>): ChannelTabExtractor {
        val extractor = mockk<ChannelTabExtractor>(relaxed = true)
        every { extractor.initialPage } returns emptyPage(items)
        return extractor
    }
```

і сім тестів у кінець класу:

```kotlin
    @Test
    fun `shorts are collected from the shorts tabs of home feed channels`() = runTest {
        val short = item("sssssssssss", "Short").apply { isShortFormContent = true }
        val long = item("lllllllllll", "Long")
        val tab = shortsTab(listOf(short, long))
        val channel = channelExtractor(
            "https://www.youtube.com/UC1/videos",
            "https://www.youtube.com/UC1/shorts",
        )
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(listOf(homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1")))
        every { service.getChannelExtractor("https://www.youtube.com/UC1") } returns channel
        every { service.getChannelTabExtractor(any()) } returns tab
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchShorts()

        assertThat(shorts.map { it.id }).containsExactly("sssssssssss")
        verifyOrder { channel.fetchPage(); channel.tabs }
        verifyOrder { tab.fetchPage(); tab.initialPage }
    }

    @Test
    fun `channels without a shorts tab are skipped`() = runTest {
        val channel = channelExtractor("https://www.youtube.com/UC1/videos")
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(listOf(homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1")))
        every { service.getChannelExtractor("https://www.youtube.com/UC1") } returns channel
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchShorts()

        assertThat(shorts).isEmpty()
        verify(exactly = 0) { service.getChannelTabExtractor(any()) }
    }

    @Test
    fun `shorts feed reports failure when every channel fails`() = runTest {
        val channel = mockk<ChannelExtractor>(relaxed = true)
        every { channel.fetchPage() } throws IOException("offline")
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(listOf(homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1")))
        every { service.getChannelExtractor("https://www.youtube.com/UC1") } returns channel
        stubYouTubeService(service)

        val thrown = runCatching { NewPipeRemoteDataSource().fetchShorts() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(thrown).hasMessageThat().isEqualTo("offline")
    }

    @Test
    fun `more shorts are built from a topic search`() = runTest {
        val tab = shortsTab(
            listOf(item("ttttttttttt", "Topic short").apply { isShortFormContent = true }),
        )
        val channel = channelExtractor("https://www.youtube.com/UC2/shorts")
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(listOf(homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC2")))
        every { service.getChannelExtractor("https://www.youtube.com/UC2") } returns channel
        every { service.getChannelTabExtractor(any()) } returns tab
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchMoreShorts()

        assertThat(shorts.map { it.id }).containsExactly("ttttttttttt")
        val query = slot<String>()
        verify(exactly = 1) { service.getSearchExtractor(capture(query)) }
        assertThat(query.captured).isIn(NewPipeRemoteDataSource.SHORTS_QUERIES)
    }

    @Test
    fun `a failing channel does not hide the shorts of the others`() = runTest {
        val failing = mockk<ChannelExtractor>(relaxed = true)
        every { failing.fetchPage() } throws IOException("offline")
        val tab = shortsTab(
            listOf(item("sssssssssss", "Short").apply { isShortFormContent = true }),
        )
        val working = channelExtractor("https://www.youtube.com/UC2/shorts")
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns searchExtractor(
            listOf(
                homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1"),
                homeItem("iiiiiiiiiii", "https://www.youtube.com/UC2"),
            ),
        )
        every { service.getChannelExtractor("https://www.youtube.com/UC1") } returns failing
        every { service.getChannelExtractor("https://www.youtube.com/UC2") } returns working
        every { service.getChannelTabExtractor(any()) } returns tab
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchShorts()

        assertThat(shorts.map { it.id }).containsExactly("sssssssssss")
    }

    @Test
    fun `the same short from two channels is kept once`() = runTest {
        val tab = shortsTab(
            listOf(item("sssssssssss", "Short").apply { isShortFormContent = true }),
        )
        val channel = channelExtractor("https://www.youtube.com/UC1/shorts")
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns searchExtractor(
            listOf(
                homeItem("hhhhhhhhhhh", "https://www.youtube.com/UC1"),
                homeItem("iiiiiiiiiii", "https://www.youtube.com/UC2"),
            ),
        )
        every { service.getChannelExtractor(any<String>()) } returns channel
        every { service.getChannelTabExtractor(any()) } returns tab
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchShorts()

        assertThat(shorts).hasSize(1)
        assertThat(shorts.map { it.id }).containsExactly("sssssssssss")
    }

    @Test
    fun `home feed without channel urls yields no shorts`() = runTest {
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(listOf(item("hhhhhhhhhhh", "No channel")))
        stubYouTubeService(service)

        val shorts = NewPipeRemoteDataSource().fetchShorts()

        assertThat(shorts).isEmpty()
        verify(exactly = 0) { service.getChannelExtractor(any<String>()) }
    }
```

Тести, які дружать із `FakeRemoteDataSource` (Task 6.8+), зламаються на компіляції — це очікувано, лікує Step 4.

- [ ] **Step 2: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*NewPipeRemoteDataSourceTest*"`

Expected: FAIL з помилкою компіляції — у `YouTubeRemoteDataSource`/`FakeRemoteDataSource` немає `fetchShorts`/`fetchMoreShorts`, у `RawVideoSummary` немає `uploaderUrl`.

- [ ] **Step 3: Додай `uploaderUrl` у модель і методи в інтерфейс**

`RemoteModels.kt` — `RawVideoSummary`:

```kotlin
data class RawVideoSummary(
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
    /** URL каналу — потрiбен лише для стрiчки Shorts, у домен не йде. */
    val uploaderUrl: String = "",
)
```

`YouTubeRemoteDataSource.kt` — додай у інтерфейс:

```kotlin
    /**
     * Стрiчка Shorts: у YouTube немає анонiмної стрiчки шортсiв, тому збираємо її
     * з вкладок Shorts каналiв, якi трапились у стрiчцi «Головна».
     */
    suspend fun fetchShorts(): List<RawVideoSummary>

    /** Ще шортси для «гортати далi»: канали з випадкової теми. */
    suspend fun fetchMoreShorts(): List<RawVideoSummary>
```

- [ ] **Step 4: Реалізуй у `NewPipeRemoteDataSource` і пофіксь фейк**

`NewPipeRemoteDataSource.kt` — додай після `fetchVideoStream`:

```kotlin
    override suspend fun fetchShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        shortsFromChannels(channelsFrom(fetchHomeFeed()))
    }

    override suspend fun fetchMoreShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        // Iнша тема → iншi канали → iншi шортси: стрiчка не закiнчується.
        shortsFromChannels(channelsFrom(search(SHORTS_QUERIES.random())))
    }

    private fun channelsFrom(videos: List<RawVideoSummary>): List<String> =
        videos.mapNotNull { it.uploaderUrl.takeIf(String::isNotBlank) }
            .distinct()
            .take(SHORTS_CHANNEL_LIMIT)

    private suspend fun shortsFromChannels(channels: List<String>): List<RawVideoSummary> {
        if (channels.isEmpty()) return emptyList()
        val pages = coroutineScope {
            channels.map { channel -> async { runCatching { shortsOfChannel(channel) } } }.awaitAll()
        }
        val loaded = pages.mapNotNull { it.getOrNull() }
        if (loaded.isEmpty()) {
            // Жоден канал не завантажився — віддаємо помилку вгору, як у fetchHomeFeed.
            throw pages.firstNotNullOf { it.exceptionOrNull() }
        }
        return loaded.flatten().distinctBy { it.id }.shuffled()
    }

    /** Вкладка Shorts каналу; канал без неї дає порожнiй список. */
    private fun shortsOfChannel(channelUrl: String): List<RawVideoSummary> {
        val channel = youtube.getChannelExtractor(channelUrl)
        channel.fetchPage()
        val shortsTab = channel.tabs.firstOrNull {
            it.url.orEmpty().trimEnd('/').endsWith(SHORTS_TAB_MARKER)
        }
            ?: return emptyList()
        val tabExtractor = youtube.getChannelTabExtractor(shortsTab)
        tabExtractor.fetchPage()
        return tabExtractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.isShortFormContent }
            .map { it.toRawSummary() }
    }
```

у `toRawSummary()` додай поле:

```kotlin
    private fun StreamInfoItem.toRawSummary() = RawVideoSummary(
        id = YouTubeUrlParser.extractVideoId(url).orEmpty(),
        title = name.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.lastOrNull()?.url.orEmpty(),
        durationSeconds = duration.orZero(),
        viewCount = viewCount.orZero(),
        uploadDate = textualUploadDate,
        uploaderUrl = uploaderUrl.orEmpty(),
    )
```

у `companion object` додай:

```kotlin
        /** Скiльки каналiв тягнемо для стрiчки Shorts. */
        internal const val SHORTS_CHANNEL_LIMIT = 6

        /** Ознака вкладки Shorts у списку вкладок каналу. */
        internal const val SHORTS_TAB_MARKER = "/shorts"

        /** Теми для «гортати далi»: нова тема → новi канали → новi шортси. */
        internal val SHORTS_QUERIES = listOf(
            "музика", "гумор", "тварини", "спорт", "ігри", "кулінарія", "авто", "подорожі",
        )
```

`FakeRemoteDataSource.kt` — додай поля й методи (решта без змін):

```kotlin
    var shorts: List<RawVideoSummary> = emptyList()
    var moreShorts: List<RawVideoSummary> = emptyList()
    var shortsError: Exception? = null
    var moreShortsError: Exception? = null

    var shortsCalls = 0
        private set
    var moreShortsCalls = 0
        private set

    override suspend fun fetchShorts(): List<RawVideoSummary> {
        shortsCalls++
        shortsError?.let { throw it }
        return shorts
    }

    override suspend fun fetchMoreShorts(): List<RawVideoSummary> {
        moreShortsCalls++
        moreShortsError?.let { throw it }
        return moreShorts
    }
```

- [ ] **Step 5: Запусти тести — мають пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*NewPipeRemoteDataSourceTest*"`

Expected: `BUILD SUCCESSFUL` (16 тестів: 9 наявних + 7 нових).

- [ ] **Step 6: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: build a shorts feed from channel shorts tabs"
```

---

### Task 6.8: `ShortsRepository` + Use Cases + DI

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/repository/ShortsRepository.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetShortsUseCase.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetMoreShortsUseCase.kt`
- Create: `app/src/main/java/com/ioi/youtube/data/repository/ShortsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/ioi/youtube/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/ioi/youtube/data/repository/ShortsRepositoryImplTest.kt` (новий)

**Interfaces:**
- Consumes: `YouTubeRemoteDataSource.fetchShorts()` / `fetchMoreShorts()` (Task 6.7), `RawVideoSummary.toDomain()`.
- Produces:
  - `interface ShortsRepository { fun getShorts(): Flow<UiState<List<VideoSummary>>>; fun getMoreShorts(): Flow<UiState<List<VideoSummary>>> }`
  - `class GetShortsUseCase @Inject constructor(…) { operator fun invoke(): Flow<UiState<List<VideoSummary>>> }`
  - `class GetMoreShortsUseCase @Inject constructor(…) { operator fun invoke(): Flow<UiState<List<VideoSummary>>> }`
  - `ShortsRepositoryImpl.ERROR_LOAD_SHORTS == "Не вдалося завантажити шортси"`

- [ ] **Step 1: Напиши тест, що падає**

`app/src/test/java/com/ioi/youtube/data/repository/ShortsRepositoryImplTest.kt`:

```kotlin
package com.ioi.youtube.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric потрiбен, бо гiлка помилки викликає `android.util.Log`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsRepositoryImplTest {

    private val remote = FakeRemoteDataSource()
    private val repository = ShortsRepositoryImpl(remote)

    private fun raw(id: String) = RawVideoSummary(
        id = id,
        title = "Short $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://img/$id.jpg",
        durationSeconds = 0,
        viewCount = 10,
        uploadDate = null,
    )

    @Test
    fun `emits loading then mapped shorts`() = runTest {
        remote.shorts = listOf(raw("aaaaaaaaaaa"))

        repository.getShorts().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val state = awaitItem()
            assertThat(state).isInstanceOf(UiState.Success::class.java)
            assertThat((state as UiState.Success).data.map { it.id }).containsExactly("aaaaaaaaaaa")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits error with the pinned message`() = runTest {
        remote.shortsError = IOException("offline")

        repository.getShorts().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val state = awaitItem()
            assertThat(state).isInstanceOf(UiState.Error::class.java)
            assertThat((state as UiState.Error).message).isEqualTo("Не вдалося завантажити шортси")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `more shorts use the dedicated remote call`() = runTest {
        remote.moreShorts = listOf(raw("bbbbbbbbbbb"))

        repository.getMoreShorts().test {
            awaitItem()
            assertThat(awaitItem()).isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(remote.moreShortsCalls).isEqualTo(1)
        assertThat(remote.shortsCalls).isEqualTo(0)
    }
}
```

- [ ] **Step 2: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*ShortsRepositoryImplTest*"`

Expected: FAIL з помилкою компіляції — немає `ShortsRepositoryImpl`/`ShortsRepository`.

- [ ] **Step 3: Створи контракт, реалізацію і Use Cases**

`app/src/main/java/com/ioi/youtube/domain/repository/ShortsRepository.kt`:

```kotlin
package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface ShortsRepository {
    fun getShorts(): Flow<UiState<List<VideoSummary>>>

    /** Ще шортси для «гортати далi» — окремий запит, не наступна сторiнка каналу. */
    fun getMoreShorts(): Flow<UiState<List<VideoSummary>>>
}
```

`app/src/main/java/com/ioi/youtube/domain/usecase/GetShortsUseCase.kt`:

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.ShortsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetShortsUseCase @Inject constructor(
    private val shortsRepository: ShortsRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> = shortsRepository.getShorts()
}
```

`app/src/main/java/com/ioi/youtube/domain/usecase/GetMoreShortsUseCase.kt`:

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.ShortsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMoreShortsUseCase @Inject constructor(
    private val shortsRepository: ShortsRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> = shortsRepository.getMoreShorts()
}
```

`app/src/main/java/com/ioi/youtube/data/repository/ShortsRepositoryImpl.kt`:

```kotlin
package com.ioi.youtube.data.repository

import android.util.Log
import com.ioi.youtube.data.mapper.toDomain
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.ShortsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Тег для logcat: фiльтр `adb logcat -s IOITube`. */
private const val TAG = "IOITube"

/**
 * Стрiчка Shorts не кешується (як коментарi): манифести й вкладки живуть хвилини,
 * а свiжий список завжди рiзний.
 */
@Singleton
class ShortsRepositoryImpl @Inject constructor(
    private val remote: YouTubeRemoteDataSource,
) : ShortsRepository {

    override fun getShorts(): Flow<UiState<List<VideoSummary>>> = shortsFlow { remote.fetchShorts() }

    override fun getMoreShorts(): Flow<UiState<List<VideoSummary>>> =
        shortsFlow { remote.fetchMoreShorts() }

    private fun shortsFlow(
        fetch: suspend () -> List<RawVideoSummary>,
    ): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        val shorts = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "shorts request failed", e)
            emit(UiState.Error(ERROR_LOAD_SHORTS, e))
            return@flow
        }
        emit(UiState.Success(shorts.map { it.toDomain() }))
    }.flowOn(Dispatchers.IO)

    companion object {
        const val ERROR_LOAD_SHORTS = "Не вдалося завантажити шортси"
    }
}
```

- [ ] **Step 4: Звʼяжи в DI**

`app/src/main/java/com/ioi/youtube/di/RepositoryModule.kt` — додай імпорти
`com.ioi.youtube.data.repository.ShortsRepositoryImpl` і `com.ioi.youtube.domain.repository.ShortsRepository`,
а в тіло модуля:

```kotlin
    @Binds
    @Singleton
    abstract fun bindShortsRepository(impl: ShortsRepositoryImpl): ShortsRepository
```

- [ ] **Step 5: Запусти тести — мають пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*ShortsRepositoryImplTest*"`

Expected: `BUILD SUCCESSFUL`, 3 тести.

- [ ] **Step 6: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь. Hilt на `assembleDebug` перевіряє граф — помилка біндингу вилізе саме тут.

```bash
git add -A
git commit -m "feat: add shorts repository and use cases"
```

---

### Task 6.9: `ShortsViewModel`

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsViewModel.kt`
- Modify: `app/src/test/java/com/ioi/youtube/testutil/FakeRepositories.kt` (+`FakeShortsRepository`)
- Test: `app/src/test/java/com/ioi/youtube/presentation/shorts/ShortsViewModelTest.kt` (новий)

**Interfaces:**
- Consumes: `GetShortsUseCase`, `GetMoreShortsUseCase` (Task 6.8), `GetVideoStreamUseCase`.
- Produces:
  - `data class ShortsUiState(val shorts: UiState<List<VideoSummary>> = UiState.Loading, val streams: Map<String, UiState<VideoStream>> = emptyMap(), val loadingMore: Boolean = false)`
  - `ShortsViewModel.uiState: StateFlow<ShortsUiState>`, `retry()`, `loadStream(videoId)`, `retryStream(videoId)`, `loadMore()`

- [ ] **Step 1: Додай фейк репозиторію**

`app/src/test/java/com/ioi/youtube/testutil/FakeRepositories.kt` — додай імпорт
`com.ioi.youtube.domain.repository.ShortsRepository` і клас у кінець файлу:

```kotlin
class FakeShortsRepository : ShortsRepository {

    private val shorts = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    private val moreShorts = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)

    var shortsCalls = 0
        private set
    var moreShortsCalls = 0
        private set

    fun setShorts(state: UiState<List<VideoSummary>>) {
        shorts.value = state
    }

    fun setMoreShorts(state: UiState<List<VideoSummary>>) {
        moreShorts.value = state
    }

    override fun getShorts(): Flow<UiState<List<VideoSummary>>> {
        shortsCalls++
        return shorts
    }

    override fun getMoreShorts(): Flow<UiState<List<VideoSummary>>> {
        moreShortsCalls++
        return moreShorts
    }
}
```

- [ ] **Step 2: Напиши тести, що падають**

`app/src/test/java/com/ioi/youtube/presentation/shorts/ShortsViewModelTest.kt`:

```kotlin
package com.ioi.youtube.presentation.shorts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetMoreShortsUseCase
import com.ioi.youtube.domain.usecase.GetShortsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.testutil.FakeShortsRepository
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
class ShortsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val shorts = FakeShortsRepository()
    private val videos = FakeVideoRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ShortsViewModel(
        getShorts = GetShortsUseCase(shorts),
        getMoreShorts = GetMoreShortsUseCase(shorts),
        getVideoStream = GetVideoStreamUseCase(videos),
    )

    @Test
    fun `starts with loading then emits the feed`() = runTest(testDispatcher) {
        val feed = listOf(TestData.video("aaaaaaaaaaa"), TestData.video("bbbbbbbbbbb"))
        shorts.setShorts(UiState.Success(feed))

        createViewModel().uiState.test {
            assertThat(awaitItem().shorts).isEqualTo(UiState.Loading)
            assertThat(awaitItem().shorts).isEqualTo(UiState.Success(feed))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load stream fetches once per short and caches the result`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        videos.setStream(
            "aaaaaaaaaaa",
            UiState.Success(VideoStream("https://v/x.mp4", "720p", "MPEG-4")),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()
            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()

            assertThat(videos.streamCalls).isEqualTo(1)
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry stream drops the cached error and fetches again`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        videos.setStream("aaaaaaaaaaa", UiState.Error("Не вдалося отримати відеопотік"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadStream("aaaaaaaaaaa")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Error::class.java)

            videos.setStream(
                "aaaaaaaaaaa",
                UiState.Success(VideoStream("https://v/x.mp4", "720p", "MPEG-4")),
            )
            viewModel.retryStream("aaaaaaaaaaa")
            advanceUntilIdle()

            assertThat(videos.streamCalls).isEqualTo(2)
            assertThat(viewModel.uiState.value.streams["aaaaaaaaaaa"])
                .isInstanceOf(UiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more appends only previously unseen shorts`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        val second = TestData.video("bbbbbbbbbbb")
        val third = TestData.video("ccccccccccc")
        shorts.setShorts(UiState.Success(listOf(first, second)))
        shorts.setMoreShorts(UiState.Success(listOf(second, third)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second)))

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts)
                .isEqualTo(UiState.Success(listOf(first, second, third)))
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load more failure keeps the current feed`() = runTest(testDispatcher) {
        val first = TestData.video("aaaaaaaaaaa")
        shorts.setShorts(UiState.Success(listOf(first)))
        shorts.setMoreShorts(UiState.Error("Не вдалося завантажити шортси"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.shorts).isEqualTo(UiState.Success(listOf(first)))
            assertThat(viewModel.uiState.value.loadingMore).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry reloads the feed`() = runTest(testDispatcher) {
        shorts.setShorts(UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(shorts.shortsCalls).isEqualTo(1)

            viewModel.retry()
            advanceUntilIdle()

            assertThat(shorts.shortsCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*ShortsViewModelTest*"`

Expected: FAIL з помилкою компіляції — немає `ShortsViewModel`/`ShortsUiState`.

- [ ] **Step 4: Реалізуй `ShortsViewModel`**

`app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsViewModel.kt`:

```kotlin
package com.ioi.youtube.presentation.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.usecase.GetMoreShortsUseCase
import com.ioi.youtube.domain.usecase.GetShortsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsUiState(
    val shorts: UiState<List<VideoSummary>> = UiState.Loading,
    val streams: Map<String, UiState<VideoStream>> = emptyMap(),
    val loadingMore: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val getShorts: GetShortsUseCase,
    private val getMoreShorts: GetMoreShortsUseCase,
    private val getVideoStream: GetVideoStreamUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val feed = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    private val streams = MutableStateFlow<Map<String, UiState<VideoStream>>>(emptyMap())
    private val loadingMore = MutableStateFlow(false)
    private val knownIds = mutableSetOf<String>()

    val uiState: StateFlow<ShortsUiState> =
        combine(feed, streams, loadingMore) { feedState, streamStates, more ->
            ShortsUiState(shorts = feedState, streams = streamStates, loadingMore = more)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShortsUiState(),
        )

    init {
        viewModelScope.launch {
            refreshTrigger.flatMapLatest { getShorts() }.collect { state ->
                if (state is UiState.Success) knownIds += state.data.map { it.id }
                feed.value = state
            }
        }
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
    }

    /** Лiниво вантажить потiк шортса; повторний виклик для того ж id нiчого не робить. */
    fun loadStream(videoId: String) {
        if (videoId.isBlank() || streams.value.containsKey(videoId)) return
        streams.update { it + (videoId to UiState.Loading) }
        viewModelScope.launch {
            getVideoStream(videoId).collect { state -> streams.update { it + (videoId to state) } }
        }
    }

    fun retryStream(videoId: String) {
        streams.update { it - videoId }
        loadStream(videoId)
    }

    /** Новi шортси з iншої теми; помилка «гортання далi» не руйнує поточну стрiчку. */
    fun loadMore() {
        if (loadingMore.value) return
        loadingMore.value = true
        viewModelScope.launch {
            getMoreShorts().collect { state ->
                if (state is UiState.Success) {
                    val fresh = state.data.filterNot { it.id in knownIds }
                    if (fresh.isNotEmpty()) {
                        knownIds += fresh.map { it.id }
                        feed.update { current ->
                            val existing = (current as? UiState.Success)?.data.orEmpty()
                            UiState.Success(existing + fresh)
                        }
                    }
                }
            }
            loadingMore.value = false
        }
    }
}
```

- [ ] **Step 5: Запусти тести — мають пройти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*ShortsViewModelTest*"`

Expected: `BUILD SUCCESSFUL`, 6 тестів.

- [ ] **Step 6: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: add shorts view model with lazy stream loading"
```

---

### Task 6.10: Екран Shorts + навігація

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/Routes.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBar.kt`
- Modify: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsScreen.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/shorts/ShortVideoPlayer.kt`
- Test: `app/src/test/java/com/ioi/youtube/presentation/shorts/ShortsScreenTest.kt` (новий)
- Test: `app/src/test/java/com/ioi/youtube/presentation/navigation/RoutesTest.kt` (+1)
- Test: `app/src/test/java/com/ioi/youtube/presentation/navigation/IOITubeBottomBarTest.kt` (+1)
- Test: `app/src/test/java/com/ioi/youtube/presentation/navigation/OrientationTest.kt` (+1 рядок)

**Interfaces:**
- Consumes: `ShortsViewModel`/`ShortsUiState` (Task 6.9), `PlayerSource`/`playerMediaItem` (Tasks 6.3, 6.5), `CommonUi` (`LoadingBox`, `ErrorBox`, `EmptyBox`).
- Produces:
  - `Routes.SHORTS == "shorts"`
  - `@Composable fun ShortsRoute(viewModel: ShortsViewModel = hiltViewModel())`
  - `@Composable fun ShortsScreen(uiState: ShortsUiState, onRetry: () -> Unit, onLoadStream: (String) -> Unit, onRetryStream: (String) -> Unit, onLoadMore: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun ShortVideoPlayer(source: PlayerSource?, modifier: Modifier = Modifier)`

- [ ] **Step 1: Напиши тести/піни, що падають**

`ShortsScreenTest.kt`:

```kotlin
package com.ioi.youtube.presentation.shorts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class ShortsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        uiState: ShortsUiState,
        onRetry: () -> Unit = {},
        onLoadMore: () -> Unit = {},
    ) {
        composeRule.setContent {
            IOITubeTheme {
                ShortsScreen(
                    uiState = uiState,
                    onRetry = onRetry,
                    onLoadStream = {},
                    onRetryStream = {},
                    onLoadMore = onLoadMore,
                )
            }
        }
    }

    @Test
    fun `shows the title channel and views of the current short`() {
        setContent(
            ShortsUiState(
                shorts = UiState.Success(listOf(TestData.video("aaaaaaaaaaa", "Short one"))),
            ),
        )

        composeRule.onNodeWithText("Short one").assertExists()
        composeRule.onNodeWithText("@Test Channel").assertExists()
        composeRule.onNodeWithText("1.5M views").assertExists()
    }

    @Test
    fun `feed error shows retry`() {
        var retried = false
        setContent(
            uiState = ShortsUiState(shorts = UiState.Error("Не вдалося завантажити шортси")),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithText("Не вдалося завантажити шортси").assertExists()
        composeRule.onNodeWithText("Спробувати знову").performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `empty feed shows empty text`() {
        setContent(ShortsUiState(shorts = UiState.Success(emptyList())))

        composeRule.onNodeWithText("Тут поки що порожньо").assertExists()
    }

    @Test
    fun `approaching the end asks for more shorts`() {
        var moreAsked = false

        setContent(
            uiState = ShortsUiState(
                shorts = UiState.Success(listOf(TestData.video("aaaaaaaaaaa"))),
            ),
            onLoadMore = { moreAsked = true },
        )

        composeRule.waitForIdle()

        assertThat(moreAsked).isTrue()
    }
}
```

`RoutesTest.kt` — додай тест:

```kotlin
    @Test
    fun `shorts route`() {
        assertThat(Routes.SHORTS).isEqualTo("shorts")
    }
```

`IOITubeBottomBarTest.kt` — додай тест:

```kotlin
    @Test
    fun `shorts tab is present`() {
        composeRule.setContent {
            IOITubeTheme {
                IOITubeBottomBar(currentRoute = Routes.SHORTS, onNavigate = {})
            }
        }

        composeRule.onNodeWithContentDescription("Shorts").assertExists()
    }
```

`OrientationTest.kt` — у тесті `every other route stays portrait` додай:

```kotlin
        assertThat(orientationForRoute(Routes.SHORTS))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
```

- [ ] **Step 2: Запусти — має впасти**

Run: `source .agent-env.sh && ./gradlew :app:testDebugUnitTest --tests "*ShortsScreenTest*" --tests "*RoutesTest*"`

Expected: FAIL з помилкою компіляції — немає `Routes.SHORTS`, `ShortsScreen`, `ShortsUiState`.

- [ ] **Step 3: Додай маршрут і пункт меню**

`Routes.kt`:

```kotlin
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SHORTS = "shorts"
    const val PLAYER = "player/{videoId}"
    const val ARG_VIDEO_ID = "videoId"

    fun player(videoId: String) = "player/$videoId"
}
```

`IOITubeBottomBar.kt` — додай імпорт `androidx.compose.material.icons.filled.SmartDisplay`
і третій пункт між Головною та Пошуком:

```kotlin
        NavigationBarItem(
            selected = currentRoute == Routes.SHORTS,
            onClick = { onNavigate(Routes.SHORTS) },
            icon = { Icon(Icons.Filled.SmartDisplay, contentDescription = "Shorts") },
        )
```

- [ ] **Step 4: Створи екран шортсів і плеєр одного шортса**

`app/src/main/java/com/ioi/youtube/presentation/shorts/ShortVideoPlayer.kt`:

```kotlin
package com.ioi.youtube.presentation.shorts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ioi.youtube.presentation.player.PlayerSource
import com.ioi.youtube.presentation.player.playerMediaItem

/** Плеєр одного шортса: цикл, автоплей, тап по вiдео — пауза/вiдтворення. */
@Composable
fun ShortVideoPlayer(
    source: PlayerSource?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var paused by remember(source) { mutableStateOf(false) }
    val playable = source?.takeIf { it.url.isNotBlank() }

    val exoPlayer = remember(playable?.url) {
        playable?.let { video ->
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(playerMediaItem(video))
                playWhenReady = true
                prepare()
            }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    if (playable == null || exoPlayer == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Відео недоступне",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                paused = !paused
                if (paused) exoPlayer.pause() else exoPlayer.play()
            },
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
            }
        },
    )
}
```

`app/src/main/java/com/ioi/youtube/presentation/shorts/ShortsScreen.kt`:

```kotlin
package com.ioi.youtube.presentation.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
import com.ioi.youtube.presentation.player.toPlayerSource

@Composable
fun ShortsRoute(
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShortsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onLoadStream = viewModel::loadStream,
        onRetryStream = viewModel::retryStream,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun ShortsScreen(
    uiState: ShortsUiState,
    onRetry: () -> Unit,
    onLoadStream: (String) -> Unit,
    onRetryStream: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val feed = uiState.shorts) {
        is UiState.Loading -> LoadingBox(modifier)

        is UiState.Error -> ErrorBox(
            message = feed.message,
            onRetry = onRetry,
            modifier = modifier,
        )

        is UiState.Success -> {
            if (feed.data.isEmpty()) {
                EmptyBox(text = "Тут поки що порожньо", modifier = modifier)
            } else {
                val pagerState = rememberPagerState(pageCount = { feed.data.size })

                // Потiк потрiбен лише видимiй сторiнцi та наступнiй (щоб свайп був без паузи).
                LaunchedEffect(pagerState.currentPage, feed.data.size) {
                    val current = feed.data.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
                    onLoadStream(current.id)
                    feed.data.getOrNull(pagerState.currentPage + 1)?.let { onLoadStream(it.id) }
                    if (pagerState.currentPage >= feed.data.size - 2) onLoadMore()
                }

                VerticalPager(
                    state = pagerState,
                    modifier = modifier.fillMaxSize(),
                ) { page ->
                    val video = feed.data[page]
                    ShortPage(
                        video = video,
                        stream = uiState.streams[video.id] ?: UiState.Loading,
                        isActive = page == pagerState.currentPage,
                        onRetry = { onRetryStream(video.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortPage(
    video: VideoSummary,
    stream: UiState<VideoStream>,
    isActive: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isActive) {
            when (stream) {
                is UiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )

                is UiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stream.message, color = Color.White)
                    TextButton(onClick = onRetry) {
                        Text("Спробувати знову")
                    }
                }

                is UiState.Success -> ShortVideoPlayer(source = stream.data.toPlayerSource())
            }
        } else {
            // Сусiднi сторiнки не тримають плеєр — лише прев'ю.
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ShortOverlay(video = video, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ShortOverlay(video: VideoSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(16.dp),
    ) {
        Text(
            text = "@${video.uploaderName}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = video.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Formatters.formatViews(video.viewCount),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
```

- [ ] **Step 5: Підключи екран у навігацію**

`IOITubeApp.kt`:
- додай імпорт `com.ioi.youtube.presentation.shorts.ShortsRoute`,
- розшир нижній бар:

```kotlin
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.SEARCH ||
        currentRoute == Routes.SHORTS
```

- додай маршрут у `NavHost` (після `composable(Routes.HOME) { … }`):

```kotlin
            composable(Routes.SHORTS) {
                ShortsRoute()
            }
```

- [ ] **Step 6: Запусти тести — мають пройти**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest \
  --tests "*ShortsScreenTest*" --tests "*RoutesTest*" --tests "*IOITubeBottomBarTest*" --tests "*OrientationTest*"
```

Expected: `BUILD SUCCESSFUL` (4 + 6 + 3 + 2 тести).

- [ ] **Step 7: Повний прогін і коміт**

Run:
```bash
source .agent-env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, без нових падінь.

```bash
git add -A
git commit -m "feat: add shorts tab with a vertical swipe feed"
```

---

### Task 6.11: Живий смоук, документація, фінальна верифікація, APK

**Files:**
- Modify: `app/src/test/java/com/ioi/youtube/data/remote/NewPipeRemoteDataSourceSmokeTest.kt`
- Modify: `docs/manual-test-checklist.md`
- Modify: `docs/superpowers/specs/2026-10-18-phase-6-enhancements-design.md` (статус)
- Modify: `.superpowers/sdd/2026-10-14-ioitube-implementation/progress.md` (журнал фази 6)

**Interfaces:**
- Consumes: усе, що зробили таски 6.1–6.10.
- Produces: підтверджений живий прогiн, оновлена документація, свіжі APK у `dist/`.

- [ ] **Step 1: Додай живу перевірку шортсів і HLS**

`NewPipeRemoteDataSourceSmokeTest.kt` — додай імпорт `okhttp3.Request` і другий тест:

```kotlin
    @Test
    fun `shorts feed and hls manifest are alive`() = runBlocking {
        assumeTrue("IOITUBE_LIVE_TESTS=1 не встановлено — живий тест пропущено", live)

        ExtractorInitializer(OkHttpDownloader(client())).init()
        val dataSource = NewPipeRemoteDataSource()

        val shortsStart = System.currentTimeMillis()
        val shorts = dataSource.fetchShorts()
        val shortsMillis = System.currentTimeMillis() - shortsStart

        check(shorts.isNotEmpty()) { "стрiчка шортсiв порожня" }
        val brokenId = shorts.firstOrNull { it.id.length != 11 }
        check(brokenId == null) { "дивний id шортса: ${brokenId?.id}" }

        val stream = dataSource.fetchVideoStream("dQw4w9WgXcQ")
        val hlsUrl = stream.hlsUrl
        check(!hlsUrl.isNullOrBlank()) { "HLS-манифест вiдсутнiй" }

        val response = client().newCall(Request.Builder().url(hlsUrl).build()).execute()
        val body = response.body?.string().orEmpty()
        check(response.code == 200) { "HLS-манифест вiддав HTTP ${response.code}" }
        check(body.contains("#EXTM3U")) { "HLS-манифест без #EXTM3U" }
        val variants = body.lineSequence().count { it.contains("BANDWIDTH") }
        check(variants >= 2) { "у манифестi лише $variants варiантiв якостi" }

        println("live shorts: n=${shorts.size} (${shortsMillis} ms), hls variants=$variants")
        println("live shorts sample: " + shorts.take(5).joinToString(" | ") { it.title.take(40) })
    }
```

- [ ] **Step 2: Прожени живий смоук**

Run: `source .agent-env.sh && IOITUBE_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests "*SmokeTest*"`

Expected: `BUILD SUCCESSFUL` + у виводі рядки `live ok: …` і
`live shorts: n=… (… ms), hls variants=…` (n > 0, variants ≥ 2).
Якщо `hls variants` = 0 або HTTP не 200 — манифест змiнився: зафiксуй вивiд у журнал i не блокуй решту таски (на пристрої є вiдкат на progressive).

- [ ] **Step 3: Онови чекліст ручного тестування**

`docs/manual-test-checklist.md`:

- шапка (рядки 3–6):
```
Документ для прогону застосунку на реальному телефоні. Автоматизовано верифіковано:
**<N> unit-тести (0 failures, 1 skip)**, `assembleDebug` / `assembleRelease` (minified) / `assembleDebugAndroidTest` — усе зелене.
Живий прогiн проти YouTube (`IOITUBE_LIVE_TESTS=1`): стрiчка 30 вiдео, пошук, потiк + HLS-манифест з варiантами якостi, опис, 20 коментарiв, стрiчка шортсiв — ок.
Фаза 6: меню без підписів, згорнуті коментарі, вибір якості, поворот/повний екран, вкладка Shorts — сценарії 14–22.
```
(`<N>` — фактична кількість тестiв iз прогону Step 4.)
- рядок 10: `Орієнтація закріплена портретна.` → `Орієнтація: Головна/Пошук/Shorts — портрет, екран вiдео обертається вiльно.`
- рядок 11 (`| 11 | Поворот екрана | …`) → `| 11 | Поворот екрана | Поза плеєром нічого не змінюється (портрет); на плеєрі — сценарії 15–16 |`
- рядок 13 → `| 13 | Нижній бар | Три вкладки-іконки (Головна / Shorts / Пошук) без підписів, перемикання зберігає стан |`
- після рядка 13 додай:

```
| 14 | Відкрити відео → блок коментарів | «Коментарі (N)» + «Показати коментарі»; списку немає. Тап → список, «Згорнути» → знову зникає |
| 15 | Повернути телефон на плеєрі | Відео обертається разом із телефоном і **не** перезапускається з початку |
| 16 | Кнопка «На весь екран» | Ландшафт, системні бари зникають, лишається тільки відео; «Вийти з повного екрана» → назад у портрет |
| 17 | Меню якості у плеєрі | Кнопка «Авто» у правому верхньому куті; у меню реальні якості з бітрейтом (наприклад «1080p · 4.3 Мбіт/с») |
| 18 | Обрати «1080p» | Картинка різкіша після кількох секунд буферизації, відео не перезапускається |
| 19 | Обрати «Авто» назад | Якість повертається до адаптивної |
| 20 | Вкладка Shorts | Вертикальна стрічка справжніх шортсів (9:16), автоплей, цикл, тап — пауза/відтворення |
| 21 | Свайп угору у Shorts | Наступний шортс грає автоматично, попередній зупиняється |
| 22 | Авіарежим → вкладка Shorts | «Не вдалося завантажити шортси» + «Спробувати знову» (кешу шортсів немає — очікувано) |
```

- у розділі 3 (зони ризику) додай пункт:
```
8. **HLS і меню якості.** Якщо якості не перемикаються або меню зникло — у `adb logcat -s IOITube` буде `HLS playback failed, falling back to progressive`: відео грає 360p, це передбачений вiдкат. Повідом iз логом.
```
- у розділі 4 онови очікуваний вивід смоуку, додавши `live shorts: n=…, hls variants=…`.

- [ ] **Step 4: Онови статус спеки та журнал SDD**

- `docs/superpowers/specs/2026-10-18-phase-6-enhancements-design.md`: `**Status:** Draft (на ревʼю власника)` → `**Status:** Затверджено, реалізовано (6.1–6.11)`
- `.superpowers/sdd/2026-10-14-ioitube-implementation/progress.md`: додай секцію `## Фаза 6` з: списком тасків 6.1–6.11, фактами дослiдження (HLS 17 варiантiв, вкладки Shorts 48 елементiв, `FEshorts` = HTTP 400), кількістю тестiв, MD5 APK.

- [ ] **Step 5: Фінальна верифікація**

Run:
```bash
source .agent-env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL` в усіх трьох викликах; у `:app:testDebugUnitTest` — 0 failures (skip 1 — живий смоук).
`connectedDebugAndroidTest` **не запускаємо** — у цьому середовищі немає ні пристрою, ні емулятора.

- [ ] **Step 6: Збери APK у `dist/` і зафіксуй хеші**

```bash
cp app/build/outputs/apk/release/app-release.apk dist/IOITube-1.0-release.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/IOITube-1.0-debug.apk
md5sum dist/IOITube-1.0-release.apk dist/IOITube-1.0-debug.apk
```

Expected: два рядки MD5. Запиши їх у журнал SDD і в повідомлення власнику.

- [ ] **Step 7: Коміт і злиття у `main`**

```bash
git add -A
git commit -m "docs: update manual test checklist and spec status for phase 6"
git checkout main
git merge --no-ff feat/ioitube-phase-6 -m "merge: phase 6 (menu, comments, quality, rotation, shorts)"
git log --oneline -5
```

Expected: `main` мiстить усi комiти фази 6.

---

## Порядок виконання і перевірки

1. Таски йдуть строго 6.1 → 6.11. Кожна таска = окремий комiт, пiсля кожної — повний `testDebugUnitTest` (ловимо регресiї одразу, а не в кiнцi).
2. Для кожної таски роби ревью перед наступною: чи змiна в межах таски, чи видно, **чому** код такий, чи впаде хоч один тест, якщо змiнити ключову логiку.
3. Review Focus згори: пiсля тасок 6.5, 6.7, 6.9 перевiр, що кожен iз пʼяти входiв закритий тестом.
4. Не змiшуй таски в одному комiтi: якщо пiд час роботи знайшов стороннiй баг — окремий комiт `fix: …` пiсля завершення поточної таски.

## Що НЕ перевіряється тут (лишається на телефон власника)

- Справжнє вiдтворення HLS i перемикання якостi (немає нi пристрою, нi емулятора).
- Справжнiй поворот телефону i поведiнка системних барiв.
- Реакцiя на тапи/свайпи в шортсах.
- R8-мініфiкований release на пристрої (перевiряє власник; у нас — лише успішна збірка).
