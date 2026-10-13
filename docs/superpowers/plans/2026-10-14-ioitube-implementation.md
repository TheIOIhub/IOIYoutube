# IOITube — План реалізації (майстер-план)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-додаток IOITube — клон YouTube із власним плеєром, що відтворює відео через NewPipe Extractor (без рекламних сегментів), з головною стрічкою, пошуком, сторінкою відео (опис + коментарі + рекомендації) та локальним кешем у Room.

**Architecture:** MVVM + Clean Architecture. Презентація (Compose + ViewModel) → домен (Use Cases + інтерфейси репозиторіїв) ← дані (NewPipe Extractor + Room + реалізації репозиторіїв). UI-стани: `UiState.Loading / Success / Error` (спека §6).

**Tech Stack:** Kotlin 2.1.0 · Compose BOM 2024.12.01 · AGP 8.7.3 / Gradle 8.11.1 · Hilt 2.54 · Room 2.6.1 · Media3 (ExoPlayer) 1.5.1 · Coil 2.7.0 · Navigation Compose 2.8.5 · NewPipe Extractor 0.26.5 (JitPack) · OkHttp 4.12.0 · Coroutines/Flow · Тести: JUnit4 + Turbine + MockK + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-14-youtube-clone-design.md`

---

## Global Constraints

Обмеження зі спеки. Кожна таска нижче неявно включає цей розділ.

- Пакет: `com.ioi.youtube`, назва застосунку в UI: **IOITube** (спека §1).
- Мова проєкту — Kotlin, UI-тексти — українською; кнопка помилки рівно `Спробувати знову` (спека §6).
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Java 17.
- Реклама блокується **виключно** через власний плеєр + NewPipe Extractor. Жодного WebView, жодного YouTube Data API (спека §1, Key constraint).
- Domain-шар НЕ залежить від Android, NewPipe і Room (спека §3). Імпорти цих бібліотек дозволені лише в `data/`, `di/` і `presentation/`.
- Один Use Case — одна дія (спека §3, §9).
- Стани завантаження: `Loading` (спінер), `Success` (контент), `Error` (повідомлення + «Спробувати знову») (спека §6).
- Немає інтернету → показати кеш із позначкою «Немає з'єднання»; кеш порожній → `Error` (спека §7).
- Усі мережеві виклики — на `Dispatchers.IO`.
- Тести бізнес-логіки — JUnit4 + fakes + Turbine, без Robolectric. Robolectric допускається тільки там, де в тасці написано явно (Room DAO, Compose-UI тести).
- Worker-моделі помилки NewPipe: `StreamInfo.getViewCount() == -1`, `getLikeCount() == -1`, `getDuration() == -1`, `getDescription() == null`, `CommentsInfo.getInfo() == null`, «немає progressive stream» — це не креші, а штатні ситуації.

### Зафіксовані версії (не оновлювати без причини)

| Що | Версія | Примітка |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Остання 8.x — сумісна з Android Studio 2025–2026 |
| Kotlin | 2.1.0 | + `org.jetbrains.kotlin.plugin.compose` 2.1.0 |
| Gradle wrapper | 8.11.1 | |
| KSP | 2.1.0-1.0.29 | Має збігатися з Kotlin |
| Compose BOM | 2024.12.01 | |
| Hilt / hilt-navigation-compose | 2.54 / 1.2.0 | |
| Room | 2.6.1 | |
| Media3 | 1.5.1 | |
| Coil | 2.7.0 | |
| Navigation Compose | 2.8.5 | |
| NewPipe Extractor | 0.26.5 | JitPack, актуальний реліз на 2026-08 |
| OkHttp | 4.12.0 | |
| JUnit / MockK / Turbine / Robolectric | 4.13.2 / 1.13.13 / 1.2.0 / 4.14.1 | |

---

## Як виконувати цей план з локальною моделлю

1. **Одна таска = один промпт.** Відкри файл фази, знайди таску, скопіюй **повністю** її текст (разом із кодом) у локальну модель. Не скорочуй, не переказуй «своїми словами».
2. **Перед таскою** `git status` має бути чистим. **Після таски** — команда перевірки зелений + коміт (команда вказана в тасці).
3. **Не проси модель нічого «поліпшити»/«додати»/«зарефакторити».** Межі таски фіксовані; зайві зміни ламають наступні таски.
4. Модель не має контексту проєкту — всі шляхи, пакети й коди вже прописані, виконуй рівно як написано.
5. Якщо крок не проходить після **двох** спроб — зупинись, збережи текст помилки в `docs/superpowers/notes.md`, не йди далі.
6. Порядок тасок суворий: Task N читає файли, створені в тасках <N.

Шаблон промпта для локальної моделі:

```
Ти виконуєш одну таску з документа плану реалізації Android-додатку IOITube (Kotlin, Jetpack Compose).
Виконай РІВНО те, що написано в тасці. Жодних зайвих файлів, рефакторингів чи фічей.
Кожен файл створюй з точним кодом із кроків. Після кожного кроку запускай вказану команду
перевірки й покажи її результат. Якщо команда падає — виправляй лише в межах цієї таски.

<ПОВНИЙ ТЕКСТ ТАСКИ>
```

## Task index

| Фаза | Файл плану | Таски |
|---|---|---|
| 1. Фундамент і домен | `2026-10-14-ioitube-phase-1-foundation.md` | 1.1 скелет Gradle/проєкту · 1.2 app-модуль + маніфест · 1.3 Compose-тема · 1.4 навігаційний скелет · 1.5 Hilt + MainActivity + перший білд · 1.6 домен-моделі + Formatters · 1.7 UiState · 1.8 інтерфейси репозиторіїв · 1.9 fakes для тестів · 1.10–1.14 Use Cases |
| 2. Шар даних | `2026-10-14-ioitube-phase-2-data-layer.md` | 2.1 NewPipe + Downloader · 2.2 YouTubeUrlParser · 2.3 Raw-моделі + маппери · 2.4 NewPipeRemoteDataSource · 2.5 FakeRemoteDataSource · 2.6–2.7 Room · 2.8 DAO-тести · 2.9–2.11 репозиторії · 2.12 DI + запуск |
| 3. Головна і пошук | `2026-10-14-ioitube-phase-3-home-search.md` | 3.1 компоненти · 3.2 HomeViewModel · 3.3 HomeScreen · 3.4 SearchViewModel · 3.5 SearchScreen · 3.6 навігація |
| 4. Плеєр і коментарі | `2026-10-14-ioitube-phase-4-player-comments.md` | 4.1 PlayerViewModel · 4.2 VideoPlayer · 4.3 деталі відео · 4.4 коментарі · 4.5 рекомендації · 4.6 помилки/офлайн |
| 5. Поліш і реліз | `2026-10-14-ioitube-phase-5-release.md` | 5.1 pull-to-refresh · 5.2 повний прогін тестів · 5.3 release-білд + README + чекліст |

## Review Focus

П'ять вхідних класів/режимів, де специфікація мовчить, а користувач постраждав би найсильніше. Кожен покритий тестом у відповідній тасці:

1. **URL у різних форматах** (`watch?v=`, `youtu.be/`, `/shorts/`, `/embed/`, `/live/`, голий 11-символьний ID, сміття рядком) → коректний ID або `null`, без креша → Task 2.2.
2. **Порожній/пробільний пошуковий запит** → миттєво `Success` з порожнім списком, жодного мережевого виклику → Task 3.4.
3. **Інтернету немає, кеш порожній** → `Error` + «Спробувати знову» → Task 2.9.
4. **Інтернету немає, кеш є** → `Success(fromCache = true)` + банер «Немає з'єднання», `Error` НЕ показується → Task 2.9.
5. **Деградація даних NewPipe** (`viewCount/likeCount/duration = -1`, `description = null`, коментарі вимкнені, відсутній progressive stream, порожній список рекомендацій) → UI показує безпечні значення, відсутній потік дає `Error`, крешів немає → Tasks 2.3, 2.4, 2.11, 4.6.

## Definition of Done (проєкт)

- `./gradlew testDebugUnitTest` — зелений.
- `./gradlew :app:assembleDebug` — зелений.
- На пристрої/емуляторі: головна стрічка показує відео, пошук працює, відео відтворюється в плеєрі, коментарі видимі, «Спробувати знову» відновлює завантаження, з вимкненим інтернетом видно кеш із банером.
