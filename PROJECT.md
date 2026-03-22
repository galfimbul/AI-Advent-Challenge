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
│   │   ├── AgentBranchEntity.kt      # Room Entity (id, name, checkpointAt, loadedTaskId, stage, currentStep, isPaused), таблица agent_branches
│   │   ├── AgentBranchDao.kt         # getAllBranches, getBranchById, insert, deleteAll, setLoadedTaskId, updateBranchTaskState, clearBranchTaskState
│   │   ├── AgentLongTermMemoryEntity.kt  # Room Entity (id, content), таблица agent_long_term_memory
│   │   ├── AgentLongTermMemoryDao.kt     # get, insert (REPLACE)
│   │   ├── AgentTaskMemoryEntity.kt   # Room Entity (id, name, content), таблица agent_task_memories
│   │   ├── AgentTaskMemoryDao.kt     # getAll, getById, insert, updateContent, deleteById, deleteAll
│   │   ├── AgentUserProfileEntity.kt   # Room Entity (id, name, preferences), таблица agent_user_profiles
│   │   ├── AgentUserProfileDao.kt      # getAll, getById, insert, update, deleteById
│   │   ├── AppDatabase.kt          # Room Database, version 8, миграции 1→2 … 7→8
│   │   ├── AgentDialogStorage.kt   # load, save, …; getBranchTaskState, updateBranchTaskState, clearBranchTaskState; профили, long-term, задачи, saveLoadedTaskIdForBranch
│   │   └── AgentPreferences.kt      # DataStore: context_strategy, last_n_messages, active_profile_id, rag_enabled
│   ├── rag/                     # RAG (День 22): OllamaEmbeddingClient, RagContextBuilder, RagMarkdownFormatter, OllamaHostResolver
│   ├── ChatRepository.kt        # sendMessage, extractFactsFromText, extractOrUpdateFacts (Sticky Facts), summarizeDialog, runWithModel, compareModelResponses
│   ├── ModelRunResult.kt        # Результат одного запроса к модели (время, токены, стоимость)
│   └── openai/
│       ├── OpenAiApi.kt         # Retrofit: POST v1/chat/completions
│       └── OpenAiDto.kt         # Request/Response DTO, MessageContentDeserializer
└── ui/
    ├── agent/
    │   ├── AgentScreen.kt       # Чат: стратегия, блок Facts, вкладки веток; Настройки (профиль, память, стратегия, N, RAG, инварианты); диалог очистки, long-tap; LazyColumn, ввод, токены
    │   ├── AgentUiState.kt      # messages, request, profiles, activeProfileId, profileEditor*, contextStrategy, branches, facts, longTermMemory, taskMemories, loadedTaskId, ragEnabled, …
    │   ├── AgentViewModel.kt    # process(…, userProfile, ragContext); RagContextBuilder при ragEnabled; профили, команды, память
    │   └── AgentViewModelFactory.kt  # AgentDialogStorage(7 DAO), ChatRepository, AgentPreferences, RagContextBuilder?, AgentViewModel
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
| API-ключ OpenAI | `secret.properties` (не в репозитории), читается в `app/build.gradle.kts` → `BuildConfig.OPENAI_API_KEY` |
| Токен Apify (MCP погода) | `secret.properties` → `BuildConfig.APIFY_API_KEY`; используется в `data/mcp/McpWeatherClient` для доступа к Weather MCP Server |
| URL своего MCP-сервера (День 17) | `secret.properties` → `BuildConfig.MCP_CUSTOM_SERVER_URL` (полный URL до `/mcp`); используется в `data/mcp/McpCustomClient` |
| Ollama для RAG агента (День 22) | `secret.properties` → `BuildConfig.OLLAMA_HOST` (базовый URL Ollama, напр. `http://10.0.2.2:11434` на эмуляторе); `data/rag/RagContextBuilder` + переключатель в настройках агента |
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
| Сценарий сообщений для теста стратегий агента и RAG | [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md): таблица из 13 сообщений (стратегии контекста), сценарии задач/MCP, **День 22** — 10 вопросов для сравнения ответов с/без RAG |
| Модель памяти агента (День 11) | Три слоя: память диалога (сессия), память задачи (agent_task_memories), долговременная (agent_long_term_memory). [docs/PLAN_DAY_11_MEMORY.md](docs/PLAN_DAY_11_MEMORY.md). Настройки агента: секции памяти; команды /add_long_term, /add_task_memory, /help; long-tap по сообщению. ChatRepository.extractFactsFromText. Подключённая задача хранится по ветке (agent_branches.loadedTaskId, миграция 4→5), восстанавливается при загрузке и смене ветки. |
| Профили пользователя агента (День 12) | AgentPreferences (DataStore): context_strategy, last_n_messages, active_profile_id. Room: agent_user_profiles (AgentUserProfileEntity, AgentUserProfileDao), миграция 5→6. AgentDialogStorage: getAllProfiles, getProfileContent, saveProfile, deleteProfile. ChatRepository.sendMessage(systemMessage); SimpleAgent.process(..., userProfile) — блок в user-промпте и расширенный system message. В настройках агента секция «Профиль пользователя» первой: выбор, добавление, редактирование, удаление. |
| Состояние задачи агента (День 13→15) | domain/agent/TaskStage.kt (enum, TaskState); с Дня 15 — этапы хранятся per-branch в agent_branches (stage, currentStep, isPaused, миграция 7→8); Storage getBranchTaskState, updateBranchTaskState, clearBranchTaskState; SimpleAgent.process(..., taskState); ViewModel: loadedTaskState, /start_task, /stop_task, onEnterScreen/onLeaveScreen, confirmTaskResult/rejectWithUserComment, executeCommand; кнопка «Команды»; пауза при выходе с экрана. |
| Инварианты агента (День 14) | Настройки агента, блок «Инварианты»; хранятся в AgentPreferences (DataStore, agent_invariants). SimpleAgent.process(invariantsText) добавляет блок в system message; при конфликте запроса с инвариантом агент отказывает и объясняет. Настройки разбиты на шесть сворачиваемых блоков (аккордеон). |
| Контролируемые переходы (День 15) | Этапы задачи (Planning/Execution/Validation/Done) хранятся per-branch в agent_branches (миграция 7→8). /start_task запускает цикл, /stop_task останавливает. /confirm, /reject, /reset_planning работают без подключённой задачи. Промпт агента: блок состояния этапа отдельно от taskMemory. |
| Тест превышения контекста | Кнопка «Превысить контекст» в настройках агента (чекбокс «Показать кнопку…»); по умолчанию скрыта; `AgentViewModel.sendContextOverflowTest()` |
| MCP погода (День 16) | Команды `/tools` и `/weather Город` в чате агента; сервер `https://jiri-spilka--weather-mcp-server.apify.actor/mcp`; токен в `secret.properties` → `APIFY_API_KEY`; клиент `data/mcp/McpWeatherClient`, Kotlin MCP SDK + Ktor (только для MCP). |
| Свой MCP-сервер (День 17) | Модуль `mcp-server/` (Ktor + MCP Kotlin SDK), инструмент `mock_echo`; команда `/mock Текст` в чате агента; URL в `secret.properties` → `MCP_CUSTOM_SERVER_URL`; клиент `data/mcp/McpCustomClient`; развёртывание — [mcp-server/DEPLOY.md](mcp-server/DEPLOY.md). При заданном URL модель сама вызывает mock_echo и get_current_weather по обычному запросу (tools в API, цикл в SimpleAgent, до 5 раундов); статический список tools — `data/AgentTools.kt`, один round-trip — `ChatRepository.sendOneCompletion`. |
| Напоминалки (День 18) | MCP-сервер: `register_reminder`, `get_reminders`(timezone опционально — время в таймзоне пользователя), SQLite (ReminderStorage). Приложение: AgentToolConstants; schedule_reminder, get_reminders; McpCustomClient.callTool; ReminderScheduler, AppReminderScheduler (setAlarmClock + fallback), ReminderReceiver; POST_NOTIFICATIONS и SCHEDULE_EXACT_ALARM; запрос уведомлений при старте (MainActivity). Таймзона устройства передаётся в get_reminders (userTimezone в process). |
| Логи запросов/ответов | Logcat, тег `OpenAI` |
| Индексация документов + эмбеддинги (День 21) | Модуль [doc-index/](doc-index/) — Ollama `nomic-embed-text`, SQLite `doc_index.sqlite`; корпус: см. [doc-index/README.md](doc-index/README.md) (корень/`docs/`/`doc-index/**/*.md`, `app/src/main/java` и `doc-index/src/main/kotlin`, `app/build.gradle.kts`, `secret.properties.example`). Стратегия **STRUCTURE**: заголовки `#`–`######`, склейка абзацев и префикс раздела в `text` чанка, `schema_version` в БД (см. README). Задачи `./gradlew :doc-index:buildDocIndex`, `:doc-index:printDocIndexReport`. В приложении: `data/index/` (`DocEmbeddingIndex.openFromAssets`). `merge*Assets` → `prepareDocIndexAssets` → `buildDocIndex`. |

## Сборка и запуск

- Сборка: `./gradlew assembleDebug` или Android Studio → Build → Make Project (**для Дня 21** перед этим должен быть доступен [Ollama](https://ollama.com) с моделью `nomic-embed-text`, иначе задача индексации завершится ошибкой — см. [doc-index/README.md](doc-index/README.md))
- Ключи: скопировать `secret.properties.example` → `secret.properties`, подставить `OPENAI_API_KEY`, при необходимости `APIFY_API_KEY` (MCP погода), `MCP_CUSTOM_SERVER_URL` (свой MCP, День 17) и `OLLAMA_HOST` (RAG в агенте, День 22)
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
- `challenge_day_15` — контролируемые переходы: этапы задачи per-branch; /start_task, /stop_task; десвязка этапов от памяти задачи
- `challenge_day_16` — MCP: подключение к Weather MCP Server (Apify), команды /tools и /weather; Kotlin MCP SDK, Ktor (только для MCP); BuildConfig.APIFY_API_KEY
- `challenge_day_17` — свой MCP-сервер (модуль mcp-server, mock_echo); команда /mock; BuildConfig.MCP_CUSTOM_SERVER_URL; McpCustomClient
- `challenge_day_18` — напоминалки: MCP register_reminder/get_reminders (SQLite), константы инструментов (AgentToolConstants), schedule_reminder/get_reminders в агенте, AlarmManager + уведомления (AppReminderScheduler, ReminderReceiver)
- `challenge_day_21` — индексация документов: модуль `doc-index` (chunking ×2, Ollama embeddings, SQLite), `prepareDocIndexAssets` → assets APK, `data/index` для top-k по косинусу
- `challenge_day_22` — RAG в агенте: эмбеддинг вопроса через Ollama, поиск по `doc_index.sqlite`, вставка фрагментов в промпт; `OLLAMA_HOST`, `data/rag/`, переключатель в настройках агента
