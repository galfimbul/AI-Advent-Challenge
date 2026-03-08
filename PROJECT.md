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
│   │   ├── ContextStrategy.kt   # Enum: SlidingWindow, StickyFacts, Branching, Summary; displayName()
│   │   ├── TaskStage.kt         # Enum: Planning, Execution, Validation, Done; TaskState; expectedActionText(), taskStageFromString()
│   │   └── SimpleAgent.kt       # Агент: process(dialog, request, ..., taskState); промпт по стратегии и состоянию задачи
│   ├── ReasoningMode.kt         # Enum режимов рассуждения (не в data)
│   └── TemperaturePreset.kt     # Константы и пресеты температуры (0–2, шаг 0.1)
├── data/
│   ├── agent/
│   │   ├── AgentMessageEntity.kt   # Room Entity (id, role, text, sortOrder, branchId), таблица agent_messages
│   │   ├── AgentMessageDao.kt      # getAllMessages, insertAll, deleteAll
│   │   ├── AgentSummaryEntity.kt    # Room Entity (id, text, sortOrder), таблица agent_summaries
│   │   ├── AgentSummaryDao.kt       # getAllSummaries, insert, deleteAll
│   │   ├── AgentFactsEntity.kt       # Room Entity (id, factsText), таблица agent_facts
│   │   ├── AgentFactsDao.kt          # getFacts, insert, deleteAll
│   │   ├── AgentBranchEntity.kt      # Room Entity (id, name, checkpointAt, loadedTaskId), таблица agent_branches
│   │   ├── AgentBranchDao.kt         # getAllBranches, getBranchById, insert, deleteAll, setLoadedTaskId
│   │   ├── AgentLongTermMemoryEntity.kt  # Room Entity (id, content), таблица agent_long_term_memory
│   │   ├── AgentLongTermMemoryDao.kt     # get, insert (REPLACE)
│   │   ├── AgentTaskMemoryEntity.kt   # Room Entity (id, name, content), таблица agent_task_memories
│   │   ├── AgentTaskMemoryDao.kt     # getAll, getById, insert, updateContent, deleteById, deleteAll
│   │   ├── AgentUserProfileEntity.kt   # Room Entity (id, name, preferences), таблица agent_user_profiles
│   │   ├── AgentUserProfileDao.kt      # getAll, getById, insert, update, deleteById
│   │   ├── AppDatabase.kt          # Room Database, version 6, миграции 1→2 … 5→6
│   │   ├── AgentDialogStorage.kt   # load, save, …; getAllProfiles, getProfileContent, saveProfile, deleteProfile; long-term, задачи, saveLoadedTaskIdForBranch
│   │   └── AgentPreferences.kt      # DataStore: context_strategy, last_n_messages, active_profile_id
│   ├── ChatRepository.kt        # sendMessage, extractFactsFromText, extractOrUpdateFacts (Sticky Facts), summarizeDialog, runWithModel, compareModelResponses
│   ├── ModelRunResult.kt        # Результат одного запроса к модели (время, токены, стоимость)
│   └── openai/
│       ├── OpenAiApi.kt         # Retrofit: POST v1/chat/completions
│       └── OpenAiDto.kt         # Request/Response DTO, MessageContentDeserializer
└── ui/
    ├── agent/
    │   ├── AgentScreen.kt       # Чат: стратегия, блок Facts, вкладки веток; Настройки (профиль пользователя, память, стратегия, N); диалог очистки, long-tap; LazyColumn, ввод, токены
    │   ├── AgentUiState.kt      # messages, request, profiles, activeProfileId, profileEditor*, contextStrategy, branches, facts, longTermMemory, taskMemories, loadedTaskId, …
    │   ├── AgentViewModel.kt    # process(…, userProfile); профили (loadProfiles, setActiveProfile, addProfile, openProfileEditor, saveProfileEditor, deleteProfile); команды и память
    │   └── AgentViewModelFactory.kt  # AgentDialogStorage(7 DAO), ChatRepository, AgentPreferences, AgentViewModel
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
| Сжатие контекста агента (День 9) | Summaries блоков по 10 сообщений + последние N сообщений в промпте; настройки в DataStore; ChatRepository.summarizeDialog |
| Стратегии контекста (День 10) | Четыре стратегии (русские названия): Sliding Window, Sticky Facts, Branching, Summary. DataStore: context_strategy, last_n_messages. Имя ветки — в диалоге при «Создать ветку». Room: agent_facts, agent_branches, branchId в messages. Bottom Sheet «Настройки агента». ChatRepository.extractOrUpdateFacts |
| Сценарий сообщений для теста стратегий агента | [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md): таблица из 13 сообщений и подсказки по проверке каждой стратегии |
| Модель памяти агента (День 11) | Три слоя: память диалога (сессия), память задачи (agent_task_memories), долговременная (agent_long_term_memory). [docs/PLAN_DAY_11_MEMORY.md](docs/PLAN_DAY_11_MEMORY.md). Настройки агента: секции памяти; команды /add_long_term, /add_task_memory, /help; long-tap по сообщению. ChatRepository.extractFactsFromText. Подключённая задача хранится по ветке (agent_branches.loadedTaskId, миграция 4→5), восстанавливается при загрузке и смене ветки. |
| Профили пользователя агента (День 12) | AgentPreferences (DataStore): context_strategy, last_n_messages, active_profile_id. Room: agent_user_profiles (AgentUserProfileEntity, AgentUserProfileDao), миграция 5→6. AgentDialogStorage: getAllProfiles, getProfileContent, saveProfile, deleteProfile. ChatRepository.sendMessage(systemMessage); SimpleAgent.process(..., userProfile) — блок в user-промпте и расширенный system message. В настройках агента секция «Профиль пользователя» первой: выбор, добавление, редактирование, удаление. |
| Состояние задачи агента (День 13) | domain/agent/TaskStage.kt (enum, TaskState); Room agent_task_memories: stage, currentStep, isPaused (миграция 6→7); Storage getTaskState, updateTaskState; SimpleAgent.process(..., taskState); ViewModel: loadedTaskState, onEnterScreen/onLeaveScreen, confirmTaskResult/rejectTaskResult, executeCommand; кнопка «Команды» (список команд); пауза при выходе с экрана. |
| Инварианты агента (День 14) | Настройки агента, блок «Инварианты»; хранятся в AgentPreferences (DataStore, agent_invariants). SimpleAgent.process(invariantsText) добавляет блок в system message; при конфликте запроса с инвариантом агент отказывает и объясняет. Настройки разбиты на шесть сворачиваемых блоков (аккордеон). |
| Тест превышения контекста | Кнопка «Превысить контекст» в настройках агента (чекбокс «Показать кнопку…»); по умолчанию скрыта; `AgentViewModel.sendContextOverflowTest()` |
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
- `challenge_day_10` — стратегии контекста: Sliding Window, Sticky Facts, Branching, Summary; Bottom Sheet настроек; сравнение на сценарии 10–15 сообщений
- `challenge_day_11` — модель памяти: три слоя (диалог, задача, долговременная); Room agent_long_term_memory, agent_task_memories; команды в поле ввода; long-tap с извлечением фактов
- `challenge_day_12` — персонализация: профили пользователя (имя + предпочтения); AgentPreferences (active_profile_id); Room agent_user_profiles; подстановка в user-промпт и system message; секция «Профиль пользователя» в настройках агента
- `challenge_day_13` — состояние задачи (FSM): этапы Planning → Execution → Validation → Done; переход по /confirm и /reject; пауза при выходе с экрана; кнопка «Команды»; AgentPreferences showContextOverflowButton
- `challenge_day_14` — инварианты агента (настройки, DataStore); сворачиваемые блоки в настройках; отказ при конфликте запроса с инвариантом
