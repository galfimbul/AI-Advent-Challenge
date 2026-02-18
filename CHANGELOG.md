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
