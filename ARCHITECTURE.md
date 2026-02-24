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

- **Поток:** AgentScreen → AgentViewModel → Agent.process(dialog, request) → ChatRepository.sendMessage → OpenAiApi. ViewModel не вызывает ChatRepository напрямую; логика «запрос → LLM → ответ» инкапсулирована в агенте (domain/agent/SimpleAgent). Агент формирует промпт с контекстом диалога, задаёт свои параметры запроса (maxTokens, stop) и маппит ChatResponse в AgentResponse.
- **Сохранение контекста (Room):** при создании ViewModel в `init` вызывается `AgentDialogStorage.load()` — диалог загружается из БД (таблица `agent_messages`, Entity → domain маппинг) и выставляется в `dialogState` и UI. После каждого успешного ответа агента вызывается `storage.save(dialogState)` — таблица очищается и заполняется заново. Кнопка «Очистить историю» вызывает `storage.clear()` и обнуляет состояние. БД создаётся один раз в `AiAdventChallengeApplication.onCreate()` и хранится в поле `database`; фабрика ViewModel получает её из `(context.applicationContext as AiAdventChallengeApplication).database`.
- **Слой Room:** `data/agent/` — AgentMessageEntity (id, role, text, sortOrder), AgentMessageDao (getAllMessages, insertAll, deleteAll), AppDatabase, AgentDialogStorage (load/save/clear с маппингом в domain).
- **UI:** чат — история диалога в виде прокручиваемого списка сообщений (LazyColumn), пузырьки «Вы» / «Агент»; кнопка «Очистить историю»; поле ввода и кнопка «Отправить» внизу; `imePadding()` и `adjustResize` в манифесте.
- **Навигация:** маршрут `agent`; на главном экране отдельный блок «Агент» с кнопкой «Начать диалог»; при загрузке — LoadingOverlay.

## Главный экран (Home)

- **Старт:** startDestination = `home`; HomeScreen — изображение (drawable) на тему AI/робота, название «Ai Advent Challenge With Love», раскрывающиеся секции (аккордеон).
- **Секции:** «Промптинг» (Чат, Обсуждение, Температура, Версии моделей) и отдельный блок «Агент» (кнопка «Начать диалог»).
- **Переходы:** из home по кнопкам в секциях — на `chat`, `discussion`, `temperature`, `modelcomparison`, `agent`; с экранов кнопка «Назад» — popBackStack() на home.

## Размещение типов

- Вспомогательные классы для стейтов хранить отдельно от ViewModel (отдельный файл, например `*UiState.kt`, или разнесённые типы в пакете экрана).
