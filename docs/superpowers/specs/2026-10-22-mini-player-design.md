# Міні-плеєр IOITube — дизайн

Дата: 2026-10-22
Статус: затверджено для планування

## 1. Контекст

Зараз `PlayerHolder` створюється всередині `PlayerScreen` і звільняється, коли
екран відео зникає з композиції. Через це відтворення не може пережити перехід
на Home/Search, а отже, й міні-плеєр неможливий без зміни володіння плеєром.

Потрібна поведінка як у YouTube: на відкритому відео в портреті потягнути
відео вниз — воно згортається в маленький плаваючий плеєр, а під ним
з'являється попередній екран, де можна далі гортати й шукати відео.

## 2. Цілі

- Потягування відео вниз у портреті згортає його в міні-плеєр, не перериваючи
  звук і зберігаючи позицію.
- Міні-плеєр видно на Home і Search: маленьке відео 16:9 справа знизу над
  нижньою навігацією, з pause/play і кнопкою закриття.
- Тап по міні-плеєру розгортає відео назад; Back у портреті також згортає його.
- Після розгортання відео з'являється одразу з кешу сесії; якість і мовна
  доріжка не скидаються.
- У Shorts міні-плеєр не показується, а відтворення зупиняється.
- Наявні fullscreen-жести (тапи ±5 с, яскравість, гучність), pinch-scale та
  анімація входу у fullscreen не змінюються.

## 3. Поза межами

- Фонове відтворення з foreground service або медіа-сповіщенням.
- Системний Picture-in-Picture.
- Перетягування самого міні-плеєра або збереження його позиції.
- Міні-плеєр для Shorts.
- Складний shared-element перехід «відео → міні-плеєр».
- Збереження масштабу FIT/FILL між згортанням і розгортанням: масштаб, як і
  зараз, визначається орієнтацією.

## 4. Ключові рішення

1. Плеєром володіє activity-scoped `PlaybackViewModel` (Hilt), а не екран.
2. `PlayerScreen` отримує готовий `PlayerHolder` і не звільняє його.
3. `PlaybackViewModel` зберігає `videoId`, `minimized`, кеш деталей/потоку та
   вибір якості/аудіо.
4. Back у портреті згортає відео; Back у fullscreen спершу виходить із fullscreen.
5. Перехід у Shorts зупиняє й очищає міні-плеєр.
6. Версія релізу — `1.2.2` (`versionCode 6`).

## 5. Архітектура

```
IOITubeApp
├── PlaybackViewModel (activity-scoped, володіє PlayerHolder)
├── NavHost
│   ├── Home / Search ── міні-плеєр як overlay
│   ├── Shorts ── stop() міні-плеєра
│   └── PlayerRoute
│       ├── PlayerViewModel (мережа/кеш: details, stream, comments)
│       └── PlayerScreen(holder = playback.holder)
└── MiniPlayer(playback, onExpand, onClose)
```

### 5.1 PlaybackViewModel

`@HiltViewModel class PlaybackViewModel @Inject constructor(@ApplicationContext context: Context)`.

Стан (Compose `mutableStateOf`, доступний для читання):
- `videoId: String?`
- `minimized: Boolean`
- `cachedDetails: VideoDetails?`
- `cachedStream: VideoStream?`
- `selectedVideo: VideoVariant?`
- `selectedAudio: AudioTrackOption?`

Похідні значення:
- `holder: PlayerHolder` — створюється ліниво з application context.
- `canMinimize: Boolean` — `videoId != null && holder.isReady`.

Методи:
- `begin(videoId: String)`
  - Якщо `videoId` відрізняється від поточного: `holder.stop()`, очистити кеш і
    вибір якості/аудіо, запам'ятати новий id.
  - Завжди встановлює `minimized = false`.
  - Для того самого відео не чіпає ні плеєр, ні кеш — це шлях розгортання.
- `cache(videoId: String, details: VideoDetails, stream: VideoStream)`
  - Оновлює кеш лише якщо `videoId == this.videoId`.
- `updateSelection(video: VideoVariant?, audio: AudioTrackOption?)`
  - Зберігає вибір для наступного розгортання.
- `minimize(): Boolean`
  - Якщо `canMinimize`, ставить `minimized = true` і повертає `true`.
  - Інакше нічого не змінює й повертає `false`.
- `stop()`
  - `holder.stop()`, очищає `videoId`, кеш, вибір і `minimized`.
- `onCleared()`
  - Звільняє holder, якщо він був створений.

### 5.2 PlayerHolder

Додати:
- `isReady: Boolean` — джерело встановлено, плеєр не звільнений.
- `stop()`:
  - `player.stop()`, `player.clearMediaItems()`;
  - скинути `currentPlan`, `loaded`, `stream`, `video`, `audio`, `auto`, `error`;
  - **не** знімати listener і **не** викликати `release()`.
- `release()` залишається без змін і викликається лише з `onCleared()`.

Після `stop()` наступний `update()` з тим самим планом має завантажити джерело
заново з позиції 0, а не вважати його вже завантаженим.

### 5.3 PlayerScreen

Сигнатура змінюється:
- `holder: PlayerHolder` замість `holderFactory`;
- `initialVideo: VideoVariant?`, `initialAudio: AudioTrackOption?`;
- `onSelectionChanged: (VideoVariant?, AudioTrackOption?) -> Unit`;
- `onMinimize: (() -> Unit)? = null`.

Прибрати `DisposableEffect`, який викликав `holder.release()`. Екран більше не
володіє плеєром.

Локальний стан якості/аудіо ініціалізується з `initialVideo`/`initialAudio` і
повідомляє зміни через `onSelectionChanged`. Масштаб залишається локальним і
скидається за орієнтацією, як зараз.

### 5.4 PlayerRoute

Отримує `playback: PlaybackViewModel`, `onVideoClick`, `onBack`.

- `LaunchedEffect(viewModel.videoId) { playback.begin(viewModel.videoId) }`.
- Коли `video` і `stream` успішні — `playback.cache(videoId, details, stream)`.
- Для показу об'єднує `PlayerUiState` з кешем: якщо `video` або `stream` у
  стані Loading, а кеш для цього ж відео є, показує кешоване значення.
- `BackHandler`:
  - fullscreen → вийти з fullscreen (наявний `onToggleFullscreen`);
  - портрет і `playback.minimize()` → `onBack()`;
  - інакше `playback.stop()`, потім `onBack()`.
- `onMinimize` для `PlayerScreen` робить те саме, що портретний Back.
- Клік по рекомендації замінює поточний player-route (`popUpTo` з поточним
  player-destination, `inclusive = true`), щоб не накопичувати екрани плеєра.
- Для player-route вимикаються `exitTransition`/`popExitTransition`: старий
  PlayerView має від'єднатися до того, як новий екран або міні-плеєр приєднає
  свій PlayerView до того самого ExoPlayer.

### 5.5 IOITubeApp

- Отримує `val playback: PlaybackViewModel = hiltViewModel()`.
- Контент Scaffold обгортає в `Box(Modifier.fillMaxSize().padding(padding))`:
  - `NavHost` заповнює Box;
  - міні-плеєр вирівнюється `BottomEnd` з відступом 12dp і тому опиняється над
    нижньою навігацією.
- Умова показу: `playback.minimized && playback.canMinimize &&
  currentRoute in (HOME, SEARCH)`.
- `LaunchedEffect(currentRoute)`: якщо маршрут Shorts — `playback.stop()`.
- `PlayerRoute` отримує `playback` і `onBack = { navController.popBackStack() }`.

### 5.6 MiniPlayer

Новий composable у `presentation/player/MiniPlayer.kt`.

- Картка `16:9`, ширина 176dp, закруглення 10dp, тінь, чорний фон.
- Всередині `AndroidView(PlayerView)`:
  - `player = playback.holder.player`;
  - `useController = false`;
  - `resizeMode = ZOOM`;
  - при dispose — `view.player = null`, без `release()`.
- Прозорий шар на всю картку: тап розгортає відео.
- Кнопки поверх шару:
  - pause/play знизу ліворуч;
  - закриття X зверху праворуч;
  - білі іконки на напівпрозорому чорному фоні.
- Стан `isPlaying` береться з `Player.Listener`; play/pause викликає
  `player.play()` / `player.pause()`.
- Content descriptions українською: «Пауза», «Відтворити», «Закрити міні-плеєр»,
  «Розгорнути відео».
- Поява — короткий fade + scale. Зникнення — одразу (`exit = None`), щоб під
  час переходу не тримати два PlayerView на одному ExoPlayer.

## 6. Жест згортання

- Працює лише в портреті (`!isFullscreen`) і лише по області відео.
- Інтегрується в наявний `OnTouchListener` у `VideoPlayer`, який уже обробляє
  fullscreen-жести.
- Новий режим `MINIMIZE`:
  - починається, коли палець зсунувся переважно вниз після touch slop;
  - має приоритет над звичайними тапами, але не над pinch;
  - під час руху `minimizeDrag` дорівнює вертикальному зсуву;
  - вся картка відео (разом із кнопками) зсувається через `graphicsLayer` і має
    `zIndex(1f)`, щоб бути поверх списку деталей;
  - контролер PlayerView ховається на старті протягування.
- Поріг: `MINIMIZE_DRAG_FRACTION = 0.30f` від висоти відео.
  - відпускання нижче порогу → анімоване повернення за 200 мс;
  - відпускання на порозі або нижче → `onMinimize()`;
  - `ACTION_CANCEL` → повернення без згортання.
- Чиста функція `shouldMinimize(dragPx, heightPx)` живе в `PlayerGestures.kt`
  і покривається unit-тестами.

## 7. Потоки станів

1. **Home → відео**
   - `PlayerRoute` викликає `begin(id)`, вантажить дані, `PlayerScreen` оновлює
     holder, відео грає.
2. **Свайп вниз / Back у портреті**
   - `minimize()` ставить `minimized = true`, `onBack()` повертає на Home/Search.
   - `PlayerScreen` і його PlayerView зникають, holder продовжує грати.
   - `MiniPlayer` монтує новий PlayerView на той самий ExoPlayer.
3. **Тап по міні-плеєру**
   - Навігація на `player/{videoId}`.
   - `begin` бачить той самий id, не зупиняє плеєр.
   - Кеш дає деталі/потік одразу; `PlayerScreen` оновлює holder тим самим
     планом, тому позиція не скидається.
   - Міні-плеєр зникає, PlayerView екрана відео приєднується до того ж плеєра.
4. **Закриття X**
   - `playback.stop()`, міні-плеєр зникає, звук зупиняється.
5. **Нове відео з Home/Search при активному міні-плеєрі**
   - Новий player-route викликає `begin(newId)`: старий holder зупиняється,
     кеш і вибір очищаються, вантажиться нове відео.
6. **Перехід у Shorts**
   - `IOITubeApp` бачить маршрут Shorts і викликає `playback.stop()`.
7. **Вихід із застосунку**
   - `PlaybackViewModel.onCleared()` звільняє ExoPlayer.

## 8. Помилки та краєві випадки

- Якщо потік ще не завантажився, `canMinimize == false`: Back зупиняє сесію й
  закриває екран, порожній міні-плеєр не показується.
- Якщо під час відтворення сталася помилка, міні-плеєр лишається видимим;
  тап розгортає екран відео, де є наявний retry.
- Якщо кешований потік застарів, розгортання все одно показує відео з кешу,
  а `PlayerViewModel` у фоні оновлює дані. Помилка обробляється наявним
  retry-механізмом.
- Один ExoPlayer має лише один активний PlayerView. `PlayerScreen` і
  `MiniPlayer` приєднують його у своєму життєвому циклі, а player-route має
  вимкнений exit-transition, щоб уникнути одночасного приєднання.
- Поворот у fullscreen і назад не створює новий Activity завдяки `configChanges`;
  holder і позиція зберігаються.
- Повне перестворення Activity (не поворот) втрачає сесію; фонове відтворення
  поза межами цієї фічі.

## 9. Тестування

### Unit / Compose

- `PlayerGesturesTest`: `shouldMinimize` — нижче порогу false, на порозі/вище true,
  нульова висота false.
- `PlaybackViewModelTest`:
  - `begin` нового id очищає кеш і вибір;
  - `begin` того самого id зберігає кеш;
  - `cache` ігнорує чужий videoId;
  - `minimize` без готового джерела повертає false;
  - `stop` очищає стан і робить `canMinimize == false`.
- `PlayerScreenTest`:
  - усі успішні кейси отримують спільний `PlayerHolder` і звільняють його в
    тесті;
  - `PlayerScreen` при dispose не звільняє переданий holder;
  - свайп вниз по відео викликає `onMinimize`, короткий свайп — ні;
  - наявні тести метаданих, коментарів, рекомендацій і fullscreen проходять.
- `MiniPlayerTest`:
  - є pause/play і X;
  - X викликає `onClose`;
  - тап по відео викликає `onExpand`;
  - pause/play змінює `playWhenReady`.
- `VideoPlayerTest`, `PinchScaleTest`: без регресій.

### Збірка

- `./gradlew :app:testDebugUnitTest --tests 'com.ioi.youtube.presentation.player.*'`
- `./gradlew :app:assembleRelease`
- `./gradlew :app:lintVitalRelease`

## 10. Реліз і ручна перевірка

- Версія `1.2.2`, `versionCode 6`.
- APK: `dist/IOITube-1.2.2-release.apk`, SHA-256 у звіті.
- Надіслати на TCL 306 через KDE Connect.
- Перевірити вручну:
  - свайп вниз згортає й не перериває звук;
  - Back у портреті згортає;
  - Back у fullscreen спершу виходить із fullscreen;
  - тап по міні-плеєру розгортає з тієї ж позиції;
  - X зупиняє;
  - Shorts зупиняє міні-плеєр;
  - нове відео замінює старе;
  - якість/мова зберігаються після розгортання.
- GitHub-реліз публікується лише після підтвердження користувача.

## 11. Файли

Нові:
- `app/src/main/java/com/ioi/youtube/presentation/player/PlaybackViewModel.kt`
- `app/src/main/java/com/ioi/youtube/presentation/player/MiniPlayer.kt`
- `app/src/test/java/com/ioi/youtube/presentation/player/PlaybackViewModelTest.kt`
- `app/src/test/java/com/ioi/youtube/presentation/player/MiniPlayerTest.kt`

Змінені:
- `PlayerHolder.kt` — `stop()`, `isReady`
- `PlayerScreen.kt` — shared holder, initial selection, `onMinimize`
- `PlayerRoute` у `PlayerScreen.kt` — `playback`, BackHandler, кеш, заміна route
- `VideoPlayer.kt` — drag-to-minimize
- `PlayerGestures.kt` — поріг згортання
- `IOITubeApp.kt` — `PlaybackViewModel`, overlay, stop на Shorts
- `PlayerScreenTest.kt`, `VideoPlayerTest.kt`, `PlayerGesturesTest.kt`
- `app/build.gradle.kts` — версія 1.2.2 / 6

## 12. Порядок впровадження

1. Зберегти поточні незакомічені fullscreen-зміни окремим комітом.
2. Додати `PlaybackViewModel` і зміни `PlayerHolder`.
3. Перевести `PlayerScreen`/`PlayerRoute` на спільний holder.
4. Додати `MiniPlayer` і overlay у `IOITubeApp`.
5. Додати жест згортання.
6. Оновити й додати тести.
7. Зібрати, перевірити на пристрої, підготувати реліз.
