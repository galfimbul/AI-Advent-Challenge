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
- **Навигация:** Jetpack Navigation Compose; маршруты `chat` и `discussion`; переход между экранами — только через главный экран.

## Главный экран (ветка `home-screen`)

- **HomeScreen:** стартовая точка приложения; изображение (drawable) на тему AI и робота, название «Ai Advent Challenge With Love», раскрывающиеся блоки по темам с кнопками навигации.
- **Маршруты:** `home` (startDestination), `chat`, `discussion`; на экранах Чат и Обсуждение кнопка «Назад» — popBackStack() на home; перехода между чатом и обсуждением напрямую нет.
- **Секции:** данные в `ui.home.HomeNav` (HomeSection, HomeNavItem, HOME_SECTIONS); первый блок «Промптинг» с пунктами Чат и Обсуждение.
- **Аккордеон:** одна открытая секция, AnimatedVisibility для раскрытия/сворачивания.
- **Картинка:** 70% ширины экрана, высота пропорциональна (aspectRatio), прозрачный фон контейнера.

## Температура (экран «Температура»)

- **API:** в `ChatCompletionRequest` добавлен параметр `temperature` (Float, 0–2); в `ChatRepository.sendWithMessages` — опциональный аргумент `temperature`; без лимита токенов для запросов с температурой; при temperature > 1.7 — лимит 800 токенов.
- **ChatRepository:** таймаут 90 с (было 60); `sendWithTemperature(prompt, temperature)`, `compareTemperatureResponses(prompt, runs)` — сравнение по точности, креативности, разнообразию и рекомендации по настройкам.
- **domain:** `TemperaturePreset.kt` — константы `MIN_TEMP`, `MAX_TEMP`, `TEMP_STEP` (0, 2, 0.1), пресеты с подписями «Название (значение)», `labelForTemperature(temp)`.
- **ui/temperature:** `TemperatureScreen`, `TemperatureViewModel`, `TemperatureUiState`, `TemperatureRun`; ползунок 0–2 (шаг 0.1, 21 значение), пресет-кнопки, «Запустить все три», список ответов с возможностью сворачивания карточек, кнопка «Сравнить» внизу при ≥2 запусках; при смене промпта — очистка ответов и сравнения.
- **ui/components:** `LoadingOverlay(visible)` — переиспользуемый полноэкранный оверлей с лоудером по центру, перекрывает системные панели, светлый фон (alpha 0.6); используется на экране «Температура».
- **Навигация:** маршрут `temperature`, пункт «Температура» в секции «Промптинг» на главном экране.
- **Правила:** в `.cursor/rules/aiadvent.mdc` — при создании новых файлов агент добавляет их в git.

## День 5 (Версии моделей)

- **Экран «Версии моделей»:** один запрос выполняется на трёх моделях OpenAI (gpt-4o-mini, gpt-4o, gpt-4.1); замер времени ответа, отображение токенов и ориентировочной стоимости; карточки с ответами, кнопка «Сравнить» для короткого вывода от API; блок «Ссылки» (Модели OpenAI, Тарифы).
- **Data:** в `ChatRepository.sendWithMessages` добавлен параметр `model: String? = null`; тип `ModelRunResult` (modelId, displayName, content, promptTokens, completionTokens, totalTokens, responseTimeMs, costUsd); константы `MODELS_FOR_COMPARISON` и таблица цен за 1M токенов; `runWithModel(userMessage, modelId, displayName)` с замером времени и расчётом стоимости; `compareModelResponses(prompt, runs)` для сравнения ответов трёх моделей.
- **ui/modelcomparison:** `ModelComparisonScreen`, `ModelComparisonViewModel`, `ModelComparisonUiState`; при загрузке — `LoadingOverlay`; ссылки открываются через Intent.ACTION_VIEW.
- **Навигация:** маршрут `modelcomparison`, пункт «Версии моделей» в секции «Промптинг» на главном экране.

## День 6 (Первый агент)

- **domain/agent:** сущность «Агент» — `SimpleAgent(ChatRepository)`, метод `process(dialog, userRequest): Result<AgentResponse>`. Типы `AgentResponse` (reply, dialog, raw), `AgentDialogState`, `AgentMessage`, `AgentRole`. Агент формирует промпт с историей диалога, вызывает `repository.sendMessage`, маппит ответ в `AgentResponse`; параметры запроса (maxTokens, stop) заданы внутри агента.
- **ui/agent:** `AgentUiState` (messages — история диалога, request, isLoading, error, токены), `AgentViewModel` (вызов только `agent.process()`, без прямого обращения к ChatRepository), `AgentScreen` — экран в виде чата: прокручиваемая история сообщений (LazyColumn, пузырьки «Вы» / «Агент»), поле ввода и кнопка «Отправить» внизу, `LoadingOverlay`.
- **Навигация:** маршрут `agent`; на главном экране отдельный блок «Агент» с кнопкой «Начать диалог» (не в секции «Промптинг»).
- **Клавиатура:** `Modifier.imePadding()` на экране «Агент» и `android:windowSoftInputMode="adjustResize"` в манифесте — кнопка «Отправить» остаётся видимой и нажимаемой при открытой клавиатуре.

## День 7 (Сохранение контекста диалога агента)

- **Room:** диалог с агентом сохраняется между перезапусками приложения. Таблица `agent_messages` (Entity: id, role, text, sortOrder); DAO: getAllMessages (ORDER BY sortOrder), insertAll, deleteAll); AppDatabase (version 1).
- **data/agent:** `AgentMessageEntity`, `AgentMessageDao`, `AppDatabase`, `AgentDialogStorage` (load/save/clear с маппингом Entity ↔ domain AgentMessage/AgentDialogState).
- **Application:** `AiAdventChallengeApplication` — в `onCreate()` один раз создаётся и сохраняется в поле `database` экземпляр БД; в манифесте `android:name=".AiAdventChallengeApplication"`.
- **ViewModel:** `AgentViewModelFactory` получает БД из Application, создаёт `AgentDialogStorage` и передаёт в `AgentViewModel`. В `init` ViewModel загружает диалог из storage и выставляет в UI; после каждого успешного ответа агента сохраняет диалог в storage. Метод `clearDialog()` — очистка в storage и обнуление состояния.
- **UI:** кнопка «Очистить историю» на экране агента (активна при непустой истории и не во время загрузки).
- **Зависимости:** Room (runtime, ktx, compiler), KSP в libs.versions.toml и app/build.gradle.kts.

## День 8 (Тест превышения контекста)

- **SimpleAgent:** параметр `process(dialog, userRequest, forceContextOverflow: Boolean = false)`. При `forceContextOverflow == true` к промпту дописывается большой блок текста (~500k символов), запрос превышает лимит контекста API — для проверки обработки ошибки.
- **AgentViewModel:** метод `sendContextOverflowTest()` — вызывает агента с фиксированным сообщением «Тест: превышение контекста» и `forceContextOverflow = true`; при ошибке добавляет это сообщение в чат и показывает текст ошибки.
- **AgentScreen:** кнопка «Превысить контекст» (видна только при `BuildConfig.DEBUG`), слева от кнопки «Отправить». Позволяет в обычном диалоге нажать и увидеть реакцию приложения на превышение контекста.
