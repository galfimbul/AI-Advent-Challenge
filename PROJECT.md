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
├── MainActivity.kt              # Точка входа, NavHost (home / chat / discussion / temperature / modelcomparison / agent)
├── AiAdventChallengeApplication.kt  # Application: создание БД один раз в onCreate(), database
├── domain/
│   ├── agent/
│   │   └── SimpleAgent.kt       # Агент: process(dialog, request, useCompression, lastN, forceContextOverflow); сжатие: summaries + последние N сообщений
│   ├── ReasoningMode.kt         # Enum режимов рассуждения (не в data)
│   └── TemperaturePreset.kt     # Константы и пресеты температуры (0–2, шаг 0.1)
├── data/
│   ├── agent/
│   │   ├── AgentMessageEntity.kt   # Room Entity (id, role, text, sortOrder), таблица agent_messages
│   │   ├── AgentMessageDao.kt      # getAllMessages, insertAll, deleteAll
│   │   ├── AgentSummaryEntity.kt    # Room Entity (id, text, sortOrder), таблица agent_summaries
│   │   ├── AgentSummaryDao.kt       # getAllSummaries, insert, deleteAll
│   │   ├── AppDatabase.kt          # Room Database, version 2, миграция 1→2
│   │   ├── AgentDialogStorage.kt   # load(): AgentDialogState(summaries, messages), save(dialog), insertSummary, clear()
│   │   └── AgentCompressionPreferences.kt  # DataStore: use_compression, last_n_messages
│   ├── ChatRepository.kt        # Запросы к API, sendMessage, summarizeDialog (суммаризация блока диалога), runWithModel, compareModelResponses
│   ├── ModelRunResult.kt        # Результат одного запроса к модели (время, токены, стоимость)
│   └── openai/
│       ├── OpenAiApi.kt         # Retrofit: POST v1/chat/completions
│       └── OpenAiDto.kt         # Request/Response DTO, MessageContentDeserializer
└── ui/
    ├── agent/
    │   ├── AgentScreen.kt       # Экран-чат: переключатель «Без сжатия»/«Со сжатием», компактные кнопки N (5/10/20); тосты сжатия; LazyColumn, ввод, токены с режимом
    │   ├── AgentUiState.kt      # Состояние: messages, request, загрузка, ошибка, токены, useCompression, lastN, lastTokensModeCompression, toastMessage
    │   ├── AgentViewModel.kt    # agent.process(useCompression, lastN); ensureSummaries с тостами при реальной суммаризации; setUseCompression, setLastN, clearToastMessage
    │   └── AgentViewModelFactory.kt  # AgentDialogStorage(messageDao, summaryDao), ChatRepository, AgentCompressionPreferences, AgentViewModel
    ├── components/
    │   └── LoadingOverlay.kt    # Полноэкранный оверлей с лоудером (переиспользуемый)
    ├── theme/                   # Цвета, типографика, тема
    ├── home/
    │   ├── HomeScreen.kt        # Главный экран: картинка, название, секции с навигацией
    │   └── HomeNav.kt           # HomeSection, HomeNavItem, HOME_SECTIONS
    ├── chat/
    │   ├── ChatScreen.kt        # Экран: ввод, настройки, ответ, токены
    │   └── ChatViewModel.kt     # Состояние, вызов repository.sendMessage
    ├── discussion/
    │   ├── DiscussionScreen.kt   # Экран: задача, 4 способа, сравнение
    │   ├── DiscussionViewModel.kt
    │   └── DiscussionUiState.kt
    ├── temperature/
    │   ├── TemperatureScreen.kt   # Экран: ползунок 0–2, пресеты, список ответов, сравнение
    │   ├── TemperatureViewModel.kt
    │   └── TemperatureUiState.kt  # TemperatureRun, runs, sliderTemperature
    └── modelcomparison/
        ├── ModelComparisonScreen.kt   # Экран: один запрос на трёх моделях, время/токены/стоимость, вывод, ссылки
        ├── ModelComparisonViewModel.kt
        └── ModelComparisonUiState.kt
```

## Где что искать

| Задача | Файл / место |
|--------|----------------|
| API-ключ | `secret.properties` (не в репозитории), читается в `app/build.gradle.kts` → `BuildConfig.OPENAI_API_KEY` |
| Модель по умолчанию | `OpenAiDto.kt` → `ChatCompletionRequest.model` (сейчас `gpt-4.1`) |
| Лимит токенов, stop sequence | `ChatRepository.kt` → `MAX_TOKENS`, `STOP_SEQUENCE`; передаются из ViewModel |
| Параметры запроса (max_tokens, stop) | `ChatRepository.sendMessage()` формирует `ChatCompletionRequest` |
| System message | `ChatRepository.kt` → `SYSTEM_MESSAGE` |
| Главный экран, секции навигации | `ui/home/` (HomeScreen, HomeNav), маршрут `home` |
| Экран Discussion, режимы рассуждения | `ui/discussion/`, `ChatRepository.solveWithReasoningMode`, `domain/ReasoningMode` |
| Температура (параметр API, экран сравнения) | `OpenAiDto.kt` → `temperature`, `ChatRepository.sendWithTemperature`, `compareTemperatureResponses`, `ui/temperature/`, `domain/TemperaturePreset.kt` |
| Оверлей загрузки (полноэкранный, переиспользуемый) | `ui/components/LoadingOverlay.kt` |
| Версии моделей (слабая/средняя/сильная, время, токены, стоимость) | `ChatRepository.runWithModel`, `compareModelResponses`, `MODELS_FOR_COMPARISON`, `ui/modelcomparison/` |
| Агент (domain), экран «Агент» (чат) | `domain/agent/SimpleAgent.kt`, `ui/agent/`; вызов API только через агента; на главном экране отдельный блок «Агент» с кнопкой «Начать диалог» |
| Сохранение диалога агента (Room) | `data/agent/` (AgentMessageEntity, AgentSummaryEntity, оба DAO, AppDatabase v2 с миграцией, AgentDialogStorage); таблицы `agent_messages`, `agent_summaries` |
| Сжатие контекста агента (День 9) | Summaries блоков по 10 сообщений + последние N сообщений в промпте; настройки `use_compression`, `last_n_messages` в DataStore (AgentCompressionPreferences); ChatRepository.summarizeDialog |
| Тест превышения контекста (только debug) | Кнопка «Превысить контекст» слева от «Отправить» на экране агента; `SimpleAgent.process(..., forceContextOverflow = true)`; `AgentViewModel.sendContextOverflowTest()` |
| Логи запросов/ответов | Logcat, тег `OpenAI` |

## Сборка и запуск

- Сборка: `./gradlew assembleDebug` или Android Studio → Build → Make Project
- Ключ: скопировать `secret.properties.example` → `secret.properties`, подставить `OPENAI_API_KEY`
- Подробно: [README.md](README.md)#установка-и-настройка

## Ветки

- `challenge_day_1` — первый день (простой запрос, экран чата)
- `challenge_day_2` — настройки запроса (токены, stop, «без ограничения»), отображение usage
- `challenge_day_3` — экран Discussion (4 способа рассуждения, сравнение), навигация
- `home-screen` — главный экран (home), drawable-картинка, секция «Промптинг», кнопки «Назад» на чате и обсуждении
- Температура — экран «Температура» (ползунок 0–2, пресеты, сравнение при ≥2 ответах)
- `challenge_day_6` — первый агент (domain/agent, экран-чат, отдельный блок «Агент», «Начать диалог»)
- `challenge_day_7` — сохранение контекста диалога агента (Room, Application, «Очистить историю»)
- `challenge_day_8` — тест превышения контекста: кнопка «Превысить контекст» (только debug), слева от «Отправить»
- `challenge_day_9` — управление контекстом: сжатие истории (summaries + последние N), переключатель и настройка N, сравнение расхода токенов
