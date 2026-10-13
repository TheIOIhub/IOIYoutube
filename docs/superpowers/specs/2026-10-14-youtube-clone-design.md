# IOIYoutube — Design Spec

**Date:** 2026-10-14  
**Status:** Draft

---

## 1. Overview

Android-додаток клон YouTube з блокувальником реклами, назва "IOITube". Перша версія включає базовий функціонал: головний екран з рекомендаціями, пошук, сторінка відео з плеєром, описом і коментарями.

**Key constraint:** Блокування реклами виконується виключно всередині власного плеєра через бібліотеку NewPipe Extractor, яка надає посилання на відео-потік без рекламних сегментів.

---

## 2. Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Content Source | NewPipe Extractor |
| Local Cache | Room Database |
| Image Loading | Coil |
| Navigation | Navigation Compose |
| Dependency Injection | Hilt |
| Async | Coroutines + Flow |
| Video Player | Media3 (ExoPlayer) |

---

## 3. Architecture

**Pattern:** MVVM + Clean Architecture

**Layers:**
```
presentation (Compose UI + ViewModels)
        ↓
domain (Use Cases + Repository Interfaces + Domain Models)
        ↑
data (NewPipe Extractor + Room + Repository Implementations)
```

**Principles:**
- Залежності течуть в один бік: `presentation → domain ← data`
- Domain шар не залежить від Android, NewPipe або Room
- Кожен Use Case відповідає за одну дію (Single Responsibility)

---

## 4. Project Structure

```
com.ioi.youtube/
├── data/                    # Шар даних
│   ├── extractor/           # NewPipe Extractor обгортки
│   ├── repository/          # Реалізації репозиторіїв
│   ├── local/               # Room бази даних, DAO
│   └── model/               # Моделі даних (DTO)
├── domain/                  # Бізнес-логіка
│   ├── model/               # Доменні моделі
│   ├── repository/          # Інтерфейси репозиторіїв
│   └── usecase/             # Use Cases
├── presentation/            # UI шар
│   ├── home/                # Головний екран
│   ├── search/              # Пошук
│   ├── player/              # Сторінка відео + плеєр
│   ├── components/          # Перевикористовувані Compose компоненти
│   └── navigation/          # Навігація
├── di/                      # Hilt модулі
└── util/                    # Утиліти, розширення
```

---

## 5. Core Components

### 5.1 Video Player
- Відео з YouTube завантажується через NewPipe Extractor
- NewPipe віддає посилання на відео-файл без реклами
- Відео програватється через Media3 (ExoPlayer)

### 5.2 Search
- Користувач вводить запит
- NewPipe повертає список відео з назвами, прев'ю, каналами
- Результати показуються у вигляді сітки

### 5.3 Video Page
- Назва відео, канал, опис, кількість переглядів
- Плеєр зверху
- Список коментарів знизу
- Рекомендовані відео знизу

### 5.4 Local Cache
- Результати пошуку та метадані відео зберігаються в Room
- Дозволяє показувати контент без інтернету

### 5.5 Navigation
- Три основні екрани: Головна → Пошук → Відео
- Стандартна навігація "назад-вперед"

---

## 6. Data Flow

```
User Action → ViewModel → UseCase → Repository → Extractor/Local DB
                                                        ↓
                                              Data flows back to UI
```

**Loading States:**
- `Loading` — показуємо спінер
- `Success` — показуємо контент
- `Error` — показуємо повідомлення про помилку з кнопкою "Спробувати знову"

---

## 7. Error Handling

| Scenario | Behavior |
|----------|----------|
| No internet | Show "No connection" message + cached content |
| YouTube format changed (NewPipe broken) | Show "Failed to load, try again later" |
| Video unavailable | Show message instead of player |
| Slow connection | Show loading indicator |

---

## 8. Testing Strategy

| Level | Tool | Description |
|-------|------|-------------|
| Unit Tests | JUnit + MockK | Бізнес-логіка без пристрою |
| Integration Tests | JUnit + Test Containers | Взаємодія компонентів |
| UI Tests | Compose Testing | Інтерфейс користувача |
| Manual Testing | Device/Emulator | Повна перевірка сценаріїв |

**Key libraries:**
- **JUnit** — модульні тести
- **MockK** — імітація залежностей (NewPipe, Repository)
- **Compose Testing** — UI тести
- **Turbine** — тестування Flow

---

## 9. Use Cases

| Use Case | Description |
|----------|-------------|
| `GetHomeFeed` | Отримання рекомендацій для головного екрана |
| `SearchVideos` | Пошук відео за запитом |
| `GetVideoDetails` | Отримання деталей відео (опис, коментарі, рекомендації) |
| `GetVideoStream` | Отримання посилання на відео-потік |
| `GetComments` | Отримання коментарів до відео |
