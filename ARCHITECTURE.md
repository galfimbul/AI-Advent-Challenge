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

- **Поток:** AgentScreen → AgentViewModel → Agent.process(dialog, request, useCompression, lastN) → ChatRepository.sendMessage → OpenAiApi. ViewModel не вызывает ChatRepository для основного чата; логика «запрос → LLM → ответ» инкапсулирована в агенте (domain/agent/SimpleAgent). Агент формирует промпт: без сжатия — вся история; со сжатием — «Краткое содержание более ранней части диалога» (summaries по порядку) + «Актуальная часть диалога» (последние N сообщений целиком; пересечение с последним summary-блоком допускается). После успешного ответа при включённом сжатии ViewModel вызывает ensureSummaries (repository.summarizeDialog для каждого недостающего блока по 10 сообщений, storage.insertSummary); сообщения из БД не удаляются.
- **Сохранение контекста (Room):** при создании ViewModel в `init` вызывается `AgentDialogStorage.load()` — диалог загружается из БД (таблицы `agent_messages` и `agent_summaries`, Entity → domain маппинг) и выставляется в `dialogState` (summaries + messages) и UI. После каждого успешного ответа вызывается `storage.save(dialogState)` (только сообщения); при сжатии затем генерируются и сохраняются недостающие summaries. Кнопка «Очистить историю» вызывает `storage.clear()` (сообщения и summaries) и обнуляет состояние. БД версия 2, миграция 1→2 создаёт таблицу `agent_summaries`; фабрика передаёт в ViewModel storage (оба DAO), ChatRepository, AgentCompressionPreferences.
- **Сжатие контекста (День 9):** настройки в DataStore (AgentCompressionPreferences): `use_compression` (Boolean), `last_n_messages` (Int, 1–100). В промпт при сжатии идут все summaries + последние N сообщений; суммаризация — блоки по 10 с начала (needSummaries = floor(M/10)); та же модель, что и чат; лимит токенов ответа (max_completion_tokens) для агента и суммаризации не задаётся. Тосты: «Сжатие контекста…» в начале и «Сжатие контекста завершено» после добавления хотя бы одного summary (только при реальной работе ensureSummaries). Сравнение расхода токенов: в UI под строкой токенов выводится режим «(со сжатием)» или «(без сжатия)» для последнего запроса.
- **Слой Room:** `data/agent/` — AgentMessageEntity, AgentMessageDao; AgentSummaryEntity, AgentSummaryDao; AppDatabase (version 2, MIGRATION_1_2); AgentDialogStorage (load/save/insertSummary/clear).
- **UI:** переключатель «Без сжатия» / «Со сжатием»; при сжатии — компактные кнопки выбора N (5, 10, 20); тосты по AgentUiState.toastMessage; чат (LazyColumn), кнопка «Очистить историю», поле ввода, кнопка «Превысить контекст» (только debug), «Отправить»; строка токенов с указанием режима; `imePadding()`, `adjustResize`.
- **Тест превышения контекста (debug):** кнопка «Превысить контекст» вызывает `sendContextOverflowTest()` → `agent.process(..., forceContextOverflow = true)`. Токены на экране — из `usage` в ответе API.
- **Навигация:** маршрут `agent`; на главном экране отдельный блок «Агент» с кнопкой «Начать диалог»; при загрузке — LoadingOverlay.

## Главный экран (Home)

- **Старт:** startDestination = `home`; HomeScreen — изображение (drawable) на тему AI/робота, название «Ai Advent Challenge With Love», раскрывающиеся секции (аккордеон).
- **Секции:** «Промптинг» (Чат, Обсуждение, Температура, Версии моделей) и отдельный блок «Агент» (кнопка «Начать диалог»).
- **Переходы:** из home по кнопкам в секциях — на `chat`, `discussion`, `temperature`, `modelcomparison`, `agent`; с экранов кнопка «Назад» — popBackStack() на home.

## Размещение типов

- Вспомогательные классы для стейтов хранить отдельно от ViewModel (отдельный файл, например `*UiState.kt`, или разнесённые типы в пакете экрана).
