# IOITube — Фаза 5: Поліш і реліз

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`

**Goal фази:** pull-to-refresh, iнструментальнi тести, повний зелений прогiн `testDebugUnitTest` + `assembleDebug`, release-бiлд iз мiнiфiкацiєю, README i чеклiст ручного тестування (спека §8).

**Architecture:** без змiн — лише поверхневi правки UI, тести, конфiгурацiя збiрки.

---

### Task 5.1: Pull-to-refresh на головнiй i в пошуку

**Files:**
- Modify: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreen.kt` (замiни повнiстю)
- Modify: `app/src/main/java/com/ioi/youtube/presentation/search/SearchScreen.kt` (замiни повнiстю)

**Interfaces:**
- Consumes: `HomeScreen`/`SearchScreen` (Tasks 3.3, 3.5), Material3 1.3.x `PullToRefreshBox`.
- Produces: той самий публiчний API (`HomeScreen(uiState, onVideoClick, onRetry, modifier)`, `SearchScreen(uiState, onQueryChange, onVideoClick, onRetry, modifier)`) — тести Фази 3 лишаються зеленими.

- [ ] **Step 1: Замiни `HomeScreen.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UiState<List<VideoSummary>>,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRetry()
        },
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
}
```

- [ ] **Step 2: Замiни `SearchScreen.kt`**

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.presentation.components.EmptyBox
import com.ioi.youtube.presentation.components.ErrorBox
import com.ioi.youtube.presentation.components.LoadingBox
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: UiState<SearchResults>,
    onQueryChange: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

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

        LaunchedEffect(uiState) {
            refreshing = false
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRetry()
            },
        ) {
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
                                item(key = "offline-banner") {
                                    com.ioi.youtube.presentation.components.OfflineBanner()
                                }
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
}
```

- [ ] **Step 3: Запусти тести Фази 3 — мають залишитися зеленими**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.presentation.home.*" --tests "com.ioi.youtube.presentation.search.*"`

Expected: PASS — публiчний API екранiв не змiнився.

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add pull-to-refresh to home and search"
```

---

### Task 5.2: Повний прогін тестів i iнструментальний тест

**Files:**
- Create: `app/src/androidTest/java/com/ioi/youtube/presentation/components/VideoCardInstrumentedTest.kt`

**Interfaces:**
- Consumes: усе, що накопичилося у Фазах 1–4.
- Produces: iнструментальний Compose-тест (запускається на пристрої/емуляторi) + процедура фiнальної верифiкацiї.

- [ ] **Step 1: Створи `VideoCardInstrumentedTest.kt`**

```kotlin
package com.ioi.youtube.presentation.components

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.presentation.theme.IOITubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoCardInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun rendersRealCardAndHandlesClick() {
        val video = VideoSummary(
            id = "dQw4w9WgXcQ",
            title = "Instrumented title",
            uploaderName = "Instrumented Channel",
            thumbnailUrl = "https://example.com/x.jpg",
            durationSeconds = 61,
            viewCount = 1500,
            uploadDate = "2 years ago",
        )
        var clicked: String? = null

        composeRule.setContent {
            IOITubeTheme {
                VideoCard(video = video, onClick = { clicked = it })
            }
        }

        composeRule.onNodeWithText("Instrumented title").assertExists()
        composeRule.onNodeWithText("Instrumented Channel · 1.5K views · 1:01").assertExists()
        composeRule.onNodeWithText("Instrumented title").performClick()

        assertEquals("dQw4w9WgXcQ", clicked)
    }
}
```

- [ ] **Step 2: Прогон усiх модульних тестiв**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, усi тести Фаз 1–4 зеленi (**85 tests, 0 failures, 1 skipped** — перевiрено контролером).

- [ ] **Step 3: Debug-бiлд**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` + файл `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Iнструментальнi тести (тiльки з пiдключеним пристроєм/емулятором)**

Run (з пристроєм): `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`.

БЕЗ пристрою/емулятора (як у цьому середовищi) обов'язкова замiна — зiбрати androidTest-APK:
`./gradlew :app:assembleDebugAndroidTest` → `BUILD SUCCESSFUL` + `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
`connectedDebugAndroidTest` у такому разi ПРОПУСКАЄТЬСЯ (крок лишається для локальної перевiрки).

- [ ] **Step 5: Коміт**

```bash
git add app/src/androidTest/java/com/ioi/youtube/presentation/components/VideoCardInstrumentedTest.kt
git commit -m "test: add instrumented video card test"
```

---

### Task 5.3: Release-бiлд + README + чеклiст

**Files:**
- Modify: `app/build.gradle.kts` (release-пiдпис + signing config)
- Replace: `app/proguard-rules.pro`
- Create: `keystore.properties.template`
- Create: `README.md`

**Interfaces:**
- Consumes: AppModule (Task 1.2).
- Produces: release-збiрка з `minifyEnabled`, signing з `keystore.properties` (файл у `.gitignore`), proguard-правила для NewPipe/Media3/OkHttp; README з командами; чеклiст ручного тестування.

- [ ] **Step 1: Створи `keystore.properties.template`**

```properties
# Скопiюй цей файл у keystore.properties (у .gitignore) i заповни значення.
# keystore створи: keytool -genkey -v -keystore ioitube.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ioitube
storeFile=ioitube.jks
storePassword=CHANGE_ME
keyAlias=ioitube
keyPassword=CHANGE_ME
```

- [ ] **Step 2: Онов `app/build.gradle.kts` двома точними правками**

**Правка A** — одразу пiсля блоку `plugins { ... }` (перед `android {`) додай:

```kotlin
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
```

**Правка B** — знайди у файлi секцiю вiд `android {` до її закривної дужки i замiни її повнiстю на:

```kotlin
android {
    namespace = "com.ioi.youtube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ioi.youtube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

Решта файлу (блок `dependencies`) залишається без змiн.


- [ ] **Step 3: Замiни `app/proguard-rules.pro`**

```proguard
# NewPipe Extractor — рефлексiя для сервiсiв
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp + Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
```

- [ ] **Step 4: Створи `README.md`** (файл у цiлому — блок мiж рядкiв ```` і ````):

````markdown
# IOITube

Android-клон YouTube на Jetpack Compose. Вiдео вiдтворюється власним плеєром (Media3/ExoPlayer);
реклама вiдсутня, бо потоки отримуються через NewPipe Extractor (без рекламних сегментiв).

## Стек

Kotlin · Jetpack Compose · MVVM + Clean Architecture · Hilt · Room · Media3 · Coil · NewPipe Extractor · Navigation Compose

## Збiрка i запуск

1. Вiдкрий проєкт в Android Studio (AGP 8.7.3, Gradle 8.11.1).
2. Запусти `app` на пристрої/емуляторi з API ≥ 26.

```bash
# усi модульнi тести
./gradlew testDebugUnitTest

# debug APK
./gradlew :app:assembleDebug

# iнструментальнi тести (потрiбен пристрiй)
./gradlew connectedDebugAndroidTest

# release APK (потрiбен keystore.properties, див. keystore.properties.template)
./gradlew :app:assembleRelease
```

## Структура

- `domain/` — моделi, iнтерфейси репозиторiїв, use cases (чистий Kotlin)
- `data/` — NewPipe Extractor, Room, реалiзацiї репозиторiїв
- `presentation/` — Compose-екрани, ViewModels, навiгацiя
- `di/` — Hilt-модулi

## Офлайн-режим

Головна стрiчка, пошук i метаданi вiдео кешуються в Room. Без iнтернету показується кеш iз банером «Немає з'єднання — показано кеш». Коментарi не кешуються.

## Доступ до релiзного пiдпису

Скопiюй `keystore.properties.template` у `keystore.properties`, створи `ioitube.jks`:

```bash
keytool -genkey -v -keystore ioitube.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ioitube
```
````

- [ ] **Step 5: Перевiр release-збiрку без пiдпису**

Run: `./gradlew :app:assembleRelease`

Expected: `BUILD SUCCESSFUL` (поки `keystore.properties` вiдсутнiй — APK буде непiдписаним; це норма для перевiрки мiнiфiкацiї).

- [ ] **Step 6: Ручне тестування (спека §8)**

Виконай на пристрої:

| # | Сценарiй | Очiкувано |
|---|---|---|
| 1 | Вiдкрити app | Стрiчка вiдео: прев'ю, назва, канал, перегляди, тривалiсть |
| 2 | Прокрутити стрiчку | Плавна прокрутка, прев'ю завантажуються (Coil) |
| 3 | Тапнути вiдео | Плеєр стартує, опис/дата/перегляди коректнi |
| 4 | Прокрутити вниз | Коментарi та «Рекомендацiї» |
| 5 | Тапнути рекомендацiю | Вiдкрилося iнше вiдео, «Назад» повертає |
| 6 | Пошук «kotlin» | Сiтка результатiв через ~0.3 с |
| 7 | Пустий пошук | «Нiчого не знайдено», без креша |
| 8 | Авiарежим → головна | Банер «Немає з'єднання — показано кеш» + кешованi вiдео |
| 9 | Авiарежим → нове вiдео | «Не вдалося…» + «Спробувати знову» вiдновлює |
| 10 | Згорнути/розгорнути app на плеєрi | Пауза/продовження |
| 11 | Поворот екрана | Екран вiдновлюється (портретна орiєнтацiя закрiплена) |

- [ ] **Step 7: Комміт**

```bash
git add -A
git commit -m "build: add release signing, proguard rules and readme"
```

---

## Definition of Done (финал)

- `./gradlew testDebugUnitTest` — зелений.
- `./gradlew :app:assembleDebug` i `:app:assembleRelease` — зеленi.
- Усi 11 пунктiв ручного чеклiсту — виконано.
- Майстер-план `2026-10-14-ioitube-implementation.md` → Definition of Done досягнуто.