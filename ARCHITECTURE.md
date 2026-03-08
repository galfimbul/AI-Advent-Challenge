# Архитектура

## Поток данных

```
Пользователь (UI)
       ↓
  ChatScreen (Compose)
       ↓  события, чтение uiState
  ChatViewModel
       ↓  sendMessage(query, maxTokens, stopPhrases)
  ChatRepository
       ↓  BuildConfig.OPENAI_API_KEY, ChatCompletionRequest
  OpenAiApi (Retrofit) → POST https://api.openai.com/v1/chat/completions
       ↓
  ChatCompletionResponse → ChatResponse(content, usage, finishReason)
       ↓
  ViewModel обновляет StateFlow → UI перерисовывается
```

## Слои

- **UI:** `ChatScreen` — только отображение и вызовы `viewModel.*`. Состояние из `viewModel.uiState`.
- **ViewModel:** `ChatViewModel` — состояние (запрос, ответ, настройки, токены), вызов `repository.sendMessage`, маппинг результата в `ChatUiState`.
- **Data:** `ChatRepository` — формирование запроса (messages, maxCompletionTokens, stop), вызов API, разбор ответа и обработка ошибок (в т.ч. пустой ответ при reasoning).
- **API:** `OpenAiApi` + `OpenAiDto` — контракт с OpenAI; кастомный десериализатор для `content` (строка или массив с `text`).

## Параметры запроса

- **Модель:** задаётся в `OpenAiDto.kt` в `ChatCompletionRequest.model`.
- **Сообщения:** в `ChatRepository.sendMessage()` — первое `system` (SYSTEM_MESSAGE), второе `user` (текст пользователя).
- **max_completion_tokens:** из UI (поле «Макс. токенов» или «Без ограничения»); передаётся как `maxTokens: Int?` в репозиторий.
- **stop:** из UI: при «Без ограничения» — `null`, иначе `listOf(STOP_SEQUENCE)`; константа `STOP_SEQUENCE` в `ChatRepository.kt`.

## Ответ API

- Успех: `choices[0].message.content` (строка или массив блоков с `text` — обрабатывает `MessageContentDeserializer`).
- В ответе также: `usage` (prompt_tokens, completion_tokens, total_tokens, completion_tokens_details.reasoning_tokens), `choices[0].finish_reason`.
- Всё это маппится в `ChatResponse` и отображается в UI (текст ответа + строка с токенами и причиной завершения).

## Экран Discussion

- **Поток:** DiscussionScreen → DiscussionViewModel → ChatRepository.solveWithReasoningMode (или compareResponses) → sendWithMessages → OpenAiApi.
- **Навигация:** MainActivity с NavHost; маршруты `chat` и `discussion`; на обоих экранах кнопка «Назад» (popBackStack() на home). Переход между чатом и обсуждением только через главный экран.

## Экран «Версии моделей» (Model Comparison)

- **Поток:** ModelComparisonScreen → ModelComparisonViewModel → ChatRepository.runWithModel(prompt, modelId, displayName) для каждой из трёх моделей (gpt-4o-mini, gpt-4o, gpt-4.1); замер времени и расчёт стоимости в репозитории; опционально compareModelResponses(prompt, runs) для короткого вывода от API.
- **Данные:** sendWithMessages принимает опциональный параметр `model`; результат — ModelRunResult (content, токены, responseTimeMs, costUsd). Константы MODELS_FOR_COMPARISON и таблица цен в ChatRepository.
- **Навигация:** маршрут `modelcomparison`, пункт «Версии моделей» в секции «Промптинг» на главном экране; при загрузке — LoadingOverlay.

## Экран «Агент»

- **Поток:** AgentScreen → AgentViewModel → Agent.process(dialog, request, ..., userProfile, invariantsText) → ChatRepository.sendMessage(..., systemMessage) → OpenAiApi. При выбранном профиле userProfile берётся из storage.getProfileContent(activeProfileId) и передаётся в process; агент подставляет блок в user-промпт и формирует расширенный system message. Агент формирует промпт в зависимости от стратегии (ContextStrategy): Sliding Window — только последние N сообщений; Sticky Facts — блок «Факты» + последние N (перед вызовом агента ViewModel вызывает extractOrUpdateFacts и сохраняет facts); Branching — все сообщения текущей ветки; Summary — summaries + последние N (как День 9). После ответа при Summary вызывается ensureSummaries. Лимит токенов ответа не задаётся.
- **Сохранение контекста (Room):** load(currentBranchId) загружает сообщения по ветке, summaries, facts, список веток и loadedTaskId для этой ветки. save(dialog) сохраняет сообщения текущей ветки; saveFacts — один блок фактов; createBranch(currentBranchId, lastMessageIndex, secondBranchName) — копирование сообщений до checkpoint в новую ветку с заданным именем. saveLoadedTaskIdForBranch(branchId, taskId) — сохранение подключённой задачи по ветке; при загрузке и смене ветки подключённая задача восстанавливается (если задача ещё есть в списке). clear() очищает сообщения, summaries, facts, ветки и пересоздаёт ветку «Основная»; долговременная память и память задач при этом не удаляются. clearTaskMemories(); долговременная память очищается отдельно (кнопка «Очистить» в настройках). — отдельное удаление всех записей памяти задачи. При очистке в UI сбрасываются счётчики токенов. БД версия 5 (миграции 1→2, 2→3, 3→4, 4→5): agent_branches.loadedTaskId; agent_facts, agent_branches, branchId в agent_messages; agent_long_term_memory, agent_task_memories.
- **Модель памяти (День 11):** три слоя: (1) Память диалога — текущие сообщения и стратегия контекста, удаляется при «Очистить историю». (2) Память задачи — таблица agent_task_memories (id, name, content); можно загрузить одну задачу в диалог (её контент подставляется в промпт); чистится кнопкой в настройках или чекбоксом в диалоге очистки. (3) Долговременная память — таблица agent_long_term_memory (одна запись на чат); очищается кнопкой «Очистить» в настройках агента, при «Очистить историю» не трогается. В промпт агента передаются longTermMemory и taskMemory (если задача загружена). Сохранение: преднастройка и редактирование в настройках; команды в поле ввода (/add_long_term текст, /add_task_memory текст — извлечение фактов через LLM и добавление в слой); long-tap по сообщению — извлечь факты и сохранить в выбранный слой. /help — подсказка по командам.
- **Стратегии контекста (День 10):** DataStore: context_strategy (по умолчанию SlidingWindow), last_n_messages. Bottom Sheet «Настройки агента»: выбор стратегии (русские названия, по 2 в ряд), N (5/10/20). Имя новой ветки запрашивается в диалоге при нажатии «Создать ветку», в настройках не хранится. При Sticky Facts — блок «Факты из диалога» (только чтение). При Branching — вкладки (FilterChip) и «Создать ветку» (до 2 веток).
- **Профили пользователя (День 12):** несколько профилей (имя + предпочтения); активный хранится в AgentPreferences (DataStore, active_profile_id). Текст активного профиля подставляется в user-промпт (блок «Профиль пользователя») и дополняет system message в ChatRepository.sendMessage(systemMessage). SimpleAgent.process(..., userProfile) формирует промпт и system message с учётом userProfile. Room: agent_user_profiles (id, name, preferences), AgentUserProfileEntity, AgentUserProfileDao; AgentDialogStorage: getAllProfiles, getProfileContent, saveProfile, deleteProfile. В Bottom Sheet настроек секция «Профиль пользователя» первой: выбор «Без профиля»/профиль, добавление, редактирование, удаление с подтверждением.
- **Инварианты (День 14):** пользователь задаёт в настройках агента текст инвариантов (правила, которые модель не должна нарушать). Хранятся в AgentPreferences (DataStore, agent_invariants). При каждом запросе передаются в SimpleAgent.process(invariantsText) и добавляются в system message блоком «Инварианты» с инструкцией при конфликте — отказ и объяснение. При пустом значении в блок подставляется «Ограничений на ответ нет». Настройки агента разбиты на шесть сворачиваемых блоков (Профиль пользователя, Долговременная память, Память задачи, Стратегия контекста, Инварианты, Отладка); по умолчанию развёрнут только первый.
- **Состояние задачи (День 13):** подключённая задача имеет состояние (этап: Planning → Execution → Validation → Done, currentStep, isPaused). В UI отображаются этап и ожидаемое действие (без счётчика шагов); ожидаемое действие задаётся этапом в коде (TaskStage.expectedActionText()). При загрузке диалога и при loadTaskIntoDialog/switchBranch загружается loadedTaskState; при загрузке из БД если задача на паузе — сразу снимается пауза в UI и БД. При выходе с экрана — onLeaveScreen() выставляет isPaused в БД; при входе — onEnterScreen() сразу снимает паузу в UI и в фоне в БД. confirmTaskResult() при переходе Planning → Execution запускает запрос «Выполняй шаги плана» и по успеху автоматически переводит в Validation с сообщением «[Валидация решения…]» и запросом на проверку (цепочка runAgentRequest с onSuccessChain). /reject не меняет этап: prepareReject() подставляет «/reject » в поле ввода; отправка «/reject» или «/reject комментарий» (rejectWithUserComment) — запрос в модель на повторное выполнение плана с учётом комментария. /reset_planning сбрасывает задачу к Планированию. Очистка диалога без удаления памяти задачи сбрасывает задачу к Планированию. executeCommand — обработка /confirm, /reset_planning, /help, /add_long_term, /add_task_memory; /reject обрабатывается в sendRequest до executeCommand.
- **Слой Room:** AgentMessageEntity (branchId), AgentMessageDao; AgentSummaryEntity, AgentSummaryDao; AgentFactsEntity, AgentFactsDao; AgentBranchEntity (loadedTaskId), AgentBranchDao (setLoadedTaskId); AgentLongTermMemoryEntity, AgentLongTermMemoryDao; AgentTaskMemoryEntity, AgentTaskMemoryDao; AgentUserProfileEntity, AgentUserProfileDao; AppDatabase (version 6); AgentDialogStorage (load/save/…, профили: getAllProfiles, getProfileContent, saveProfile, deleteProfile).
- **UI:** отображение текущей стратегии; кнопка «Настройки» (иконка) рядом с полем ввода; настройки — шесть сворачиваемых блоков (аккордеон), блок «Инварианты» с полем ввода и «Сохранить»; при Facts — блок «Факты из диалога»; при Ветки — вкладки и «Создать ветку»; чат (LazyColumn), «Очистить историю» (диалог с чекбоксом «Также очистить память задачи»), поле ввода (команды /add_long_term, /add_task_memory, /help не уходят в модель), «Превысить контекст» (debug), «Отправить»; long-tap по сообщению — диалог «Извлечь факты и сохранить»; строка токенов; тосты; `imePadding()`, `adjustResize`.
- **Тест превышения контекста (debug):** sendContextOverflowTest() → agent.process(..., forceContextOverflow = true, invariantsText = uiState.invariantsText).
- **Навигация:** маршрут `agent`; на главном экране блок «Агент» с кнопкой «Начать диалог»; при загрузке — LoadingOverlay.
- **Сценарий для ручной проверки стратегий:** см. [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md).

## Главный экран (Home)

- **Старт:** startDestination = `home`; HomeScreen — изображение (drawable) на тему AI/робота, название «Ai Advent Challenge With Love», раскрывающиеся секции (аккордеон).
- **Секции:** «Промптинг» (Чат, Обсуждение, Температура, Версии моделей) и отдельный блок «Агент» (кнопка «Начать диалог»).
- **Переходы:** из home по кнопкам в секциях — на `chat`, `discussion`, `temperature`, `modelcomparison`, `agent`; с экранов кнопка «Назад» — popBackStack() на home.

## Размещение типов

- Вспомогательные классы для стейтов хранить отдельно от ViewModel (отдельный файл, например `*UiState.kt`, или разнесённые типы в пакете экрана).
