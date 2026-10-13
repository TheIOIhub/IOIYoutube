# IOITube — Фаза 1: Фундамент і домен

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Майстер-план:** `2026-10-14-ioitube-implementation.md` · **Спека:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`

**Goal фази:** порожній проєкт перетворюється на застосунок, що збирається (`assembleDebug` зелений), із Compose-темою, навігаційним скелетом і повністю протестованим доменним шаром (моделі, `UiState`, інтерфейси репозиторіїв, fakes, 5 use cases зі спеки §9).

**Architecture:** тільки `domain/` (чистий Kotlin) + `presentation/` скелети + Hilt. Жодного коду `data/` у цій фазі.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12.01, Hilt 2.54, Navigation Compose 2.8.5, JUnit4 + Turbine.

**Версії зафіксовані в майстер-плані.** Правила роботи з локальною моделлю — в майстер-плані (одна таска = один повний промпт; коміт після кожної таски).

---

### Task 1.1: Скелет проєкту (Gradle + git)

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle.properties`
- Modify: `build.gradle.kts` (кореневий)
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Create: `.gitignore`
- Delete: `gradle/libs.versions.toml` (якщо майстер створив)

**Interfaces:**
- Consumes: нічого (перша таска).
- Produces: робочий Gradle-проєкт із підключеними репозиторіями `google()`, `mavenCentral()`, `jitpack.io`; кореневий `build.gradle.kts` з усіма плагінами без версій; wrapper 8.11.1.

- [ ] **Step 1: Створи проєкт майстром Android Studio**

File → New → New Project → **Empty Activity**. Параметри:
- Name: `IOIYoutube`
- Package name: `com.ioi.youtube`
- Save location: `/home/the101hub/Projects/IOIYoutube`
- Language: Kotlin · Build configuration language: Kotlin DSL (build.gradle.kts)
- Minimum SDK: **API 26 (Android 8.0, Oreo)**

- [ ] **Step 2: Заміни кореневий `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "IOIYoutube"
include(":app")
```

- [ ] **Step 3: Заміни `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

Якщо файл містить `android.builtInKotlin` або інші прапорці AGP 9+ — видали їх усі, залиш тільки вміст вище.

- [ ] **Step 4: Заміни кореневий `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.54" apply false
}
```

- [ ] **Step 5: Версія Gradle wrapper**

Відкрий `gradle/wrapper/gradle-wrapper.properties`, зрівняй `distributionUrl` із:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

- [ ] **Step 6: Створи `.gitignore`**

```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
keystore.properties
*.jks
*.keystore
```

- [ ] **Step 7: Видали `gradle/libs.versions.toml`, якщо він існує**

```bash
rm -f gradle/libs.versions.toml
```

- [ ] **Step 8: Коміт**

```bash
git add -A && git status --short
git commit -m "chore: replace Android Studio scaffold with pinned Gradle setup"
```

---

### Task 1.2: app-модуль, маніфест, ресурси

**Files:**
- Replace: `app/build.gradle.kts`
- Replace: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/proguard-rules.pro`

**Interfaces:**
- Consumes: кореневий `build.gradle.kts` з Task 1.1 (плагіни).
- Produces: залежності проєкту; `AndroidManifest` з `INTERNET` і посиланнями на `.IOIYoutubeApplication` (створюється в Task 1.5) і `.MainActivity`; тема `Theme.IOIYoutube`.

- [ ] **Step 1: Заміни `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-android-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.teamnewpipe:NewPipeExtractor:0.26.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 2: Заміни `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".IOIYoutubeApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.IOIYoutube">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 3: Створи `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">IOITube</string>
</resources>
```

- [ ] **Step 4: Створи `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.IOIYoutube" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 5: Створи `app/proguard-rules.pro`**

```proguard
# Нині порожньо — правила для release додаються у Фазі 5 (Task 5.3).
```

- [ ] **Step 6: Перевір резолв залежностей**

Run: `./gradlew :app:dependencies --configuration debugCompileClasspath | tail -5`

Expected: у виводі немає рядків `FAILED`; Gradle завантажує залежності й завершується `BUILD SUCCESSFUL`. (Перший запуск може тривати кілька хвилин.)

- [ ] **Step 7: Коміт**

```bash
git add -A
git commit -m "build: configure app module with compose, hilt, room, media3, newpipe deps"
```

---

### Task 1.3: Compose-тема

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/theme/Color.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/theme/Type.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/theme/Theme.kt`
- Delete: `app/src/main/java/com/ioi/youtube/ui/theme/` (папка від майстра, якщо є)

**Interfaces:**
- Consumes: нічого.
- Produces: `IOITubeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`, `IOITubeTypography`, кольори `YouTubeRed`, `DarkBackground`, `DarkSurface`, `LightBackground`, `LightSurface`, `SubtleGray`.

- [ ] **Step 1: Створи `Color.kt`**

```kotlin
package com.ioi.youtube.presentation.theme

import androidx.compose.ui.graphics.Color

val YouTubeRed = Color(0xFFFF0000)
val DarkBackground = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF1F1F1F)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF9F9F9)
val SubtleGray = Color(0xFF606060)
```

- [ ] **Step 2: Створи `Type.kt`**

```kotlin
package com.ioi.youtube.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IOITubeTypography = Typography(
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)
```

- [ ] **Step 3: Створи `Theme.kt`**

```kotlin
package com.ioi.youtube.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = YouTubeRed,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColors = lightColorScheme(
    primary = YouTubeRed,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun IOITubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = IOITubeTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Видали старі theme-файли майстра**

```bash
rm -rf app/src/main/java/com/ioi/youtube/ui/theme
```

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add youtube-like compose theme"
```

---

### Task 1.4: Навігаційний скелет

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/presentation/navigation/Routes.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/navigation/IOITubeApp.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/home/HomeScreenPlaceholder.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/search/SearchScreenPlaceholder.kt`
- Create: `app/src/main/java/com/ioi/youtube/presentation/player/PlayerScreenPlaceholder.kt`

**Interfaces:**
- Consumes: `IOITubeTheme` (Task 1.3).
- Produces: `Routes` (`HOME`, `SEARCH`, `PLAYER`, `ARG_VIDEO_ID`, `player(videoId)`), `IOITubeApp(navController: NavHostController = rememberNavController())` — верхньорівневий Composable застосунку.

- [ ] **Step 1: Створи `Routes.kt`**

```kotlin
package com.ioi.youtube.presentation.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player/{videoId}"
    const val ARG_VIDEO_ID = "videoId"

    fun player(videoId: String) = "player/$videoId"
}
```

- [ ] **Step 2: Створи `IOITubeApp.kt`**

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
import com.ioi.youtube.presentation.home.HomeScreenPlaceholder
import com.ioi.youtube.presentation.player.PlayerScreenPlaceholder
import com.ioi.youtube.presentation.search.SearchScreenPlaceholder

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
            composable(Routes.HOME) { HomeScreenPlaceholder() }
            composable(Routes.SEARCH) { SearchScreenPlaceholder() }
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

- [ ] **Step 3: Створи `HomeScreenPlaceholder.kt`**

```kotlin
package com.ioi.youtube.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreenPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Home")
    }
}
```

- [ ] **Step 4: Створи `SearchScreenPlaceholder.kt`**

```kotlin
package com.ioi.youtube.presentation.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SearchScreenPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Search")
    }
}
```

- [ ] **Step 5: Створи `PlayerScreenPlaceholder.kt`**

```kotlin
package com.ioi.youtube.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlayerScreenPlaceholder(videoId: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Player: $videoId")
    }
}
```

- [ ] **Step 6: Коміт**

```bash
git add -A
git commit -m "feat: add navigation skeleton with bottom bar"
```

---

### Task 1.5: Hilt + MainActivity + перший повний білд

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/IOIYoutubeApplication.kt`
- Replace: `app/src/main/java/com/ioi/youtube/MainActivity.kt`

**Interfaces:**
- Consumes: `IOITubeApp` (Task 1.4), `IOITubeTheme` (Task 1.3).
- Produces: `IOIYoutubeApplication : Application` з `@HiltAndroidApp` (інжектиться з Фази 2), `MainActivity` з `@AndroidEntryPoint` — єдиний Activity застосунку.

- [ ] **Step 1: Створи `IOIYoutubeApplication.kt`**

```kotlin
package com.ioi.youtube

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IOIYoutubeApplication : Application()
```

- [ ] **Step 2: Заміни `MainActivity.kt`**

```kotlin
package com.ioi.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ioi.youtube.presentation.navigation.IOITubeApp
import com.ioi.youtube.presentation.theme.IOITubeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IOITubeTheme {
                IOITubeApp()
            }
        }
    }
}
```

- [ ] **Step 3: Перевір збірку**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`. Перший білд з KSP/Hilt і Compose — довгий (5–15 хв).

- [ ] **Step 4: Перевір на пристрої/емуляторі**

Запусти `app` з Android Studio. Очікується: білий/темний екран з написом `Home` і нижньою панеллю `Home | Search`; тап по `Search` — напис `Search`.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: wire hilt application and main activity"
```

---

### Task 1.6: Домен-моделі + Formatters

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/model/VideoSummary.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/model/VideoDetails.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/model/Comment.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/model/VideoStream.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/model/SearchResults.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/util/Formatters.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/util/FormattersTest.kt`

**Interfaces:**
- Consumes: нічого.
- Produces: `VideoSummary(id: String, title: String, uploaderName: String, thumbnailUrl: String, durationSeconds: Long, viewCount: Long, uploadDate: String?)`; `VideoDetails(..., recommendedVideos: List<VideoSummary> = emptyList())`; `Comment(id, authorName, authorAvatarUrl: String?, text, likeCount: Long, publishedTime: String?, heartedByUploader: Boolean, pinned: Boolean)`; `VideoStream(url: String, resolution: String, format: String)`; `SearchResults(query: String, videos: List<VideoSummary>)`; `Formatters.formatViews/formatCount/formatDuration`.

- [ ] **Step 1: Напиши падаючий тест `FormattersTest.kt`**

```kotlin
package com.ioi.youtube.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatViews formats view counts`() {
        assertEquals("No views", Formatters.formatViews(0))
        assertEquals("No views", Formatters.formatViews(-1))
        assertEquals("999 views", Formatters.formatViews(999))
        assertEquals("1.5K views", Formatters.formatViews(1_500))
        assertEquals("12.3K views", Formatters.formatViews(12_300))
        assertEquals("1.5M views", Formatters.formatViews(1_500_000))
        assertEquals("2.3B views", Formatters.formatViews(2_300_000_000))
    }

    @Test
    fun `formatCount formats plain counts without suffix`() {
        assertEquals("0", Formatters.formatCount(-1))
        assertEquals("0", Formatters.formatCount(0))
        assertEquals("42", Formatters.formatCount(42))
        assertEquals("1.5K", Formatters.formatCount(1_500))
        assertEquals("3.0M", Formatters.formatCount(3_000_000))
    }

    @Test
    fun `formatDuration formats mm ss and h mm ss`() {
        assertEquals("0:00", Formatters.formatDuration(0))
        assertEquals("0:00", Formatters.formatDuration(-5))
        assertEquals("1:15", Formatters.formatDuration(75))
        assertEquals("12:34", Formatters.formatDuration(754))
        assertEquals("1:00:00", Formatters.formatDuration(3600))
        assertEquals("1:02:05", Formatters.formatDuration(3725))
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.util.FormattersTest"`

Expected: FAIL, компіляція тесту не проходить (`Unresolved reference: Formatters`).

- [ ] **Step 3: Створи `VideoSummary.kt`**

```kotlin
package com.ioi.youtube.domain.model

data class VideoSummary(
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadDate: String?,
)
```

- [ ] **Step 4: Створи `VideoDetails.kt`**

```kotlin
package com.ioi.youtube.domain.model

data class VideoDetails(
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
    val recommendedVideos: List<VideoSummary> = emptyList(),
)
```

- [ ] **Step 5: Створи `Comment.kt`**

```kotlin
package com.ioi.youtube.domain.model

data class Comment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long,
    val publishedTime: String?,
    val heartedByUploader: Boolean,
    val pinned: Boolean,
)
```

- [ ] **Step 6: Створи `VideoStream.kt`**

```kotlin
package com.ioi.youtube.domain.model

data class VideoStream(
    val url: String,
    val resolution: String,
    val format: String,
)
```

- [ ] **Step 7: Створи `SearchResults.kt`**

```kotlin
package com.ioi.youtube.domain.model

data class SearchResults(
    val query: String,
    val videos: List<VideoSummary>,
)
```

- [ ] **Step 8: Створи `Formatters.kt`**

```kotlin
package com.ioi.youtube.domain.util

object Formatters {

    fun formatCount(count: Long): String = when {
        count < 0 -> "0"
        count < 1_000 -> "$count"
        count < 1_000_000 -> "%.1fK".format(count / 1_000.0)
        count < 1_000_000_000 -> "%.1fM".format(count / 1_000_000.0)
        else -> "%.1fB".format(count / 1_000_000_000.0)
    }

    fun formatViews(count: Long): String =
        if (count <= 0) "No views" else "${formatCount(count)} views"

    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = if (totalSeconds < 0) 0 else totalSeconds
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
```

- [ ] **Step 9: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.util.FormattersTest"`

Expected: PASS (3 тести).

- [ ] **Step 10: Коміт**

```bash
git add -A
git commit -m "feat: add domain models and formatters"
```

---

### Task 1.7: UiState

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/model/UiState.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/model/UiStateTest.kt`

**Interfaces:**
- Consumes: нічого.
- Produces: `sealed interface UiState<out T>` з `Loading: UiState<Nothing>`, `Success<T>(data: T, fromCache: Boolean = false)`, `Error(message: String, cause: Throwable? = null)`; extension `fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R>`.

- [ ] **Step 1: Напиши падаючий тест `UiStateTest.kt`**

```kotlin
package com.ioi.youtube.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {

    @Test
    fun `map transforms success data and keeps fromCache`() {
        val state: UiState<Int> = UiState.Success(2, fromCache = true)

        val mapped = state.map { it * 10 }

        assertEquals(UiState.Success(20, fromCache = true), mapped)
    }

    @Test
    fun `map passes through error and loading`() {
        val error: UiState<Int> = UiState.Error("boom", IllegalStateException("x"))

        assertEquals(error, error.map { it + 1 })
        assertEquals(UiState.Loading, (UiState.Loading as UiState<Int>).map { it + 1 })
    }
}
```

- [ ] **Step 2: Запусти тест — має впасти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.model.UiStateTest"`

Expected: FAIL (`Unresolved reference: UiState`).

- [ ] **Step 3: Створи `UiState.kt`**

```kotlin
package com.ioi.youtube.domain.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(
        val data: T,
        val fromCache: Boolean = false,
    ) : UiState<T>

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : UiState<Nothing>
}

fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Success -> UiState.Success(transform(data), fromCache)
    is UiState.Error -> this
    UiState.Loading -> UiState.Loading
}
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.model.UiStateTest"`

Expected: PASS (2 тести).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add UiState sealed interface with map extension"
```

---

### Task 1.8: Інтерфейси репозиторіїв

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/repository/VideoRepository.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/repository/SearchRepository.kt`
- Create: `app/src/main/java/com/ioi/youtube/domain/repository/CommentRepository.kt`

**Interfaces:**
- Consumes: `UiState`, `VideoSummary`, `VideoDetails`, `VideoStream`, `Comment`, `SearchResults` (Task 1.6–1.7).
- Produces:
  - `VideoRepository.getHomeFeed(): Flow<UiState<List<VideoSummary>>>` · `getVideoDetails(videoId: String): Flow<UiState<VideoDetails>>` · `getVideoStream(videoId: String): Flow<UiState<VideoStream>>`
  - `SearchRepository.search(query: String): Flow<UiState<SearchResults>>`
  - `CommentRepository.getComments(videoId: String): Flow<UiState<List<Comment>>>`

- [ ] **Step 1: Створи `VideoRepository.kt`**

```kotlin
package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getHomeFeed(): Flow<UiState<List<VideoSummary>>>
    fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>>
    fun getVideoStream(videoId: String): Flow<UiState<VideoStream>>
}
```

- [ ] **Step 2: Створи `SearchRepository.kt`**

```kotlin
package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun search(query: String): Flow<UiState<SearchResults>>
}
```

- [ ] **Step 3: Створи `CommentRepository.kt`**

```kotlin
package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import kotlinx.coroutines.flow.Flow

interface CommentRepository {
    fun getComments(videoId: String): Flow<UiState<List<Comment>>>
}
```

- [ ] **Step 4: Перевір компіляцію**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add repository interfaces"
```

---

### Task 1.9: Test fakes + тестові дані

**Files:**
- Create: `app/src/test/java/com/ioi/youtube/testutil/TestData.kt`
- Create: `app/src/test/java/com/ioi/youtube/testutil/FakeRepositories.kt`

**Interfaces:**
- Consumes: інтерфейси з Task 1.8, моделі з Task 1.6.
- Produces: `TestData.video(id: String = "dQw4w9WgXcQ", title: String = "Video $id"): VideoSummary`, `TestData.details(id: String = ...): VideoDetails`, `TestData.comments(): List<Comment>`; `FakeVideoRepository` (з лічильниками `feedCalls/detailsCalls/streamCalls` і сетерами `setFeed/setDetails/setStream`), `FakeSearchRepository` (`searchCalls`, `setResults`), `FakeCommentRepository` (`commentsCalls`, `setComments`).

- [ ] **Step 1: Створи `TestData.kt`**

```kotlin
package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoSummary

object TestData {

    fun video(id: String = "dQw4w9WgXcQ", title: String = "Video $id"): VideoSummary =
        VideoSummary(
            id = id,
            title = title,
            uploaderName = "Test Channel",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationSeconds = 212,
            viewCount = 1_500_000,
            uploadDate = "2 years ago",
        )

    fun details(id: String = "dQw4w9WgXcQ"): VideoDetails =
        VideoDetails(
            id = id,
            title = "Video $id",
            uploaderName = "Test Channel",
            uploaderAvatarUrl = "https://example.com/avatar.jpg",
            description = "Description of video $id",
            viewCount = 1_500_000,
            likeCount = 42_000,
            uploadDate = "2 years ago",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationSeconds = 212,
            recommendedVideos = listOf(video("aaaaaaaaaaa"), video("bbbbbbbbbbb")),
        )

    fun comments(): List<Comment> = listOf(
        Comment(
            id = "c1",
            authorName = "Alice",
            authorAvatarUrl = null,
            text = "First!",
            likeCount = 150,
            publishedTime = "1 day ago",
            heartedByUploader = true,
            pinned = true,
        ),
        Comment(
            id = "c2",
            authorName = "Bob",
            authorAvatarUrl = null,
            text = "Nice video",
            likeCount = 42,
            publishedTime = "2 days ago",
            heartedByUploader = false,
            pinned = false,
        ),
    )
}
```

- [ ] **Step 2: Створи `FakeRepositories.kt`**

```kotlin
package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.CommentRepository
import com.ioi.youtube.domain.repository.SearchRepository
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVideoRepository : VideoRepository {

    private val feed = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    private val details = mutableMapOf<String, MutableStateFlow<UiState<VideoDetails>>>()
    private val streams = mutableMapOf<String, MutableStateFlow<UiState<VideoStream>>>()

    var feedCalls = 0
        private set
    var detailsCalls = 0
        private set
    var streamCalls = 0
        private set

    fun setFeed(state: UiState<List<VideoSummary>>) {
        feed.value = state
    }

    fun setDetails(videoId: String, state: UiState<VideoDetails>) {
        details.getOrPut(videoId) { MutableStateFlow(state) }.value = state
    }

    fun setStream(videoId: String, state: UiState<VideoStream>) {
        streams.getOrPut(videoId) { MutableStateFlow(state) }.value = state
    }

    override fun getHomeFeed(): Flow<UiState<List<VideoSummary>>> {
        feedCalls++
        return feed
    }

    override fun getVideoDetails(videoId: String): Flow<UiState<VideoDetails>> {
        detailsCalls++
        return details.getOrPut(videoId) { MutableStateFlow(UiState.Loading) }
    }

    override fun getVideoStream(videoId: String): Flow<UiState<VideoStream>> {
        streamCalls++
        return streams.getOrPut(videoId) { MutableStateFlow(UiState.Loading) }
    }
}

class FakeSearchRepository : SearchRepository {

    private val results = MutableStateFlow<UiState<SearchResults>>(UiState.Loading)

    var searchCalls = 0
        private set

    fun setResults(state: UiState<SearchResults>) {
        results.value = state
    }

    override fun search(query: String): Flow<UiState<SearchResults>> {
        searchCalls++
        return results
    }
}

class FakeCommentRepository : CommentRepository {

    private val comments = MutableStateFlow<UiState<List<Comment>>>(UiState.Loading)

    var commentsCalls = 0
        private set

    fun setComments(state: UiState<List<Comment>>) {
        comments.value = state
    }

    override fun getComments(videoId: String): Flow<UiState<List<Comment>>> {
        commentsCalls++
        return comments
    }
}
```

- [ ] **Step 3: Перевір компіляцію тестів**

Run: `./gradlew :app:compileDebugUnitTestKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "test: add fakes and fixtures for repository interfaces"
```

---

### Task 1.10: GetHomeFeedUseCase

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetHomeFeedUseCase.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/usecase/GetHomeFeedUseCaseTest.kt`

**Interfaces:**
- Consumes: `VideoRepository.getHomeFeed()` (Task 1.8), `FakeVideoRepository` (Task 1.9), `TestData` (Task 1.9).
- Produces: `class GetHomeFeedUseCase @Inject constructor(videoRepository: VideoRepository)` з `operator fun invoke(): Flow<UiState<List<VideoSummary>>>`.

- [ ] **Step 1: Створи `GetHomeFeedUseCase.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHomeFeedUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> =
        videoRepository.getHomeFeed()
}
```

- [ ] **Step 2: Напиши тест `GetHomeFeedUseCaseTest.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.VideoRepository
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetHomeFeedUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetHomeFeedUseCase(repository)

    @Test
    fun `forwards feed state from repository`() = runTest {
        val videos = listOf(TestData.video(), TestData.video("abcdefghijk"))
        repository.setFeed(UiState.Success(videos))

        useCase().test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(videos))
            awaitComplete()
        }
    }

    @Test
    fun `forwards loading state from repository`() = runTest {
        useCase().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            awaitComplete()
        }
    }

    @Test
    fun `delegates to repository exactly once per invocation`() {
        val mockedRepository = mockk<VideoRepository> {
            every { getHomeFeed() } returns flowOf(listOf(TestData.video()))
        }

        GetHomeFeedUseCase(mockedRepository)().first()

        verify(exactly = 1) { mockedRepository.getHomeFeed() }
    }
}
```

- [ ] **Step 3: Додай залежність Truth (один раз)**

В `app/build.gradle.kts` у блок `dependencies` додай рядок:

```kotlin
    testImplementation("com.google.truth:truth:1.4.4")
```

- [ ] **Step 4: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.usecase.GetHomeFeedUseCaseTest"`

Expected: PASS (3 тести, включно з MockK-перевiркою делегування — спека §8).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add GetHomeFeedUseCase with tests"
```

---

### Task 1.11: SearchVideosUseCase

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/SearchVideosUseCase.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/usecase/SearchVideosUseCaseTest.kt`

**Interfaces:**
- Consumes: `SearchRepository.search(query)` (Task 1.8), `FakeSearchRepository` (Task 1.9).
- Produces: `class SearchVideosUseCase @Inject constructor(searchRepository: SearchRepository)` з `operator fun invoke(query: String): Flow<UiState<SearchResults>>`.

- [ ] **Step 1: Створи `SearchVideosUseCase.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchVideosUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    operator fun invoke(query: String): Flow<UiState<SearchResults>> =
        searchRepository.search(query)
}
```

- [ ] **Step 2: Напиши тест `SearchVideosUseCaseTest.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.SearchResults
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeSearchRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchVideosUseCaseTest {

    private val repository = FakeSearchRepository()
    private val useCase = SearchVideosUseCase(repository)

    @Test
    fun `forwards search results from repository`() = runTest {
        val results = SearchResults(query = "kotlin", videos = listOf(TestData.video()))
        repository.setResults(UiState.Success(results))

        useCase("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(results))
            awaitComplete()
        }
    }

    @Test
    fun `forwards error state from repository`() = runTest {
        repository.setResults(UiState.Error("Search failed"))

        useCase("kotlin").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("Search failed"))
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.usecase.SearchVideosUseCaseTest"`

Expected: PASS (2 тести).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add SearchVideosUseCase with tests"
```

---

### Task 1.12: GetVideoDetailsUseCase

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetVideoDetailsUseCase.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/usecase/GetVideoDetailsUseCaseTest.kt`

**Interfaces:**
- Consumes: `VideoRepository.getVideoDetails(videoId)` (Task 1.8), `FakeVideoRepository` (Task 1.9).
- Produces: `class GetVideoDetailsUseCase @Inject constructor(videoRepository: VideoRepository)` з `operator fun invoke(videoId: String): Flow<UiState<VideoDetails>>`.

- [ ] **Step 1: Створи `GetVideoDetailsUseCase.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideoDetailsUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    operator fun invoke(videoId: String): Flow<UiState<VideoDetails>> =
        videoRepository.getVideoDetails(videoId)
}
```

- [ ] **Step 2: Напиши тест `GetVideoDetailsUseCaseTest.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeVideoRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetVideoDetailsUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetVideoDetailsUseCase(repository)

    @Test
    fun `forwards details for requested video id`() = runTest {
        val details = TestData.details("abcdefghijk")
        repository.setDetails("abcdefghijk", UiState.Success(details))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(details))
            awaitComplete()
        }
    }

    @Test
    fun `forwards error state`() = runTest {
        repository.setDetails("abcdefghijk", UiState.Error("Video unavailable"))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("Video unavailable"))
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.usecase.GetVideoDetailsUseCaseTest"`

Expected: PASS (2 тести).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add GetVideoDetailsUseCase with tests"
```

---

### Task 1.13: GetCommentsUseCase

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetCommentsUseCase.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/usecase/GetCommentsUseCaseTest.kt`

**Interfaces:**
- Consumes: `CommentRepository.getComments(videoId)` (Task 1.8), `FakeCommentRepository` (Task 1.9).
- Produces: `class GetCommentsUseCase @Inject constructor(commentRepository: CommentRepository)` з `operator fun invoke(videoId: String): Flow<UiState<List<Comment>>>`.

- [ ] **Step 1: Створи `GetCommentsUseCase.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.CommentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCommentsUseCase @Inject constructor(
    private val commentRepository: CommentRepository,
) {
    operator fun invoke(videoId: String): Flow<UiState<List<Comment>>> =
        commentRepository.getComments(videoId)
}
```

- [ ] **Step 2: Напиши тест `GetCommentsUseCaseTest.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeCommentRepository
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetCommentsUseCaseTest {

    private val repository = FakeCommentRepository()
    private val useCase = GetCommentsUseCase(repository)

    @Test
    fun `forwards comments from repository`() = runTest {
        val comments = TestData.comments()
        repository.setComments(UiState.Success(comments))

        useCase("dQw4w9WgXcQ").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(comments))
            awaitComplete()
        }
    }

    @Test
    fun `forwards loading state`() = runTest {
        useCase("dQw4w9WgXcQ").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.usecase.GetCommentsUseCaseTest"`

Expected: PASS (2 тести).

- [ ] **Step 4: Коміт**

```bash
git add -A
git commit -m "feat: add GetCommentsUseCase with tests"
```

---

### Task 1.14: GetVideoStreamUseCase

**Files:**
- Create: `app/src/main/java/com/ioi/youtube/domain/usecase/GetVideoStreamUseCase.kt`
- Test: `app/src/test/java/com/ioi/youtube/domain/usecase/GetVideoStreamUseCaseTest.kt`

**Interfaces:**
- Consumes: `VideoRepository.getVideoStream(videoId)` (Task 1.8), `FakeVideoRepository` (Task 1.9).
- Produces: `class GetVideoStreamUseCase @Inject constructor(videoRepository: VideoRepository)` з `operator fun invoke(videoId: String): Flow<UiState<VideoStream>>`.

- [ ] **Step 1: Створи `GetVideoStreamUseCase.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideoStreamUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    operator fun invoke(videoId: String): Flow<UiState<VideoStream>> =
        videoRepository.getVideoStream(videoId)
}
```

- [ ] **Step 2: Напиши тест `GetVideoStreamUseCaseTest.kt`**

```kotlin
package com.ioi.youtube.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.testutil.FakeVideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetVideoStreamUseCaseTest {

    private val repository = FakeVideoRepository()
    private val useCase = GetVideoStreamUseCase(repository)

    @Test
    fun `forwards stream from repository`() = runTest {
        val stream = VideoStream(url = "https://rr3---sn-abc.googlevideo.com/videoplayback", resolution = "720p", format = "MPEG-4")
        repository.setStream("abcdefghijk", UiState.Success(stream))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Success(stream))
            awaitComplete()
        }
    }

    @Test
    fun `forwards error state`() = runTest {
        repository.setStream("abcdefghijk", UiState.Error("No stream available"))

        useCase("abcdefghijk").test {
            assertThat(awaitItem()).isEqualTo(UiState.Error("No stream available"))
            awaitComplete()
        }
    }
}
```

- [ ] **Step 3: Запусти тест — має пройти**

Run: `./gradlew testDebugUnitTest --tests "com.ioi.youtube.domain.usecase.GetVideoStreamUseCaseTest"`

Expected: PASS (2 тести).

- [ ] **Step 4: Фінальна перевірка фази**

Run: `./gradlew testDebugUnitTest`

Expected: усі тести фази зелені (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Коміт**

```bash
git add -A
git commit -m "feat: add GetVideoStreamUseCase with tests"
```

---

## Review Focus фази 1

1. `Formatters` із від'ємними значеннями (`-1`, `-5`) — «No views», «0:00», без креша → Task 1.6.
2. `UiState.map` зберігає `fromCache` і пропускає `Error`/`Loading` без трансформації → Task 1.7.
3. Fakes повертають **новий** `Flow` на кожен виклик (імерсійні лічильники) — інакше ViewModel-тести Фази 3 дадуть хибні результати → Task 1.9.
