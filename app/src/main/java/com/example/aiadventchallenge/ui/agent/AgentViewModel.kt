package com.example.aiadventchallenge.ui.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentCompressionPreferences
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.data.agent.TaskMemoryItem
import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import com.example.aiadventchallenge.domain.agent.SimpleAgent
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
  private val compressionPreferences: AgentCompressionPreferences
) : ViewModel() {

  private val _uiState = MutableStateFlow(AgentUiState())
  val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

  private var dialogState: AgentDialogState = AgentDialogState()

  init {
    viewModelScope.launch {
      try {
        val settings = compressionPreferences.getSettings()
        _uiState.value = _uiState.value.copy(
          contextStrategy = settings.contextStrategy,
          lastN = settings.lastN,
          useCompression = settings.useCompression
        )
        dialogState = storage.load(_uiState.value.currentBranchId)
        val longTerm = storage.getLongTermMemory()
        val taskList = storage.getTaskMemories()
        val restoredLoadedId = dialogState.loadedTaskId
        val loadedId = restoredLoadedId?.takeIf { id -> taskList.any { it.id == id } }
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = dialogState.currentBranchId,
          facts = dialogState.facts,
          longTermMemory = longTerm,
          taskMemories = taskList,
          loadedTaskId = loadedId
        )
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

    if (request.startsWith("/")) {
      handleMemoryCommand(request)
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
      val longTerm = _uiState.value.longTermMemory
      val taskMemory = _uiState.value.loadedTaskId?.let { id -> storage.getTaskMemoryContent(id) }
      agent.process(
        stateToSend,
        request,
        contextStrategy = contextStrategy,
        lastN = lastN,
        longTermMemory = longTerm,
        taskMemory = taskMemory
      )
        .onSuccess { agentResponse ->
          dialogState = agentResponse.dialog
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            messages = agentResponse.dialog.messages,
            request = "",
            error = null,
            promptTokens = agentResponse.raw.promptTokens,
            completionTokens = agentResponse.raw.completionTokens,
            totalTokens = agentResponse.raw.totalTokens,
            lastTokensModeCompression = (contextStrategy == ContextStrategy.Summary),
            facts = agentResponse.dialog.facts
          )
          try {
            storage.save(dialogState)
            if (contextStrategy == ContextStrategy.Summary) {
              ensureSummaries(dialogState)
            }
          } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to save dialog", e)
          }
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = e.message ?: e.toString()
          )
        }
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

  private fun handleMemoryCommand(input: String) {
    val trimmed = input.trim()
    when {
      trimmed.equals("/help", ignoreCase = true) || trimmed.equals("/memory_help", ignoreCase = true) -> {
        _uiState.value = _uiState.value.copy(
          request = "",
          toastMessage = "Команды: /add_long_term текст — факты в долговременную память; /add_task_memory текст — факты в загруженную задачу; /help — эта подсказка"
        )
      }
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

  fun setContextStrategy(value: ContextStrategy) {
    _uiState.value = _uiState.value.copy(contextStrategy = value, useCompression = (value == ContextStrategy.Summary))
    viewModelScope.launch {
      try {
        compressionPreferences.setContextStrategy(value)
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
        compressionPreferences.setLastN(n)
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
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          currentBranchId = dialogState.currentBranchId,
          facts = dialogState.facts,
          loadedTaskId = loadedId
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
    refreshLongTermMemory()
    loadTaskMemories()
    _uiState.value.loadedTaskId?.let { openTaskEditor(it) }
  }

  fun closeSettingsSheet() {
    _uiState.value = _uiState.value.copy(settingsSheetOpen = false)
  }

  fun setUseCompression(value: Boolean) {
    val strategy = if (value) ContextStrategy.Summary else ContextStrategy.SlidingWindow
    setContextStrategy(strategy)
  }

  /** Тест превышения контекста: отправляет запрос с искусственно раздутым промптом. Только для debug. */
  fun sendContextOverflowTest() {
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      agent.process(dialogState, "Тест: превышение контекста", contextStrategy = ContextStrategy.SlidingWindow, lastN = 10, forceContextOverflow = true, longTermMemory = "", taskMemory = null)
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
        val taskList = storage.getTaskMemories()
        _uiState.value = _uiState.value.copy(
          showClearConfirmDialog = false,
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = 1L,
          facts = "",
          taskMemories = taskList,
          loadedTaskId = if (alsoTaskMemory) null else _uiState.value.loadedTaskId,
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
        _uiState.value = _uiState.value.copy(
          taskMemories = newList,
          loadedTaskId = if (_uiState.value.loadedTaskId == taskId) null else _uiState.value.loadedTaskId,
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
}
