# Changelog

Технические изменения по дням. Описание фич для пользователя — в [README.md](README.md).

## День 1 (ветка `challenge_day_1`)

- Добавлены: `ChatRepository`, `OpenAiApi`, `OpenAiDto` (базовый запрос/ответ).
- Добавлены: `ChatScreen`, `ChatViewModel` — один запрос, один ответ.
- Ключ: `secret.properties` → BuildConfig, не коммитится.
- Логирование: OkHttp LoggingInterceptor, тег `OpenAI`.
- Таймаут 60 с, разрешение INTERNET.

## День 2 (ветка `challenge_day_2`)

- **Запрос:** system message, `max_completion_tokens`, `stop` (список строк); модель `gpt-4.1`.
- **ChatRepository:** константы `MAX_TOKENS`, `STOP_SEQUENCE` («Закончил ответ»); `sendMessage(userMessage, maxTokens, stopPhrases)`; возврат `Result<ChatResponse>` с usage и finish_reason.
- **OpenAiDto:** `Usage`, `CompletionTokensDetails`; `MessageContentDeserializer` для content (строка или массив с `text`).
- **UI:** настройки (макс. токенов, чекбокс «Без ограничения»), отображение токенов и причины завершения.
- Обработка пустого ответа при `finish_reason == "length"` (reasoning-модели).

## День 3 (ветка `challenge_day_3`)

- **Экран Discussion:** одна задача решается четырьмя способами (прямой ответ, пошагово, свой промпт, эксперты); 4 блока ответов, кнопка «Сравнить».
- **ChatRepository:** вынесена общая логика в `sendWithMessages(messages, maxTokens, stop)`; добавлены `solveWithReasoningMode(task, ReasoningMode)` и `compareResponses(task, direct, stepByStep, selfPrompt, experts)`.
- **domain:** enum `ReasoningMode` (Direct, StepByStep, SelfPrompt, Experts) — не в data, по соглашению о размещении типов.
- **ui/discussion:** `DiscussionScreen`, `DiscussionViewModel`, `DiscussionUiState`.
- **Навигация:** Jetpack Navigation Compose; маршруты `chat` и `discussion`; кнопка «Обсуждение» на чате, «К чату» на экране Discussion.
