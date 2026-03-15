# План: День 18 — Напоминалки (MCP + локальные уведомления)

Итоговый план со всеми уточнениями. Ветка: `challenge_day_18`.

---

## Цель

- Пользователь в чате: «Напомни через 30 минут купить молоко» → модель вызывает инструмент → **MCP-сервер** сохраняет факт (сообщение, время срабатывания); **приложение** ставит локальное уведомление через AlarmManager.
- Вопрос «Сколько напоминалок?» / «Какие напоминания?» → модель вызывает инструмент → приложение запрашивает **MCP** → возвращается список с сервера, модель отвечает пользователю.

Отложенная выдача (уведомление в момент T) + полезность для пользователя + агрегированный ответ (список с сервера).

---

## Принятые решения


| Вопрос                                | Решение                                                                                                                                             |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Хранение на MCP-сервере               | **SQLite** — одна таблица (id, message, scheduled_at, created_at). Зависимость: например `org.xerial:sqlite-jdbc`.                                  |
| Планирование уведомления в приложении | **AlarmManager** (setExactAndAllowWhileIdle / setAlarmClock) для точного времени. WorkManager не использовать.                                      |
| Имена инструментов и параметров       | **Единый источник** — один файл/объект с константами; список `tools` для API и ветки в `runToolCall` строятся из этих констант, без хардкода строк. |
| После перезагрузки устройства         | **Не переустанавливаем** напоминания (AlarmManager сбрасывается). Ограничение принимаем.                                                            |


---

## Поток данных

1. Пользователь: «Напомни через 30 минут про молоко».
2. Модель возвращает `tool_calls`: `schedule_reminder`(message, in_minutes).
3. Приложение: вызвать MCP `register_reminder` → сервер пишет в SQLite; затем вызвать `ReminderScheduler.scheduleReminder(30, "молоко")` → AlarmManager + уведомление через 30 мин.
4. Пользователь: «Сколько у меня напоминалок?»
5. Модель возвращает `tool_calls`: `get_reminders`.
6. Приложение вызывает MCP `get_reminders` → сервер отдаёт список из SQLite → модель формулирует ответ.

---

## 1. MCP-сервер (модуль `mcp-server/`)

- **Хранилище:** SQLite, одна таблица:
  - `id` (INTEGER PRIMARY KEY AUTOINCREMENT или UUID)
  - `message` (TEXT)
  - `scheduled_at` (TEXT ISO-8601 или INTEGER Unix ms)
  - `created_at` (TEXT/INTEGER, опционально)
- Инициализация: при старте приложения создать БД и таблицу, если её нет (например в `ReminderStorage.kt` или в `Main.kt`).
- **Инструменты:**
  - `register_reminder`(message: string, in_minutes: number) — вычислить `scheduled_at = now + in_minutes`, INSERT, вернуть текст подтверждения.
  - `get_reminders`() — SELECT из таблицы, вернуть список (текст или JSON) для модели.
- Файлы: `Main.kt` (добавить оба инструмента), слой доступа к БД (например `ReminderStorage.kt`). В `build.gradle.kts` добавить зависимость на SQLite JDBC.

---

## 2. Приложение (Android)

### 2.1. Единый источник констант инструментов

- **Файл:** `app/.../data/AgentToolConstants.kt`
- Содержит:
  - Имена инструментов: `MOCK_ECHO`, `GET_CURRENT_WEATHER`, `SCHEDULE_REMINDER`, `GET_REMINDERS` (значения строк: `"mock_echo"`, `"get_current_weather"`, `"schedule_reminder"`, `"get_reminders"`).
  - Имена параметров: `MESSAGE`, `IN_MINUTES`, `CITY`, `LANG` и т.д.
- В `AgentTools.kt`: список `tools` для OpenAI собирать из этих констант (описания и параметры используют константы).
- В `SimpleAgent.runToolCall`: `when (name)` по константам (например `AgentToolConstants.SCHEDULE_REMINDER`), разбор аргументов по константам параметров.

### 2.2. MCP-клиент

- В `McpCustomClient.kt`: метод `callTool(toolName: String, arguments: Map<String, Any>): String` (универсальный вызов). `callMockEcho` оставить как обёртку над `callTool(MOCK_ECHO, ...)`.
- Для `schedule_reminder`: вызывать на сервере инструмент `register_reminder`(message, in_minutes). Для `get_reminders`: вызывать `get_reminders`().

### 2.3. Напоминания в агенте

- Интерфейс `ReminderScheduler`: `fun scheduleReminder(inMinutes: Int, message: String)` (в domain или data).
- Реализация `AppReminderScheduler` (в app):
  - **AlarmManager:** setExactAndAllowWhileIdle (API 31+) / setExact; время = `currentTimeMillis() + inMinutes * 60_000L`. При необходимости поддержать «через 30 секунд» — отдельный параметр или один `delay_seconds`.
  - При срабатывании: PendingIntent → BroadcastReceiver (или другой способ); в Receiver показать **Notification** (NotificationManager), канал для напоминаний создать при инициализации (Application или при первом schedule).
- В `SimpleAgent.runToolCall`: для `SCHEDULE_REMINDER` — вызов MCP `register_reminder`, затем `reminderScheduler?.scheduleReminder(inMinutes, message)`; для `GET_REMINDERS` — вызов MCP `get_reminders`, вернуть ответ.
- Передача: `AgentViewModelFactory` создаёт `AppReminderScheduler(applicationContext)`, передаёт в `SimpleAgent.process(..., reminderScheduler)`.

### 2.4. Разрешения и канал

- API 33+: разрешение POST_NOTIFICATIONS (запрос при первом использовании напоминаний или при старте).
- Создать NotificationChannel для напоминаний при первом запуске или при первом schedule.

---

## 3. Документация

- **CHANGELOG.md** — пункт «День 18: Напоминалки»: MCP register_reminder / get_reminders, SQLite на сервере, AlarmManager и уведомления в приложении, константы инструментов.
- **ARCHITECTURE.md** — кратко: запрос «напомни через N минут» → schedule_reminder → регистрация на MCP + уведомление в приложении; запрос о списке → get_reminders → ответ с сервера.
- **PROJECT.md** — строка про День 18 в таблице «Где что искать».

---

## Порядок реализации

1. **AgentToolConstants** — константы имён инструментов и параметров; рефакторинг `AgentTools.kt` и `SimpleAgent.kt` на использование констант (в т.ч. mock_echo, get_current_weather).
2. **MCP-сервер** — SQLite (таблица, инициализация), инструменты `register_reminder`, `get_reminders`.
3. **Приложение** — `McpCustomClient.callTool`; в константы и в агент добавить `schedule_reminder`, `get_reminders`; интерфейс `ReminderScheduler`, `AppReminderScheduler` (AlarmManager + Notification), передача в агент и вызов из `runToolCall`.
4. **Документация** — отдельным коммитом после кода (по правилам проекта).

---

## Дополнения после реализации

- **Точное время:** планирование через `setAlarmClock` (иконка в статус-баре); при `SecurityException` (нет SCHEDULE_EXACT_ALARM) — fallback на `setAndAllowWhileIdle`. В манифесте: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM.
- **Запрос разрешений:** при старте приложения (MainActivity) запрос POST_NOTIFICATIONS (API 33+).
- **Таймзона:** при вызове get_reminders приложение передаёт `userTimezone` (TimeZone.getDefault().id); MCP get_reminders принимает опциональный параметр `timezone` и возвращает время в этой таймзоне. SimpleAgent.process(..., userTimezone).
- **Логи:** AppReminderScheduler и ReminderReceiver — теги для отладки срабатывания и показа уведомлений.

