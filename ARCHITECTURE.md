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

- **Поток:** AgentScreen → AgentViewModel → Agent.process(dialog, request, contextStrategy, lastN) → ChatRepository.sendMessage → OpenAiApi. Агент формирует промпт в зависимости от стратегии (ContextStrategy): Sliding Window — только последние N сообщений; Sticky Facts — блок «Факты» + последние N (перед вызовом агента ViewModel вызывает extractOrUpdateFacts и сохраняет facts); Branching — все сообщения текущей ветки; Summary — summaries + последние N (как День 9). После ответа при Summary вызывается ensureSummaries. Лимит токенов ответа не задаётся.
- **Сохранение контекста (Room):** load(currentBranchId) загружает сообщения по ветке, summaries, facts, список веток. save(dialog) сохраняет сообщения текущей ветки; saveFacts — один блок фактов; createBranch(currentBranchId, lastMessageIndex, secondBranchName) — копирование сообщений до checkpoint в новую ветку с заданным именем. clear() очищает всё и пересоздаёт ветку «Основная»; при очистке в UI сбрасываются также счётчики токенов. БД версия 3 (миграции 1→2, 2→3): agent_facts, agent_branches, branchId в agent_messages.
- **Стратегии контекста (День 10):** DataStore: context_strategy (по умолчанию SlidingWindow), last_n_messages. Bottom Sheet «Настройки агента»: выбор стратегии (русские названия, по 2 в ряд), N (5/10/20). Имя новой ветки запрашивается в диалоге при нажатии «Создать ветку», в настройках не хранится. При Sticky Facts — блок «Факты из диалога» (только чтение). При Branching — вкладки (FilterChip) и «Создать ветку» (до 2 веток).
- **Слой Room:** AgentMessageEntity (branchId), AgentMessageDao (getMessagesByBranch, deleteByBranch); AgentSummaryEntity, AgentSummaryDao; AgentFactsEntity, AgentFactsDao; AgentBranchEntity, AgentBranchDao; AppDatabase (version 3); AgentDialogStorage (load/save/saveFacts/insertSummary/createBranch/clear).
- **UI:** отображение текущей стратегии; кнопка «Настройки» (иконка) рядом с полем ввода; при Facts — блок «Факты из диалога»; при Ветки — вкладки и «Создать ветку»; чат (LazyColumn), «Очистить историю», поле ввода, «Превысить контекст» (debug), «Отправить»; строка токенов; тосты; `imePadding()`, `adjustResize`.
- **Тест превышения контекста (debug):** sendContextOverflowTest() → agent.process(..., forceContextOverflow = true).
- **Навигация:** маршрут `agent`; на главном экране блок «Агент» с кнопкой «Начать диалог»; при загрузке — LoadingOverlay.
- **Сценарий для ручной проверки стратегий:** см. [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md).

## Главный экран (Home)

- **Старт:** startDestination = `home`; HomeScreen — изображение (drawable) на тему AI/робота, название «Ai Advent Challenge With Love», раскрывающиеся секции (аккордеон).
- **Секции:** «Промптинг» (Чат, Обсуждение, Температура, Версии моделей) и отдельный блок «Агент» (кнопка «Начать диалог»).
- **Переходы:** из home по кнопкам в секциях — на `chat`, `discussion`, `temperature`, `modelcomparison`, `agent`; с экранов кнопка «Назад» — popBackStack() на home.

## Размещение типов

- Вспомогательные классы для стейтов хранить отдельно от ViewModel (отдельный файл, например `*UiState.kt`, или разнесённые типы в пакете экрана).
