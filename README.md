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

Скопiюй `keystore.properties.template` у `keystore.properties`, створi `ioitube.jks`:

```bash
keytool -genkey -v -keystore ioitube.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ioitube
```
