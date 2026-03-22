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

## День 9 (Управление контекстом)

- **Сжатие истории:** в промпт для LLM при включённом сжатии идут summaries блоков по 10 сообщений (с начала диалога) + последние N сообщений целиком. N настраивается (5, 10, 20). Пересечение последнего summary-блока с «последние N» допускается. Все сообщения по-прежнему хранятся в `agent_messages` и отображаются в чате; из БД ничего не удаляется.
- **Room:** таблица `agent_summaries` (AgentSummaryEntity: id, text, sortOrder), AgentSummaryDao (getAllSummaries, insert, deleteAll). AppDatabase version 2, миграция 1→2. AgentDialogStorage: load() возвращает AgentDialogState(summaries, messages), save() — только сообщения, insertSummary(text, sortOrder), clear() — сообщения и summaries.
- **ChatRepository:** метод `summarizeDialog(messages: List<AgentMessage>): Result<String>` — один вызов API с системным промптом «суммаризатор», та же модель (gpt-4.1); лимит токенов ответа не задаётся (maxTokens = null).
- **Domain:** AgentDialogState(summaries, messages). SimpleAgent.process(..., useCompression, lastN) — формирование промпта из summaries + последние N при useCompression; запрос к LLM без лимита max_completion_tokens.
- **ViewModel:** загрузка настроек сжатия из AgentCompressionPreferences (DataStore) в init; после успешного ответа при useCompression — ensureSummaries (генерация недостающих summaries по блокам по 10, сохранение в storage); setUseCompression, setLastN с сохранением в DataStore. Тосты сжатия только при реальной суммаризации: «Сжатие контекста…» в начале, «Сжатие контекста завершено» после добавления хотя бы одного summary; clearToastMessage().
- **UI:** переключатель «Без сжатия» / «Со сжатием»; при сжатии — компактные кнопки выбора N (5, 10, 20). Строка токенов с подписью режима «(со сжатием)» / «(без сжатия)». Тосты через AgentUiState.toastMessage и LaunchedEffect в AgentScreen.
- **Зависимости:** DataStore Preferences (libs.versions.toml, app/build.gradle.kts).

## День 10 (Стратегии управления контекстом)

- **Четыре стратегии:** Sliding Window (только последние N сообщений), Sticky Facts (блок facts + последние N), Branching (две ветки от checkpoint, все сообщения текущей ветки), Summary (summaries + последние N, режим Дня 9).
- **Domain:** enum ContextStrategy (SlidingWindow, StickyFacts, Branching, Summary) с отображаемыми именами на русском («Скользящее окно», «Факты», «Ветки», «Сжатие»); BranchInfo(id, name), AgentDialogState расширен (facts, currentBranchId, branches). SimpleAgent.process(dialog, request, contextStrategy, lastN) — формирование промпта по стратегии; для StickyFacts в промпт добавлена инструкция учитывать блок «Факты». Лимит токенов ответа не задаётся.
- **DataStore:** ключи context_strategy (по умолчанию SlidingWindow), last_n_messages. AgentCompressionSettings: contextStrategy, lastN, useCompression (имя второй ветки не хранится — запрашивается в диалоге при создании).
- **Room:** таблица agent_facts (id, factsText), AgentFactsDao; таблица agent_branches (id, name, checkpointAt), AgentBranchDao; в agent_messages добавлено поле branchId. Миграция 2→3. AgentDialogStorage: load(currentBranchId), save(dialog), saveFacts(factsText), createBranch(currentBranchId, lastMessageIndex, secondBranchName), clear() с пересозданием ветки «Основная».
- **ChatRepository:** extractOrUpdateFacts(currentFacts, messages) — один вызов LLM для извлечения/обновления фактов (без лимита токенов).
- **ViewModel:** при StickyFacts перед ответом агента — extractOrUpdateFacts, сохранение facts; при Summary — ensureSummaries после ответа. switchBranch(branchId), createBranch(branchName), openCreateBranchDialog/dismissCreateBranchDialog/setCreateBranchNameInput, openSettingsSheet/closeSettingsSheet. При clearDialog() сбрасываются также promptTokens, completionTokens, totalTokens, lastTokensModeCompression.
- **UI:** отображение текущей стратегии (русские названия); кнопка «Настройки» (иконка) → Bottom Sheet «Настройки агента» (стратегии по 2 в ряд, N 5/10/20; имя новой ветки не в настройках). При Branching: «Создать ветку» открывает диалог ввода имени, затем createBranch(name). При Facts — блок «Факты из диалога» (только чтение). При Ветки — вкладки (FilterChip) и «Создать ветку».
- **Документация:** сценарий из 13 сообщений для ручной проверки стратегий — в [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md); ссылка из PROJECT.md и раздела «Агент» в ARCHITECTURE.md.
- **Рекомендация:** прогонять сценарий из docs/AGENT_TEST_SCENARIO.md по каждой стратегии и сравнивать качество ответа, стабильность, расход токенов, удобство.

## День 11 (Модель памяти ассистента)

- **Три слоя памяти:** (1) Память диалога — сообщения и стратегия контекста, живёт только в сессии, удаляется при «Очистить историю». (2) Память задачи — отдельное хранилище (agent_task_memories), опционально загружается в диалог; чистится отдельной кнопкой или чекбоксом при очистке сессии. (3) Долговременная память — один блок на чат (agent_long_term_memory); очищается кнопкой «Очистить» в настройках агента, при «Очистить историю» не трогается.
- **Room, миграция 3→4:** таблицы agent_long_term_memory (id, content), agent_task_memories (id, name, content). AgentLongTermMemoryDao, AgentTaskMemoryDao. AgentDialogStorage.clear() не трогает эти таблицы; clearTaskMemories() — удаление всех записей задач.
- **ChatRepository:** extractFactsFromText(text: String): Result<String> — извлечение фактов из произвольного текста (для команд и long-tap).
- **Domain:** SimpleAgent.process(..., longTermMemory, taskMemory) — в начале промпта блоки «Долговременная память» и «Память текущей задачи» (если не пусто).
- **ViewModel:** загрузка longTerm и списка задач в init и при открытии настроек; передача в agent.process; saveLongTermMemory, clearLongTermMemory, saveTaskMemory, loadTaskIntoDialog, unloadTaskFromDialog, clearTaskMemories; openClearConfirmDialog, confirmClearDialog(alsoTaskMemory); разбор команд в поле ввода: /add_long_term текст, /add_task_memory текст (в загруженную задачу), /help; addFactsToLongTermFromMessage, addFactsToTaskFromMessage (long-tap).
- **UI:** «Очистить историю» → диалог подтверждения с чекбоксом «Также очистить память задачи». В Bottom Sheet «Настройки агента»: секции «Долговременная память» (поле + Сохранить + Очистить), «Память задачи» (список, загрузить в диалог/отключить, добавить задачу, очистить память задачи). Long-tap по сообщению → диалог «Извлечь факты и сохранить» → «В долговременную память» / «В задачу: имя». Команды в поле ввода не отправляются в модель — выполняются действия с памятью; /help — тост с подсказкой.
- **Документация:** план в [docs/PLAN_DAY_11_MEMORY.md](docs/PLAN_DAY_11_MEMORY.md).

### Подключённая задача по ветке (после Дня 11)

- **Сохранение по ветке:** для каждой ветки диалога хранится id подключённой задачи (loadedTaskId). При перезаходе в приложение или при переключении на другую ветку ранее подключённая к этому диалогу задача автоматически подставляется (если задача не удалена).
- **Room, миграция 4→5:** в таблицу `agent_branches` добавлена колонка `loadedTaskId INTEGER NULL`. AgentBranchEntity.loadedTaskId; AgentBranchDao.setLoadedTaskId(branchId, taskId).
- **AgentDialogState:** поле `loadedTaskId: Long?` — возвращается из storage.load() для текущей ветки.
- **AgentDialogStorage:** load() заполняет loadedTaskId из branchDao.getBranchById; saveLoadedTaskIdForBranch(branchId, taskId) — сохранение при подключении/отключении задачи.
- **ViewModel:** в init и при switchBranch восстанавливается loadedTaskId из загруженного диалога (только если задача с этим id ещё есть в списке задач). loadTaskIntoDialog / unloadTaskFromDialog перед обновлением UI вызывают saveLoadedTaskIdForBranch(currentBranchId, …).
- **Bottom Sheet настроек:** ограничение высоты листа (80% экрана), отступ под status bar (WindowInsets.systemBars), контент заполняет лист без пустого пространства снизу.

## День 12 (Персонализация ассистента — профили пользователя)

- **Профили пользователя:** несколько профилей (имя + текст предпочтений: стиль, формат, ограничения); переключение активного, добавление, редактирование, удаление. Вариант C: текст активного профиля подставляется в user-промпт (блок «Профиль пользователя») и дополняет system message («Учитывай предпочтения пользователя…»).
- **AgentPreferences (DataStore):** переименование с AgentCompressionPreferences; ключи context_strategy, last_n_messages; добавлен active_profile_id (0 = «без профиля», иначе id профиля). Хранение активного профиля в AgentPreferences.
- **Room, миграция 5→6:** таблица agent_user_profiles (id, name, preferences). AgentUserProfileEntity, AgentUserProfileDao (getAll, getById, insert, update, deleteById). AgentDialogStorage: getAllProfiles(), getProfileContent(id), saveProfile(id?, name, preferences), deleteProfile(id). При добавлении профиля он сразу становится активным.
- **ChatRepository:** константа DEFAULT_SYSTEM_MESSAGE вынесена в companion; sendMessage(..., systemMessage: String? = null, ...) — при переданном systemMessage используется он, иначе DEFAULT_SYSTEM_MESSAGE.
- **SimpleAgent:** process(..., userProfile: String = ""). При непустом userProfile: в начало user-промпта — блок «Профиль пользователя (учитывай в ответах…):» + текст; system message = DEFAULT_SYSTEM_MESSAGE + дополнение про предпочтения + userProfile; вызов repository.sendMessage(..., systemMessage = systemMessage).
- **ViewModel и UiState:** profiles, activeProfileId, profileEditorId, profileEditorName, profileEditorPreferences; loadProfiles(), setActiveProfile(id), addProfile(name, preferences) → setActiveProfile(newId), openProfileEditor(id), updateProfileEditorName/Preferences, saveProfileEditor(), closeProfileEditor(), deleteProfile(id). В sendRequest() передаётся userProfile из storage.getProfileContent(activeProfileId) в agent.process(..., userProfile).
- **UI:** на экране диалога под стратегией отображается текущий профиль («Профиль: <имя>» или «Профиль: Без профиля»). В Bottom Sheet настроек секция «Профиль пользователя» первой: заголовок; чип «Без профиля»; список профилей (чип выбора, «Изменить», «Удалить» с подтверждением); «Добавить профиль» (форма: название + предпочтения, Сохранить/Отмена); при открытом редакторе — форма редактирования (имя, предпочтения, Сохранить/Отмена/Удалить). AlertDialog для подтверждения удаления.
- **Документация:** README (секция «Профиль пользователя» в настройках агента), ARCHITECTURE и PROJECT (слой профилей, entity/DAO/Storage, подстановка в агенте и репозитории).

## День 13 (Состояние задачи — Task State Machine)

- **Конечный автомат задачи:** этапы Planning → Execution → Validation → Done. Пользователь только формирует задачу; переход по этапам только по подтверждению (/confirm) или отказу (/reject). Ожидаемое действие задаётся этапом в коде (не хранится в БД). currentStep инкрементируется только на этапе Execution при /confirm.
- **Domain:** [TaskStage.kt](app/src/main/java/com/example/aiadventchallenge/domain/agent/TaskStage.kt) — enum Planning, Execution, Validation, Done; displayName(), expectedActionText(), asString(); taskStageFromString(); data class TaskState(stage, currentStep, isPaused).
- **Room, миграция 6→7:** в agent_task_memories добавлены колонки stage (TEXT), currentStep (INTEGER), isPaused (INTEGER). AgentTaskMemoryDao.updateTaskState(id, stage, currentStep, isPaused). При создании задачи — дефолты planning, 0, 0.
- **Storage:** getTaskState(id): TaskState?; updateTaskState(id, stage, currentStep, isPaused). Ожидаемое действие вычисляется по этапу в коде.
- **SimpleAgent:** параметр taskState: TaskState?; в блок «Память текущей задачи» добавлен подблок: этап, шаг, ожидаемое действие (stage.expectedActionText()), пауза/активна.
- **ViewModel:** loadedTaskState; загрузка при init, loadTaskIntoDialog, switchBranch; onLeaveScreen() / onEnterScreen() — пауза при выходе с экрана, снятие при входе. confirmTaskResult() / rejectTaskResult() с добавлением служебного сообщения в диалог и save. executeCommand(input) — общий обработчик команд (/confirm, /reject, /help, /add_long_term, /add_task_memory); вызов из поля ввода и из UI «Команды».
- **UI:** на экране диалога отображаются этап, шаг, ожидаемое действие (кратко), «на паузе» при isPaused. Кнопка «Команды» открывает список команд с описаниями; при выборе выполнение через executeCommand (для команд с аргументом — диалог ввода). Кнопка «Превысить контекст» перенесена в настройки; видимость по флагу showContextOverflowButton (AgentPreferences, по умолчанию false).
- **Валидация агентом:** на этапе Validation ожидаемое действие инструктирует агента проверить результат и предложить пользователю /confirm или /reject.

### Уточнения логики Дня 13 (после первоначальной реализации)

- **Планирование:** пользователь сам отправляет сообщение для получения плана; подтверждение плана — /confirm → автоматический переход в Выполнение и запрос «Выполняй шаги плана».
- **Авто-валидация:** после ответа модели по выполнению автоматически (без /confirm) переход в Проверка: в чат добавляется «[Валидация решения…]», в модель уходит запрос на проверку; ответ выдаётся на ревью.
- **/reject:** этап не меняется; кнопка «/reject» в диалоге «Команды» подставляет в поле ввода «/reject » для комментария; отправка строки «/reject» или «/reject комментарий» — запрос в модель на повторное выполнение плана с учётом комментария (rejectWithUserComment, prepareReject).
- **Счётчик шагов:** убран из UI и из промпта агента (SimpleAgent, AgentScreen); currentStep остаётся в TaskState/Entity для совместимости.
- **Пауза при выходе с экрана:** onLeaveScreen() выставляет isPaused в БД; onEnterScreen() сразу обновляет UI (loadedTaskState.copy(isPaused = false)) и в фоне сохраняет в БД; при загрузке диалога в init ViewModel, если задача была на паузе, сразу выставляется resumedState и обновляется БД — чтобы при пересоздании экрана /confirm был доступен.
- **Удаление задачи:** при deleteTaskMemory очищаются также loadedTaskState и (если удалялась текущая) loadedTaskId в UI.
- **Очистка диалога:** при подтверждении очистки без галочки «удалить память задачи» подключённая задача сбрасывается к этапу Планирование (stage, currentStep = 0) в БД.
- **Команда /reset_planning** (и /planning): сброс подключённой задачи к этапу Планирование; кнопка в диалоге «Команды».

## День 14 (инварианты агента)

- **Инварианты:** пользователь задаёт в настройках агента текст правил, которые модель не должна нарушать. Хранение — AgentPreferences (DataStore, ключ `agent_invariants`). При каждом запросе текст передаётся в `SimpleAgent.process(invariantsText)` и добавляется в system message блоком «Инварианты» с инструкцией: при противоречии запроса инварианту — отказ и объяснение.
- **Пустые инварианты:** в system message всё равно добавляется блок с фразой «Ограничений на ответ нет».
- **ViewModel:** загрузка инвариантов при init и при открытии Bottom Sheet настроек; `saveInvariants(text)` с тостом «Не удалось сохранить» при ошибке; передача `invariantsText` во все вызовы `agent.process()` (в т.ч. тест превышения контекста).
- **Настройки агента:** разбиты на шесть смысловых блоков со сворачиванием (аккордеон): Профиль пользователя, Долговременная память, Память задачи, Стратегия контекста, Инварианты, Отладка. По умолчанию развёрнут только первый блок. Индикатор сворачивания — символы ▼/▲ как на главном экране.
- **Блок «Инварианты»:** многострочное поле с placeholder, maxLines 20, при превышении 1500 символов — предупреждение под полем; кнопка «Сохранить».
- **Сценарий проверки:** см. [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md) — секция «День 14: инварианты».

## День 15 (контролируемые переходы состояний)

- **Перенос этапов на уровень ветки:** состояние задачи (этап, currentStep, isPaused) теперь хранится per-branch в таблице `agent_branches` (новые колонки stage, currentStep, isPaused) вместо `agent_task_memories`. Память задачи (task memory) больше не влияет на этапы.
- **Room, миграция 7→8:** в `agent_branches` добавлены stage (TEXT NULL), currentStep (INTEGER), isPaused (INTEGER). AgentBranchEntity, AgentBranchDao: updateBranchTaskState, clearBranchTaskState. AgentDialogStorage: getBranchTaskState, updateBranchTaskState, clearBranchTaskState.
- **Явная активация:** новая команда `/start_task` — запускает цикл этапов для текущей ветки (stage = Planning). Без неё этапы неактивны (stage = null). `/stop_task` — останавливает цикл (stage = null).
- **Деcвязка от задачи:** `/confirm`, `/reject`, `/reset_planning` больше не требуют подключённой задачи (loadedTaskId). Работают с branch-level state. При отсутствии активного цикла — toast «Запустите задачу: /start_task».
- **Промпт агента:** блок состояния задачи (этап, ожидаемое действие) теперь выводится независимо от taskMemory. Добавлена строгая инструкция: модель не может выполнять работу другого этапа; переход только по `/confirm`; после завершения работы на текущем этапе модель обязана напомнить пользователю подтвердить `/confirm`. expectedActionText() для каждого этапа явно содержит инструкцию напомнить про `/confirm`.
- **Авто-цепочка:** сохранена: /confirm из Planning → авто Execution → авто Validation.
- **Очистка диалога:** сбрасывает branch stage в null (вместо сброса task memory stage в Planning).
- **UI:** в диалоге «Команды» добавлены `/start_task` и `/stop_task`.
- **Сценарий проверки:** см. [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md) — секция «День 15».

## День 16 (MCP погода)

- **Подключение MCP:** агент получает погоду через Model Context Protocol. Сервер: `https://jiri-spilka--weather-mcp-server.apify.actor/mcp` (Apify Weather MCP Server). Токен Apify задаётся в `secret.properties` как `APIFY_API_KEY`, пробрасывается в `BuildConfig.APIFY_API_KEY` и подставляется в URL при подключении (`?token=…`).
- **data/mcp:** `McpWeatherClient` — ленивая инициализация MCP-клиента (Kotlin SDK), StreamableHttpClientTransport поверх Ktor HttpClient (SSE, HttpTimeout 30/60/90 с). Методы `listTools()` и `getWeather(city)`; вызов инструмента `get_current_weather` с параметрами `city` и `lang=ru`.
- **Команды в чате:** `/tools` — вывод списка MCP-инструментов в чат; `/weather Город` — запрос погоды, ответ в чат (на русском при поддержке сервером). Обработка в `AgentViewModel.executeCommand`; ответы и запросы сохраняются в историю диалога.
- **UI:** в диалоге «Команды» добавлены пункты `/tools` и `/weather` (для погоды — диалог ввода города). Индикатор загрузки MCP через `isMcpLoading` в AgentUiState.
- **Зависимости:** Model Context Protocol Kotlin SDK (io.modelcontextprotocol:kotlin-sdk), Ktor client (core, okhttp, sse) — используются только для MCP; основной HTTP-клиент приложения — Retrofit.

## День 17 (свой MCP-сервер)

- **MCP-сервер (модуль `mcp-server/`):** отдельный Gradle-модуль (Kotlin, Ktor, MCP Kotlin SDK server). Транспорт: Streamable HTTP на пути `/mcp` (`mcpStreamableHttp`). Инструмент `mock_echo`: входной параметр `message` (строка), возврат текста вида `Echo: <message> | Время: <ISO-8601>`. Порт задаётся переменной окружения `PORT` (по умолчанию 8080). Сборка: `./gradlew :mcp-server:installDist` или `:mcp-server:run`; развёртывание на VPS описано в [mcp-server/DEPLOY.md](mcp-server/DEPLOY.md).
- **Приложение:** в `secret.properties` добавлено свойство `MCP_CUSTOM_SERVER_URL` (полный URL до `/mcp`), читается в `BuildConfig.MCP_CUSTOM_SERVER_URL`. Новый клиент `data/mcp/McpCustomClient`: ленивое подключение по Streamable HTTP к указанному URL, метод `callMockEcho(message: String): String` (вызов инструмента `mock_echo`).
- **Команда в чате агента:** `/mock Текст` — вызов mock-инструмента на своём MCP-сервере; запрос и ответ выводятся в чат (как у `/weather`). При пустом `MCP_CUSTOM_SERVER_URL` показывается toast с просьбой указать URL в secret.properties.
- **UI:** в диалоге «Команды» добавлен пункт «/mock — вызвать mock-инструмент MCP» с диалогом ввода текста.
- **Вызов MCP моделью по обычному промпту:** при заданном `MCP_CUSTOM_SERVER_URL` в запрос к OpenAI передаётся список tools (mock_echo, get_current_weather) в формате function calling. SimpleAgent при наличии tools выполняет цикл (до 5 раундов): sendOneCompletion → при tool_calls выполняет mock_echo/get_current_weather через MCP и повторяет запрос с результатами; финальный текст без tool_calls возвращается в чат. Data: OpenAiDto (tools, tool_calls, ChatMessage с ролями tool/assistant+tool_calls), ChatRepository.sendOneCompletion, ChatResponseWithToolCalls; data/AgentTools.kt — статический список tools; domain/agent/SimpleAgent — processWithTools, runToolCall.
- **Ветка:** `challenge_day_17`.

## День 18 (напоминалки)

- **MCP-сервер:** инструменты `register_reminder`(message, in_minutes) и `get_reminders`(). Хранение в SQLite (таблица reminders: id, message, scheduled_at, created_at); слой `ReminderStorage.kt`, зависимость `org.xerial:sqlite-jdbc`. При запросе «напомни через N минут» модель вызывает tool `schedule_reminder`; приложение регистрирует напоминание на сервере и ставит локальное уведомление через AlarmManager.
- **Приложение:** единый источник констант инструментов — `data/AgentToolConstants.kt` (имена инструментов и параметров); список tools в `AgentTools.kt` и ветки в `SimpleAgent.runToolCall` используют константы. Новые инструменты для модели: `schedule_reminder` (message, in_minutes), `get_reminders`. `McpCustomClient.callTool(toolName, arguments)` — универсальный вызов MCP.
- **Локальные уведомления:** интерфейс `ReminderScheduler` (domain/agent), реализация `AppReminderScheduler` (data/reminder) — AlarmManager: приоритетно `setAlarmClock` (точное время, иконка в статус-баре); при SecurityException (нет SCHEDULE_EXACT_ALARM) — fallback на `setAndAllowWhileIdle`. При срабатывании `ReminderReceiver` показывает Notification. Канал «Напоминания»; разрешения POST_NOTIFICATIONS и SCHEDULE_EXACT_ALARM в манифесте. Запрос POST_NOTIFICATIONS при старте приложения (MainActivity, API 33+). Логи в AppReminderScheduler и ReminderReceiver (теги для отладки). После перезагрузки устройства напоминания не восстанавливаются.
- **Таймзона для списка напоминаний:** при вызове get_reminders приложение передаёт таймзону устройства (`TimeZone.getDefault().id`); MCP-сервер принимает опциональный параметр `timezone` (IANA) и возвращает время в этой таймзоне (formatScheduledAt). SimpleAgent.process(..., userTimezone) передаёт таймзону в runToolCall.
- **Интеграция:** AgentViewModelFactory создаёт AppReminderScheduler, передаёт в AgentViewModel и в `SimpleAgent.process(..., reminderScheduler, userTimezone)`. При вызове schedule_reminder агент сначала вызывает MCP register_reminder, затем reminderScheduler.scheduleReminder(inMinutes, message).
- **Ветка:** `challenge_day_18`.

## День 21 (индексация документов — chunking, эмбеддинги Ollama, SQLite)

- **Модуль `doc-index`:** JVM (Kotlin 17 toolchain), зависимости OkHttp, Gson, `sqlite-jdbc`. Сканер корпуса: все `*.md` в корне репозитория и в `docs/`, все `*.kt` под `app/src/main/java/`. Две стратегии нарезки: **FIXED_WINDOW** (окно 1200 UTF-16 code units, overlap 200, `section` вида `part:i/n`) и **STRUCTURE** (Markdown по заголовкам `#`–`###`, преамбула `(preamble)`; Kotlin по файлу; фрагменты длиннее 4000 символов дополнительно режутся окнами 1200/200). Перед вызовом Ollama длинные чанки дополнительно режутся до `MAX_EMBEDDING_INPUT_CHARS` (2048 UTF-16, overlap 256), чтобы не превышать токен-лимит `nomic-embed-text` (HTTP 500 «input length exceeds the context length»); при разбиении создаются строки с id `…__emb_N` и пометкой в `section`. Эмбеддинги: Ollama `POST /api/embeddings`, модель **`nomic-embed-text`**, базовый URL из `OLLAMA_HOST` или `--ollama-base` (по умолчанию `http://127.0.0.1:11434`). SQLite: таблицы `chunks` и `index_meta` (в т.ч. `max_embedding_input_chars`, `embedding_input_overlap`).
- **Gradle:** `:doc-index:buildDocIndex` (JavaExec), `:doc-index:printDocIndexReport` — сводка и примеры чанков в консоль и `doc-index/build/reports/doc_index_report.md`. В `:app` задача `prepareDocIndexAssets` (Copy) зависит от `buildDocIndex`, а задачи `merge*Assets` зависят от `prepareDocIndexAssets` (так что для JVM unit tests Ollama не нужен); ассеты подключаются через `generated/docIndexAssets` → в APK попадает `doc_index.sqlite`.
- **Приложение:** пакет `data/index` — `DocEmbeddingIndex.openFromAssets`, копирование БД из assets во внутреннее хранилище при изменении размера файла, `search(queryEmbedding, ChunkingStrategy, topK)` с полным сканом и косинусным сходством; `VectorMath`, `EmbeddingVector`. Юнит-тесты: `VectorMathTest` (косинус, разбор BLOB).
- **Документация:** [doc-index/README.md](doc-index/README.md) — установка/запуск Ollama, переменные, curl, типичные ошибки; [PROJECT.md](PROJECT.md) — строка в таблице «Где что искать» и примечание в «Сборка и запуск».
- **Игнор Git:** `**/doc_index.sqlite`, `doc-index/build/reports/` (сгенерированные артефакты не коммитятся).

### Сравнение стратегий chunking (на примере `ARCHITECTURE.md`)

Точные числа по текущему корпусу: `./gradlew :doc-index:printDocIndexReport` (после успешной индексации).

| Критерий | FIXED_WINDOW | STRUCTURE |
|----------|--------------|-----------|
| Границы | Каждые ~1000 шагов по тексту (1200 символов, перекрытие 200); граница может пройти посередине раздела или заголовка. | По смысловым секциям Markdown (заголовки); внутри одной секции текст цельнее; при перегрузе секции (>4000) — те же окна, что у fixed, но с меткой секции. |
| Метаданные `section` | `part:i/n` по файлу. | Текст заголовка или `(preamble)`; при поднарезке — `Заголовок · part k/m`. |
| Риск «обрыва» мысли | Выше на границе окна без учёта структуры. | Ниже между секциями; возможен обрыв внутри очень длинной секции после 4000 символов. |

Примеры границ (иллюстративно): у **FIXED_WINDOW** конец чанка может обрывать маркированный список или строку кода посередине; у **STRUCTURE** граница чаще совпадает с заголовком `## …`, а тело секции остаётся в одном чанке, пока не превысит порог 4000 символов.

#### Числа только для `ARCHITECTURE.md` (chunking без эмбеддингов)

Подсчёт по тем же параметрам, что в коде (`FIXED_WINDOW` 1200/200, `STRUCTURE` по заголовкам + подокна 1200/200 для секций >4000). Длины — в символах Unicode (для преимущественно BMP совпадает с `String.length` в Kotlin).

| Метрика | FIXED_WINDOW | STRUCTURE |
|--------|--------------|-----------|
| Число чанков | 14 | 18 |
| Min / median / max длина текста | 477 / 1200 / 1200 | 147 / 1200 / 1200 |
| Средняя длина | ~1148 | ~837 |
| Доля чанков, где последний непробельный символ **не** `.`, `?`, `!`, `)`, `]`, `"`, `'` (грубый «обрыв») | ~0,93 | ~0,56 |

Примеры границ: **FIXED** — фрагмент около середины файла заканчивается на «…`onLeaveScreen()` выста» (обрыв посередине слова/токена). **STRUCTURE** — начало чанка может совпадать с элементом списка под заголовком (цельная строка документации).

- **Ветка:** `challenge_day_21`.

## День 22 (ветка `challenge_day_22`)

- **RAG в агенте:** опционально перед вызовом OpenAI запрос эмбеддится через Ollama (`nomic-embed-text`, тот же API, что при индексации), выполняется top-k поиск по `doc_index.sqlite` из assets (стратегия чанков **STRUCTURE**), фрагменты вставляются в промпт / system message как «Локальный индекс (фрагменты документации)».
- **Конфиг:** `secret.properties` → `BuildConfig.OLLAMA_HOST` (пример в `secret.properties.example`); при пустом URL и включённом RAG — тост, запрос к OpenAI не отправляется.
- **Код:** `data/rag/` — `OllamaHostResolver`, `OllamaEmbeddingClient`, `RagMarkdownFormatter`, `RagContextBuilder`; `SimpleAgent.process(..., ragContextMarkdown)`; в ветке с tools контекст добавляется в system message.
- **UI:** DataStore `rag_enabled`; переключатель «Использовать RAG» в настройках агента (блок «Локальный индекс (RAG)»); `AgentViewModel` вызывает `RagContextBuilder.buildContext` при отправке сообщения.
- **Проверка вручную:** 10 контрольных вопросов и подсказки по сравнению ответов с/без RAG — [docs/AGENT_TEST_SCENARIO.md](docs/AGENT_TEST_SCENARIO.md) (раздел «День 22: RAG»).
- **Тесты:** `RagMarkdownFormatterTest` — форматирование блока контекста без сети и SQLite.

### Корпус индекса (уточнение)

- **`CorpusScanner`:** в индекс дополнительно включены `doc-index/**/*.md`, `doc-index/src/main/kotlin/**/*.kt`, `app/build.gradle.kts`, `secret.properties.example`, чтобы фрагменты совпадали с «ожидаемыми источниками» в сценарии Дня 22 и не терялись ответы про `nomic-embed-text`, Gradle и ключи (раньше не индексировались `doc-index/README.md` и `ModelConstants.kt`).

### Итерация: recall RAG и STRUCTURE chunking

- **`RagRetrievalEnhancement`:** к запросу эмбеддинга (только для поиска) и к лексическому rerank подмешиваются отдельные подсказки для вопросов про **стратегии контекста агента** (`ContextStrategy`) и про **поток данных экрана «Агент»** (UI → API), в дополнение к теме индексации/RAG/эмбеддингов. Юнит-тесты расширены в `RagRetrievalEnhancementTest`.
- **`StructureChunker` / `ModelConstants`:** STRUCTURE — заголовки Markdown **`#`…`######`**; длинные секции сначала по **абзацам** (`\n\n`), затем при необходимости окна 1200/200; в начале `TextChunk.text` — **строка с названием раздела** для лучшего эмбеддинга; лимит тела секции уменьшен на `STRUCTURE_HEADING_LINE_RESERVE`. В `index_meta` **`schema_version` = `2`** — нужна пересборка `:doc-index:buildDocIndex` и полная сборка приложения, чтобы в APK попала новая БД.
- **`ARCHITECTURE.md`:** в разделе «Экран «Агент»» добавлен явный абзац, где описан поток **от UI до вызова OpenAI API** и цепочка компонентов (см. файл).
- Актуальное описание стратегии **STRUCTURE** и констант chunking — в [doc-index/README.md](doc-index/README.md). Численные таблицы в блоке «День 21» выше отражают более раннюю конфигурацию; после смены нарезки пересчитывайте метрики через `./gradlew :doc-index:printDocIndexReport`.
