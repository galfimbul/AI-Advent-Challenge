# Карта проекта AI Advent Challenge

Краткий обзор для быстрой навигации. Описание фич по дням — в [README.md](README.md).

## Стек

- **Язык:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Сеть:** Retrofit, OkHttp (логирование), Gson
- **Асинхронность:** Kotlin Coroutines
- **API:** OpenAI Chat Completions ([документация](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create))

## Структура пакетов

```
app/src/main/java/com/example/aiadventchallenge/
├── MainActivity.kt              # Точка входа, Compose Scaffold + ChatScreen
├── data/
│   ├── ChatRepository.kt        # Запросы к API, ключ, таймаут, system message
│   └── openai/
│       ├── OpenAiApi.kt         # Retrofit: POST v1/chat/completions
│       └── OpenAiDto.kt         # Request/Response DTO, MessageContentDeserializer
└── ui/
    ├── theme/                   # Цвета, типографика, тема
    └── chat/
        ├── ChatScreen.kt        # Экран: ввод, настройки, ответ, токены
        └── ChatViewModel.kt     # Состояние, вызов repository.sendMessage
```

## Где что искать

| Задача | Файл / место |
|--------|----------------|
| API-ключ | `secret.properties` (не в репозитории), читается в `app/build.gradle.kts` → `BuildConfig.OPENAI_API_KEY` |
| Модель по умолчанию | `OpenAiDto.kt` → `ChatCompletionRequest.model` (сейчас `gpt-4.1`) |
| Лимит токенов, stop sequence | `ChatRepository.kt` → `MAX_TOKENS`, `STOP_SEQUENCE`; передаются из ViewModel |
| Параметры запроса (max_tokens, stop) | `ChatRepository.sendMessage()` формирует `ChatCompletionRequest` |
| System message | `ChatRepository.kt` → `SYSTEM_MESSAGE` |
| Логи запросов/ответов | Logcat, тег `OpenAI` |

## Сборка и запуск

- Сборка: `./gradlew assembleDebug` или Android Studio → Build → Make Project
- Ключ: скопировать `secret.properties.example` → `secret.properties`, подставить `OPENAI_API_KEY`
- Подробно: [README.md](README.md)#установка-и-настройка

## Ветки

- `challenge_day_1` — первый день (простой запрос, экран чата)
- `challenge_day_2` — настройки запроса (токены, stop, «без ограничения»), отображение usage
