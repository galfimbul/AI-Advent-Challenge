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

## Главный экран (Home)

- **Старт:** startDestination = `home`; HomeScreen — изображение (drawable) на тему AI/робота, название «Ai Advent Challenge With Love», раскрывающиеся секции (аккордеон).
- **Переходы:** из home по кнопкам в секции «Промптинг» — на `chat` или `discussion`; с экранов Чат и Обсуждение кнопка «Назад» — popBackStack() на home.

## Размещение типов

- Вспомогательные классы для стейтов хранить отдельно от ViewModel (отдельный файл, например `*UiState.kt`, или разнесённые типы в пакете экрана).
