package com.example.aiadventchallenge.ui.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentPreferences
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.data.agent.TaskMemoryItem
import com.example.aiadventchallenge.data.mcp.McpCustomClient
import com.example.aiadventchallenge.data.mcp.McpWeatherClient
import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import com.example.aiadventchallenge.domain.agent.ReminderScheduler
import com.example.aiadventchallenge.domain.agent.SimpleAgent
import com.example.aiadventchallenge.domain.agent.TaskStage
import com.example.aiadventchallenge.domain.agent.TaskState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_TAG = "AgentViewModel"

/**
 * ViewModel экрана «Агент». Вызывает только агента (agent.process), не обращается к ChatRepository напрямую для чата.
 * Диалог загружается из Room при создании и сохраняется после каждого ответа.
 * При включённом сжатии после ответа вызывается генерация недостающих summaries (repository.summarizeDialog).
 */
class AgentViewModel(
  private val agent: SimpleAgent,
  private val storage: AgentDialogStorage,
  private val repository: ChatRepository,
  private val agentPreferences: AgentPreferences,
  private val reminderScheduler: ReminderScheduler?
) : ViewModel() {

  private val _uiState = MutableStateFlow(AgentUiState())
  val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

  private var dialogState: AgentDialogState = AgentDialogState()

  init {
    viewModelScope.launch {
      try {
        val settings = agentPreferences.getSettings()
        val showOverflow = agentPreferences.getShowContextOverflowButton()
        _uiState.value = _uiState.value.copy(
          contextStrategy = settings.contextStrategy,
          lastN = settings.lastN,
          useCompression = settings.useCompression,
          showContextOverflowButton = showOverflow
        )
        dialogState = storage.load(_uiState.value.currentBranchId)
        val longTerm = storage.getLongTermMemory()
        val taskList = storage.getTaskMemories()
        val restoredLoadedId = dialogState.loadedTaskId
        val loadedId = restoredLoadedId?.takeIf { id -> taskList.any { it.id == id } }
        val profiles = storage.getAllProfiles()
        val activeProfileId = agentPreferences.getActiveProfileId()
        val validActiveId = activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
        val invariantsText = agentPreferences.getInvariantsText()
        val branchId = dialogState.currentBranchId
        val branchTaskState = storage.getBranchTaskState(branchId)
        val resumedState = if (branchTaskState?.isPaused == true) branchTaskState.copy(isPaused = false) else branchTaskState
        if (branchTaskState?.isPaused == true) {
          storage.updateBranchTaskState(branchId, branchTaskState.stage, branchTaskState.currentStep, isPaused = false)
        }
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = branchId,
          facts = dialogState.facts,
          longTermMemory = longTerm,
          taskMemories = taskList,
          loadedTaskId = loadedId,
          loadedTaskState = resumedState,
          profiles = profiles,
          activeProfileId = validActiveId,
          invariantsText = invariantsText
        )
        if (validActiveId != activeProfileId) {
          agentPreferences.setActiveProfileId(validActiveId)
        }
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load dialog or settings", e)
      }
    }
  }

  fun updateRequest(text: String) {
    _uiState.value = _uiState.value.copy(request = text, error = null)
  }

  fun sendRequest() {
    val request = _uiState.value.request.trim()
    if (request.isEmpty()) return

    if (request.equals("/reject", ignoreCase = true) || request.startsWith("/reject ", ignoreCase = true)) {
      rejectWithUserComment(request)
      return
    }
    if (request.startsWith("/")) {
      executeCommand(request)
      return
    }

    val contextStrategy = _uiState.value.contextStrategy
    val lastN = _uiState.value.lastN

    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      var stateToSend = dialogState
      if (contextStrategy == ContextStrategy.StickyFacts) {
        _uiState.value = _uiState.value.copy(toastMessage = "Обновление фактов…")
        val messagesWithUser = dialogState.messages + AgentMessage(AgentRole.User, request)
        repository.extractOrUpdateFacts(dialogState.facts, messagesWithUser)
          .onSuccess { newFacts ->
            storage.saveFacts(newFacts)
            stateToSend = dialogState.copy(facts = newFacts)
            _uiState.value = _uiState.value.copy(facts = newFacts)
          }
          .onFailure { e ->
            _uiState.value = _uiState.value.copy(
              isLoading = false,
              error = "Ошибка извлечения фактов: ${e.message}",
              toastMessage = null
            )
            return@launch
          }
      }
      runAgentRequest(stateToSend, request)
    }
  }

  /**
   * Вызывает агента с заданным состоянием диалога и запросом.
   * [onSuccessChain] — опционально вызывается после успеха (для авто-валидации после выполнения плана).
   */
  private suspend fun runAgentRequest(
    stateToSend: AgentDialogState,
    request: String,
    taskStateOverride: TaskState? = null,
    onSuccessChain: (suspend (AgentDialogState, TaskState?) -> Unit)? = null
  ) {
    val contextStrategy = _uiState.value.contextStrategy
    val lastN = _uiState.value.lastN
    val longTerm = _uiState.value.longTermMemory
    val taskMemory = _uiState.value.loadedTaskId?.let { id -> storage.getTaskMemoryContent(id) }
    val taskState = taskStateOverride ?: _uiState.value.loadedTaskState
    val userProfile = _uiState.value.activeProfileId?.let { id -> storage.getProfileContent(id) } ?: ""
    val invariantsText = _uiState.value.invariantsText
    agent.process(
      stateToSend,
      request,
      contextStrategy = contextStrategy,
      lastN = lastN,
      longTermMemory = longTerm,
      taskMemory = taskMemory,
      taskState = taskState,
      userProfile = userProfile,
      invariantsText = invariantsText,
      reminderScheduler = reminderScheduler
    )
      .onSuccess { agentResponse ->
        dialogState = agentResponse.dialog
        _uiState.value = _uiState.value.copy(
          isLoading = (onSuccessChain != null),
          messages = agentResponse.dialog.messages,
          request = "",
          error = null,
          promptTokens = agentResponse.raw.promptTokens,
          completionTokens = agentResponse.raw.completionTokens,
          totalTokens = agentResponse.raw.totalTokens,
          lastTokensModeCompression = (contextStrategy == ContextStrategy.Summary),
          facts = agentResponse.dialog.facts,
          loadedTaskState = taskStateOverride ?: _uiState.value.loadedTaskState
        )
        try {
          storage.save(dialogState)
          if (contextStrategy == ContextStrategy.Summary) {
            ensureSummaries(dialogState)
          }
        } catch (e: Exception) {
          Log.e(LOG_TAG, "Failed to save dialog", e)
        }
        onSuccessChain?.invoke(agentResponse.dialog, taskState)
      }
      .onFailure { e ->
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          error = e.message ?: e.toString()
        )
      }
  }

  /** Генерирует и сохраняет недостающие summaries (блоки по 10 сообщений с начала). Сообщения не удаляются. */
  private suspend fun ensureSummaries(current: AgentDialogState) {
    val m = current.messages.size
    val needSummaries = m / 10
    var summaries = current.summaries
    if (needSummaries <= summaries.size) {
      dialogState = current.copy(summaries = summaries)
      return
    }
    _uiState.value = _uiState.value.copy(toastMessage = "Сжатие контекста…")
    var addedCount = 0
    for (i in summaries.size until needSummaries) {
      val start = i * 10
      val end = (i + 1) * 10
      if (end > current.messages.size) break
      val block = current.messages.subList(start, end)
      val result = repository.summarizeDialog(block)
      result.getOrElse { e ->
        Log.e(LOG_TAG, "Summarization failed", e)
        _uiState.value = _uiState.value.copy(error = "Ошибка суммаризации: ${e.message}")
        return
      }
      val summaryText = result.getOrNull() ?: continue
      try {
        storage.insertSummary(summaryText, i)
        summaries = summaries + summaryText
        addedCount++
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to insert summary", e)
        _uiState.value = _uiState.value.copy(error = "Не удалось сохранить суммаризацию: ${e.message}")
        return
      }
    }
    dialogState = current.copy(summaries = summaries)
    if (addedCount > 0) {
      _uiState.value = _uiState.value.copy(toastMessage = "Сжатие контекста завершено")
    }
  }

  /**
   * Выполнить команду (из поля ввода или из UI «Команды»).
   * Поведение идентично вводу команды вручную.
   */
  fun executeCommand(input: String) {
    val trimmed = input.trim()
    when {
      trimmed.equals("/tools", ignoreCase = true) -> {
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(
            request = "",
            isMcpLoading = true,
            error = null
          )
          try {
            val tools = McpWeatherClient.listTools()
            val description = if (tools.isEmpty()) {
              "Инструменты MCP не найдены."
            } else {
              buildString {
                appendLine("Доступные инструменты MCP:")
                tools.forEachIndexed { index, tool ->
                  append(index + 1)
                  append(". ")
                  append(tool.name)
                  tool.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    append(" — ")
                    append(desc)
                  }
                  appendLine()
                }
              }.trimEnd()
            }
            appendAssistantMessage(description)
          } catch (e: Exception) {
            val message = e.message ?: e.toString()
            appendAssistantMessage("Не удалось получить список инструментов: $message")
          } finally {
            _uiState.value = _uiState.value.copy(isMcpLoading = false)
          }
        }
      }
      trimmed.startsWith("/weather", ignoreCase = true) -> {
        val city = trimmed.removePrefix("/weather").removePrefix("/WEATHER").trim().trim('"')
        if (city.isEmpty()) {
          _uiState.value = _uiState.value.copy(
            request = "",
            toastMessage = "Укажи город: /weather Москва"
          )
          return
        }
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(
            request = "",
            isMcpLoading = true,
            error = null
          )
          try {
            appendUserMessage("[Запрос погоды для города \"$city\" через MCP]")
            val weather = McpWeatherClient.getWeather(city)
            appendAssistantMessage(weather.rawText)
          } catch (e: Exception) {
            val message = e.message ?: e.toString()
            appendAssistantMessage("Не удалось получить погоду: $message")
          } finally {
            _uiState.value = _uiState.value.copy(isMcpLoading = false)
          }
        }
      }
      trimmed.startsWith("/mock", ignoreCase = true) -> {
        val text = trimmed.removePrefix("/mock").removePrefix("/MOCK").trim().trim('"')
        if (BuildConfig.MCP_CUSTOM_SERVER_URL.isBlank()) {
          _uiState.value = _uiState.value.copy(
            request = "",
            toastMessage = "Укажите MCP_CUSTOM_SERVER_URL в secret.properties"
          )
          return
        }
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(
            request = "",
            isMcpLoading = true,
            error = null
          )
          try {
            appendUserMessage("[Mock: \"${text.ifBlank { "…" }}\" через MCP]")
            val result = McpCustomClient.callMockEcho(text.ifBlank { "" })
            appendAssistantMessage(result)
          } catch (e: Exception) {
            val message = e.message ?: e.toString()
            appendAssistantMessage("Не удалось вызвать mock-инструмент: $message")
          } finally {
            _uiState.value = _uiState.value.copy(isMcpLoading = false)
          }
        }
      }
      trimmed.equals("/help", ignoreCase = true) || trimmed.equals("/memory_help", ignoreCase = true) -> {
        _uiState.value = _uiState.value.copy(
          request = "",
          toastMessage = "Команды: /start_task — запустить задачу; /stop_task — остановить; /confirm — следующий этап; /reject — отклонить; /reset_planning — сброс к планированию; /add_long_term; /add_task_memory; /help"
        )
      }
      trimmed.equals("/start_task", ignoreCase = true) -> startTask()
      trimmed.equals("/stop_task", ignoreCase = true) -> stopTask()
      trimmed.equals("/confirm", ignoreCase = true) -> confirmTaskResult()
      trimmed.equals("/reset_planning", ignoreCase = true) || trimmed.equals("/planning", ignoreCase = true) -> resetTaskToPlanning()
      trimmed.startsWith("/add_long_term", ignoreCase = true) -> {
        val text = trimmed.removePrefix("/add_long_term").trim()
        if (text.isEmpty()) {
          _uiState.value = _uiState.value.copy(toastMessage = "Укажите текст после команды: /add_long_term ваш текст")
          return
        }
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, request = "", error = null)
          repository.extractFactsFromText(text)
            .onSuccess { facts ->
              if (facts.isNotBlank()) {
                storage.appendToLongTermMemory(facts)
                _uiState.value = _uiState.value.copy(
                  isLoading = false,
                  longTermMemory = storage.getLongTermMemory(),
                  toastMessage = "Факты добавлены в долговременную память"
                )
              } else {
                _uiState.value = _uiState.value.copy(isLoading = false, toastMessage = "Не удалось извлечь факты")
              }
            }
            .onFailure { e ->
              _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Ошибка извлечения фактов: ${e.message}"
              )
            }
        }
      }
      trimmed.startsWith("/add_task_memory", ignoreCase = true) -> {
        val text = trimmed.removePrefix("/add_task_memory").trim()
        val loadedId = _uiState.value.loadedTaskId
        if (loadedId == null) {
          _uiState.value = _uiState.value.copy(
            request = "",
            toastMessage = "Сначала загрузите задачу в диалог (Настройки → Память задачи)"
          )
          return
        }
        if (text.isEmpty()) {
          _uiState.value = _uiState.value.copy(toastMessage = "Укажите текст после команды: /add_task_memory ваш текст")
          return
        }
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, request = "", error = null)
          repository.extractFactsFromText(text)
            .onSuccess { facts ->
              if (facts.isNotBlank()) {
                storage.appendToTaskMemory(loadedId, facts)
                val updatedContent = storage.getTaskMemoryContent(loadedId)
                _uiState.value = _uiState.value.copy(
                  isLoading = false,
                  taskEditorContent = if (_uiState.value.taskEditorId == loadedId) (updatedContent ?: _uiState.value.taskEditorContent) else _uiState.value.taskEditorContent,
                  toastMessage = "Факты добавлены в память задачи"
                )
              } else {
                _uiState.value = _uiState.value.copy(isLoading = false, toastMessage = "Не удалось извлечь факты")
              }
            }
            .onFailure { e ->
              _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Ошибка извлечения фактов: ${e.message}"
              )
            }
        }
      }
      else -> {
        _uiState.value = _uiState.value.copy(
          request = "",
          toastMessage = "Неизвестная команда. Введите /help для списка команд."
        )
      }
    }
  }

  fun clearToastMessage() {
    _uiState.value = _uiState.value.copy(toastMessage = null)
  }

  private fun appendUserMessage(text: String) {
    val branchId = _uiState.value.currentBranchId
    val msg = AgentMessage(AgentRole.User, text)
    val newMessages = _uiState.value.messages + msg
    dialogState = dialogState.copy(messages = newMessages)
    _uiState.value = _uiState.value.copy(messages = newMessages)
    viewModelScope.launch {
      try {
        storage.save(dialogState)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save dialog after MCP user message", e)
      }
    }
  }

  private fun appendAssistantMessage(text: String) {
    val branchId = _uiState.value.currentBranchId
    val msg = AgentMessage(AgentRole.Assistant, text)
    val newMessages = _uiState.value.messages + msg
    dialogState = dialogState.copy(messages = newMessages)
    _uiState.value = _uiState.value.copy(messages = newMessages)
    viewModelScope.launch {
      try {
        storage.save(dialogState)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save dialog after MCP assistant message", e)
      }
    }
  }

  /** Пауза задачи ветки при выходе с экрана. */
  fun onLeaveScreen() {
    val state = _uiState.value.loadedTaskState ?: return
    val branchId = _uiState.value.currentBranchId
    viewModelScope.launch {
      try {
        storage.updateBranchTaskState(branchId, state.stage, state.currentStep, isPaused = true)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to pause task on leave", e)
      }
    }
  }

  /** Снять паузу при входе на экран: сразу обновить UI, затем сохранить в БД. */
  fun onEnterScreen() {
    val state = _uiState.value.loadedTaskState ?: return
    val branchId = _uiState.value.currentBranchId
    _uiState.value = _uiState.value.copy(loadedTaskState = state.copy(isPaused = false))
    viewModelScope.launch {
      try {
        storage.updateBranchTaskState(branchId, state.stage, state.currentStep, isPaused = false)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to resume task on enter", e)
      }
    }
  }

  private fun nextStage(stage: TaskStage): TaskStage? = when (stage) {
    TaskStage.Planning -> TaskStage.Execution
    TaskStage.Execution -> TaskStage.Validation
    TaskStage.Validation -> TaskStage.Done
    TaskStage.Done -> null
  }

  private fun prevStage(stage: TaskStage): TaskStage? = when (stage) {
    TaskStage.Planning -> null
    TaskStage.Execution -> TaskStage.Planning
    TaskStage.Validation -> TaskStage.Execution
    TaskStage.Done -> TaskStage.Validation
  }

  fun confirmTaskResult() {
    val state = _uiState.value.loadedTaskState ?: run {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Запустите задачу: /start_task")
      return
    }
    if (state.isPaused) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Задача на паузе")
      return
    }
    if (state.stage == TaskStage.Done) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Задача уже завершена")
      return
    }
    val next = nextStage(state.stage) ?: return
    val branchId = _uiState.value.currentBranchId
    var newStep = state.currentStep
    if (state.stage == TaskStage.Execution) newStep = state.currentStep + 1
    viewModelScope.launch {
      try {
        storage.updateBranchTaskState(branchId, next, newStep, isPaused = false)
        val msg = AgentMessage(AgentRole.User, "[Подтверждено: переход к этапу ${next.displayName()}]")
        dialogState = storage.load(branchId)
        val newMessages = dialogState.messages + msg
        storage.save(dialogState.copy(messages = newMessages))
        dialogState = dialogState.copy(messages = newMessages)
        val updatedState = storage.getBranchTaskState(branchId)
        _uiState.value = _uiState.value.copy(
          request = "",
          messages = newMessages,
          loadedTaskState = updatedState,
          toastMessage = "Этап: ${next.displayName()}"
        )
        if (next == TaskStage.Execution) {
          _uiState.value = _uiState.value.copy(isLoading = true)
          runAgentRequest(
            dialogState,
            "Выполняй шаги плана.",
            updatedState,
            onSuccessChain = { execDialog, execState ->
              if (execState?.stage != TaskStage.Execution) return@runAgentRequest
              storage.updateBranchTaskState(branchId, TaskStage.Validation, execState.currentStep, isPaused = false)
              val validationState = storage.getBranchTaskState(branchId) ?: return@runAgentRequest
              val validationMsg = AgentMessage(AgentRole.User, "[Валидация решения…]")
              val withValidationMsg = execDialog.messages + validationMsg
              dialogState = execDialog.copy(messages = withValidationMsg)
              storage.save(dialogState)
              _uiState.value = _uiState.value.copy(
                messages = withValidationMsg,
                loadedTaskState = validationState,
                toastMessage = "Валидация решения…"
              )
              runAgentRequest(
                dialogState,
                "Проверь результат. Если выполнение корректно — предложи пользователю подтвердить (/confirm). Иначе укажи, что переделать, и предложи /reject.",
                validationState
              )
            }
          )
        } else if (next == TaskStage.Validation) {
          val prompt = "Проверь результат и предложи пользователю подтвердить (/confirm) или отклонить (/reject)."
          _uiState.value = _uiState.value.copy(isLoading = true)
          runAgentRequest(dialogState, prompt, updatedState)
        }
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to confirm task result", e)
        _uiState.value = _uiState.value.copy(request = "", error = "Ошибка: ${e.message}")
      }
    }
  }

  /** Подставляет в поле ввода «/reject » для добавления комментария. Вызывать из UI (Команды). */
  fun prepareReject() {
    _uiState.value = _uiState.value.copy(request = "/reject ", error = null)
  }

  /**
   * Обработка отправки /reject или /reject комментарий: этап не меняется,
   * в диалог пишется отметка об отклонении, в модель уходит запрос на повторное выполнение плана с учётом комментария.
   */
  fun rejectWithUserComment(fullMessage: String) {
    val state = _uiState.value.loadedTaskState ?: run {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Запустите задачу: /start_task")
      return
    }
    if (state.isPaused) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Задача на паузе")
      return
    }
    if (state.stage == TaskStage.Planning) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Уже на первом этапе")
      return
    }
    val t = fullMessage.trim()
    val comment = when {
      t.equals("/reject", ignoreCase = true) -> ""
      t.startsWith("/reject ", ignoreCase = true) -> t.drop(8).trim()
      else -> t
    }
    viewModelScope.launch {
      try {
        val commentLabel = if (comment.isNotEmpty()) " Комментарий: $comment." else ""
        val msg = AgentMessage(AgentRole.User, "[Отклонено пользователем.$commentLabel]")
        dialogState = storage.load(_uiState.value.currentBranchId)
        val newMessages = dialogState.messages + msg
        storage.save(dialogState.copy(messages = newMessages))
        dialogState = dialogState.copy(messages = newMessages)
        val prompt = if (comment.isNotEmpty()) {
          "Предыдущий результат отклонён пользователем. Комментарий пользователя: $comment. Выполни план заново с учётом замечаний."
        } else {
          "Предыдущий результат отклонён пользователем. Выполни план заново, учтя возможные улучшения."
        }
        _uiState.value = _uiState.value.copy(
          request = "",
          messages = newMessages,
          toastMessage = "Запрос на повторное выполнение отправлен"
        )
        _uiState.value = _uiState.value.copy(isLoading = true)
        runAgentRequest(dialogState, prompt, state)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to reject and re-execute", e)
        _uiState.value = _uiState.value.copy(request = "", error = "Ошибка: ${e.message}")
      }
    }
  }

  /** Сбросить задачу ветки к этапу «Планирование», шаг 0. Команда /reset_planning или /planning. */
  fun resetTaskToPlanning() {
    if (_uiState.value.loadedTaskState == null) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Запустите задачу: /start_task")
      return
    }
    val branchId = _uiState.value.currentBranchId
    viewModelScope.launch {
      try {
        storage.updateBranchTaskState(branchId, TaskStage.Planning, 0, isPaused = false)
        val updated = storage.getBranchTaskState(branchId)
        _uiState.value = _uiState.value.copy(
          request = "",
          loadedTaskState = updated,
          toastMessage = "Задача сброшена к планированию"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to reset task to planning", e)
        _uiState.value = _uiState.value.copy(request = "", error = "Ошибка: ${e.message}")
      }
    }
  }

  /** Запустить жизненный цикл задачи — устанавливает этап Planning для текущей ветки. */
  fun startTask() {
    if (_uiState.value.loadedTaskState != null) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Задача уже запущена. Этап: ${_uiState.value.loadedTaskState!!.stage.displayName()}")
      return
    }
    val branchId = _uiState.value.currentBranchId
    viewModelScope.launch {
      try {
        storage.updateBranchTaskState(branchId, TaskStage.Planning, 0, isPaused = false)
        val state = storage.getBranchTaskState(branchId)
        val msg = AgentMessage(AgentRole.User, "[Задача запущена: этап ${TaskStage.Planning.displayName()}]")
        dialogState = storage.load(branchId)
        val newMessages = dialogState.messages + msg
        storage.save(dialogState.copy(messages = newMessages))
        dialogState = dialogState.copy(messages = newMessages)
        _uiState.value = _uiState.value.copy(
          request = "",
          messages = newMessages,
          loadedTaskState = state,
          toastMessage = "Задача запущена: ${TaskStage.Planning.displayName()}"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to start task", e)
        _uiState.value = _uiState.value.copy(request = "", error = "Ошибка: ${e.message}")
      }
    }
  }

  /** Остановить жизненный цикл задачи — сбросить этап ветки в null. */
  fun stopTask() {
    if (_uiState.value.loadedTaskState == null) {
      _uiState.value = _uiState.value.copy(request = "", toastMessage = "Задача не запущена")
      return
    }
    val branchId = _uiState.value.currentBranchId
    viewModelScope.launch {
      try {
        storage.clearBranchTaskState(branchId)
        val msg = AgentMessage(AgentRole.User, "[Задача остановлена]")
        dialogState = storage.load(branchId)
        val newMessages = dialogState.messages + msg
        storage.save(dialogState.copy(messages = newMessages))
        dialogState = dialogState.copy(messages = newMessages)
        _uiState.value = _uiState.value.copy(
          request = "",
          messages = newMessages,
          loadedTaskState = null,
          toastMessage = "Задача остановлена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to stop task", e)
        _uiState.value = _uiState.value.copy(request = "", error = "Ошибка: ${e.message}")
      }
    }
  }

  fun setContextStrategy(value: ContextStrategy) {
    _uiState.value = _uiState.value.copy(contextStrategy = value, useCompression = (value == ContextStrategy.Summary))
    viewModelScope.launch {
      try {
        agentPreferences.setContextStrategy(value)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save contextStrategy", e)
      }
    }
  }

  fun setLastN(value: Int) {
    val n = value.coerceIn(1, 100)
    _uiState.value = _uiState.value.copy(lastN = n)
    viewModelScope.launch {
      try {
        agentPreferences.setLastN(n)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save lastN", e)
      }
    }
  }

  fun openCreateBranchDialog() {
    _uiState.value = _uiState.value.copy(
      showCreateBranchDialog = true,
      createBranchNameInput = "Ветка 2"
    )
  }

  fun dismissCreateBranchDialog() {
    _uiState.value = _uiState.value.copy(showCreateBranchDialog = false)
  }

  fun setCreateBranchNameInput(value: String) {
    _uiState.value = _uiState.value.copy(createBranchNameInput = value)
  }

  fun switchBranch(branchId: Long) {
    if (_uiState.value.currentBranchId == branchId) return
    viewModelScope.launch {
      try {
        dialogState = storage.load(branchId)
        val taskList = _uiState.value.taskMemories
        val restoredLoadedId = dialogState.loadedTaskId
        val loadedId = restoredLoadedId?.takeIf { id -> taskList.any { it.id == id } }
        val branchTaskState = storage.getBranchTaskState(branchId)
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          currentBranchId = dialogState.currentBranchId,
          facts = dialogState.facts,
          loadedTaskId = loadedId,
          loadedTaskState = branchTaskState
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to switch branch", e)
      }
    }
  }

  fun createBranch(branchName: String) {
    if (_uiState.value.branches.size >= 2) return
    val lastIndex = dialogState.messages.size - 1
    if (lastIndex < 0) return
    val name = branchName.trim().ifBlank { "Ветка 2" }
    viewModelScope.launch {
      try {
        val newId = storage.createBranch(
          currentBranchId = _uiState.value.currentBranchId,
          lastMessageIndex = lastIndex,
          secondBranchName = name
        )
        dialogState = storage.load(newId)
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          currentBranchId = dialogState.currentBranchId,
          branches = dialogState.branches,
          showCreateBranchDialog = false
        )
        _uiState.value = _uiState.value.copy(toastMessage = "Ветка «$name» создана")
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to create branch", e)
        _uiState.value = _uiState.value.copy(error = "Не удалось создать ветку: ${e.message}")
      }
    }
  }

  fun openSettingsSheet() {
    _uiState.value = _uiState.value.copy(settingsSheetOpen = true)
    loadProfiles()
    refreshLongTermMemory()
    loadTaskMemories()
    _uiState.value.loadedTaskId?.let { openTaskEditor(it) }
    viewModelScope.launch {
      val inv = agentPreferences.getInvariantsText()
      _uiState.value = _uiState.value.copy(invariantsText = inv)
    }
  }

  fun closeSettingsSheet() {
    _uiState.value = _uiState.value.copy(settingsSheetOpen = false)
  }

  /** Сохраняет текст инвариантов в AgentPreferences. При ошибке показывает тост «Не удалось сохранить». */
  fun saveInvariants(text: String) {
    viewModelScope.launch {
      try {
        agentPreferences.setInvariantsText(text)
        _uiState.value = _uiState.value.copy(invariantsText = text)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save invariants", e)
        _uiState.value = _uiState.value.copy(toastMessage = "Не удалось сохранить")
      }
    }
  }

  fun setUseCompression(value: Boolean) {
    val strategy = if (value) ContextStrategy.Summary else ContextStrategy.SlidingWindow
    setContextStrategy(strategy)
  }

  /** Тест превышения контекста: отправляет запрос с искусственно раздутым промптом. Только для debug. */
  fun sendContextOverflowTest() {
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      agent.process(dialogState, "Тест: превышение контекста", contextStrategy = ContextStrategy.SlidingWindow, lastN = 10, forceContextOverflow = true, longTermMemory = "", taskMemory = null, taskState = null, userProfile = "", invariantsText = _uiState.value.invariantsText, reminderScheduler = reminderScheduler)
        .onSuccess { agentResponse ->
          dialogState = agentResponse.dialog
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            messages = agentResponse.dialog.messages,
            error = null,
            promptTokens = agentResponse.raw.promptTokens,
            completionTokens = agentResponse.raw.completionTokens,
            totalTokens = agentResponse.raw.totalTokens
          )
          try {
            storage.save(dialogState)
          } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to save dialog", e)
          }
        }
        .onFailure { e ->
          val testMsg = AgentMessage(AgentRole.User, "Тест: превышение контекста")
          dialogState = dialogState.copy(messages = dialogState.messages + testMsg)
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            messages = dialogState.messages,
            error = e.message ?: e.toString()
          )
          try {
            storage.save(dialogState)
          } catch (ex: Exception) {
            Log.e(LOG_TAG, "Failed to save dialog after overflow test", ex)
          }
        }
    }
  }

  fun openClearConfirmDialog() {
    _uiState.value = _uiState.value.copy(showClearConfirmDialog = true, clearDialogAlsoTaskMemory = false)
  }

  fun dismissClearConfirmDialog() {
    _uiState.value = _uiState.value.copy(showClearConfirmDialog = false)
  }

  fun setClearDialogAlsoTaskMemory(value: Boolean) {
    _uiState.value = _uiState.value.copy(clearDialogAlsoTaskMemory = value)
  }

  fun confirmClearDialog() {
    val alsoTaskMemory = _uiState.value.clearDialogAlsoTaskMemory
    viewModelScope.launch {
      try {
        storage.clear()
        if (alsoTaskMemory) {
          storage.clearTaskMemories()
        }
        dialogState = storage.load(1L)
        storage.clearBranchTaskState(1L)
        val taskList = storage.getTaskMemories()
        val keptLoadedId = if (alsoTaskMemory) null else _uiState.value.loadedTaskId
        _uiState.value = _uiState.value.copy(
          showClearConfirmDialog = false,
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = 1L,
          facts = "",
          taskMemories = taskList,
          loadedTaskId = keptLoadedId,
          loadedTaskState = null,
          promptTokens = null,
          completionTokens = null,
          totalTokens = null,
          lastTokensModeCompression = null
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to clear dialog", e)
      }
    }
  }

  // --- Долговременная память ---

  fun saveLongTermMemory(content: String) {
    viewModelScope.launch {
      try {
        storage.saveLongTermMemory(content)
        _uiState.value = _uiState.value.copy(
          longTermMemory = storage.getLongTermMemory(),
          toastMessage = "Долговременная память сохранена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save long-term memory", e)
      }
    }
  }

  fun clearLongTermMemory() {
    viewModelScope.launch {
      try {
        storage.saveLongTermMemory("")
        _uiState.value = _uiState.value.copy(
          longTermMemory = "",
          toastMessage = "Долговременная память очищена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to clear long-term memory", e)
      }
    }
  }

  fun refreshLongTermMemory() {
    viewModelScope.launch {
      try {
        _uiState.value = _uiState.value.copy(longTermMemory = storage.getLongTermMemory())
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load long-term memory", e)
      }
    }
  }

  // --- Профили пользователя ---

  fun loadProfiles() {
    viewModelScope.launch {
      try {
        val profiles = storage.getAllProfiles()
        val activeId = agentPreferences.getActiveProfileId()
        val validActiveId = activeId?.takeIf { id -> profiles.any { it.id == id } }
        _uiState.value = _uiState.value.copy(
          profiles = profiles,
          activeProfileId = validActiveId
        )
        if (validActiveId != activeId) {
          agentPreferences.setActiveProfileId(validActiveId)
        }
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load profiles", e)
      }
    }
  }

  fun setActiveProfile(id: Long?) {
    viewModelScope.launch {
      try {
        agentPreferences.setActiveProfileId(id)
        _uiState.value = _uiState.value.copy(activeProfileId = id)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to set active profile", e)
      }
    }
  }

  fun addProfile(name: String, preferences: String) {
    viewModelScope.launch {
      try {
        val newId = storage.saveProfile(null, name.trim().ifBlank { "Профиль" }, preferences)
        val profiles = storage.getAllProfiles()
        agentPreferences.setActiveProfileId(newId)
        _uiState.value = _uiState.value.copy(
          profiles = profiles,
          activeProfileId = newId,
          profileEditorId = null,
          profileEditorName = "",
          profileEditorPreferences = "",
          toastMessage = "Профиль добавлен"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to add profile", e)
      }
    }
  }

  fun openProfileEditor(id: Long?) {
    if (id == null) {
      _uiState.value = _uiState.value.copy(
        profileEditorId = null,
        profileEditorName = "",
        profileEditorPreferences = ""
      )
      return
    }
    viewModelScope.launch {
      try {
        val name = _uiState.value.profiles.firstOrNull { it.id == id }?.name ?: ""
        val content = storage.getProfileContent(id) ?: ""
        _uiState.value = _uiState.value.copy(
          profileEditorId = id,
          profileEditorName = name,
          profileEditorPreferences = content
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to open profile editor", e)
      }
    }
  }

  fun updateProfileEditorName(value: String) {
    _uiState.value = _uiState.value.copy(profileEditorName = value)
  }

  fun updateProfileEditorPreferences(value: String) {
    _uiState.value = _uiState.value.copy(profileEditorPreferences = value)
  }

  fun saveProfileEditor() {
    val id = _uiState.value.profileEditorId
    val name = _uiState.value.profileEditorName.trim().ifBlank { "Профиль" }
    val preferences = _uiState.value.profileEditorPreferences
    viewModelScope.launch {
      try {
        val savedId = storage.saveProfile(id, name, preferences)
        val profiles = storage.getAllProfiles()
        _uiState.value = _uiState.value.copy(
          profiles = profiles,
          profileEditorId = null,
          profileEditorName = "",
          profileEditorPreferences = "",
          toastMessage = "Профиль сохранён"
        )
        if (id != null && id == _uiState.value.activeProfileId) {
          _uiState.value = _uiState.value.copy(activeProfileId = savedId)
        }
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save profile", e)
      }
    }
  }

  fun closeProfileEditor() {
    _uiState.value = _uiState.value.copy(
      profileEditorId = null,
      profileEditorName = "",
      profileEditorPreferences = ""
    )
  }

  fun deleteProfile(profileId: Long) {
    viewModelScope.launch {
      try {
        storage.deleteProfile(profileId)
        val profiles = storage.getAllProfiles()
        val wasActive = _uiState.value.activeProfileId == profileId
        val newActiveId = if (wasActive) null else _uiState.value.activeProfileId
        if (wasActive) {
          agentPreferences.setActiveProfileId(null)
        }
        _uiState.value = _uiState.value.copy(
          profiles = profiles,
          activeProfileId = newActiveId,
          profileEditorId = if (_uiState.value.profileEditorId == profileId) null else _uiState.value.profileEditorId,
          profileEditorName = if (_uiState.value.profileEditorId == profileId) "" else _uiState.value.profileEditorName,
          profileEditorPreferences = if (_uiState.value.profileEditorId == profileId) "" else _uiState.value.profileEditorPreferences,
          toastMessage = "Профиль удалён"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to delete profile", e)
      }
    }
  }

  // --- Память задачи ---

  fun loadTaskMemories() {
    viewModelScope.launch {
      try {
        _uiState.value = _uiState.value.copy(taskMemories = storage.getTaskMemories())
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load task memories", e)
      }
    }
  }

  fun loadTaskIntoDialog(taskId: Long) {
    viewModelScope.launch {
      try {
        storage.saveLoadedTaskIdForBranch(_uiState.value.currentBranchId, taskId)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save loaded task for branch", e)
      }
    }
    _uiState.value = _uiState.value.copy(loadedTaskId = taskId)
  }

  fun unloadTaskFromDialog() {
    viewModelScope.launch {
      try {
        storage.saveLoadedTaskIdForBranch(_uiState.value.currentBranchId, null)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to clear loaded task for branch", e)
      }
    }
    _uiState.value = _uiState.value.copy(loadedTaskId = null)
  }

  fun openTaskEditor(taskId: Long) {
    viewModelScope.launch {
      try {
        val name = _uiState.value.taskMemories.firstOrNull { it.id == taskId }?.name ?: "Задача"
        val content = storage.getTaskMemoryContent(taskId) ?: ""
        _uiState.value = _uiState.value.copy(
          taskEditorId = taskId,
          taskEditorName = name,
          taskEditorContent = content
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to open task editor", e)
      }
    }
  }

  fun updateTaskEditorName(value: String) {
    _uiState.value = _uiState.value.copy(taskEditorName = value)
  }

  fun updateTaskEditorContent(value: String) {
    _uiState.value = _uiState.value.copy(taskEditorContent = value)
  }

  fun saveTaskEditor() {
    val id = _uiState.value.taskEditorId ?: return
    val name = _uiState.value.taskEditorName.trim().ifBlank { "Задача" }
    val content = _uiState.value.taskEditorContent
    viewModelScope.launch {
      try {
        storage.updateTaskMemory(id, name, content)
        val updatedList = storage.getTaskMemories()
        _uiState.value = _uiState.value.copy(
          taskMemories = updatedList,
          toastMessage = "Память задачи сохранена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save task editor", e)
      }
    }
  }

  fun saveTaskMemory(name: String, content: String) {
    viewModelScope.launch {
      try {
        val newId = storage.saveTaskMemory(name.trim().ifBlank { "Задача" }, content)
        _uiState.value = _uiState.value.copy(
          taskMemories = storage.getTaskMemories(),
          toastMessage = "Задача добавлена"
        )
        openTaskEditor(newId)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save task memory", e)
      }
    }
  }

  fun deleteTaskMemory(taskId: Long) {
    viewModelScope.launch {
      try {
        storage.deleteTaskMemory(taskId)
        val newList = storage.getTaskMemories()
        val wasLoaded = _uiState.value.loadedTaskId == taskId
        _uiState.value = _uiState.value.copy(
          taskMemories = newList,
          loadedTaskId = if (wasLoaded) null else _uiState.value.loadedTaskId,
          taskEditorId = if (_uiState.value.taskEditorId == taskId) null else _uiState.value.taskEditorId,
          taskEditorName = if (_uiState.value.taskEditorId == taskId) "" else _uiState.value.taskEditorName,
          taskEditorContent = if (_uiState.value.taskEditorId == taskId) "" else _uiState.value.taskEditorContent,
          toastMessage = "Задача удалена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to delete task memory", e)
      }
    }
  }

  fun clearTaskMemories() {
    viewModelScope.launch {
      try {
        storage.clearTaskMemories()
        _uiState.value = _uiState.value.copy(
          taskMemories = emptyList(),
          loadedTaskId = null,
          taskEditorId = null,
          taskEditorName = "",
          taskEditorContent = "",
          toastMessage = "Память задачи очищена"
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to clear task memories", e)
      }
    }
  }

  /** Извлечь факты из текста сообщения и добавить в долговременную память (long-tap). */
  fun addFactsToLongTermFromMessage(messageText: String) {
    _uiState.value = _uiState.value.copy(longTapMessageText = null)
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      repository.extractFactsFromText(messageText)
        .onSuccess { facts ->
          if (facts.isNotBlank()) {
            storage.appendToLongTermMemory(facts)
            _uiState.value = _uiState.value.copy(
              isLoading = false,
              longTermMemory = storage.getLongTermMemory(),
              toastMessage = "Факты добавлены в долговременную память"
            )
          } else {
            _uiState.value = _uiState.value.copy(isLoading = false, toastMessage = "Не удалось извлечь факты")
          }
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(isLoading = false, error = "Ошибка: ${e.message}")
        }
    }
  }

  /** Извлечь факты из текста сообщения и добавить в выбранную задачу (long-tap). */
  fun addFactsToTaskFromMessage(taskId: Long, messageText: String) {
    _uiState.value = _uiState.value.copy(longTapMessageText = null)
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      repository.extractFactsFromText(messageText)
        .onSuccess { facts ->
          if (facts.isNotBlank()) {
            storage.appendToTaskMemory(taskId, facts)
            val updatedContent = storage.getTaskMemoryContent(taskId)
            _uiState.value = _uiState.value.copy(
              isLoading = false,
              taskEditorContent = if (_uiState.value.taskEditorId == taskId) (updatedContent ?: _uiState.value.taskEditorContent) else _uiState.value.taskEditorContent,
              toastMessage = "Факты добавлены в память задачи"
            )
          } else {
            _uiState.value = _uiState.value.copy(isLoading = false, toastMessage = "Не удалось извлечь факты")
          }
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(isLoading = false, error = "Ошибка: ${e.message}")
        }
    }
  }

  fun setLongTapMessage(text: String?) {
    _uiState.value = _uiState.value.copy(longTapMessageText = text)
  }

  fun setShowContextOverflowButton(value: Boolean) {
    viewModelScope.launch {
      try {
        agentPreferences.setShowContextOverflowButton(value)
        _uiState.value = _uiState.value.copy(showContextOverflowButton = value)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to set showContextOverflowButton", e)
      }
    }
  }
}
