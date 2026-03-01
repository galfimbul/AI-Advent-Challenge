package com.example.aiadventchallenge.ui.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentCompressionPreferences
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
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
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = dialogState.currentBranchId,
          facts = dialogState.facts
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
      agent.process(stateToSend, request, contextStrategy = contextStrategy, lastN = lastN)
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
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          currentBranchId = dialogState.currentBranchId,
          facts = dialogState.facts
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
      agent.process(dialogState, "Тест: превышение контекста", contextStrategy = ContextStrategy.SlidingWindow, lastN = 10, forceContextOverflow = true)
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

  fun clearDialog() {
    viewModelScope.launch {
      try {
        storage.clear()
        dialogState = storage.load(1L)
        _uiState.value = _uiState.value.copy(
          messages = dialogState.messages,
          branches = dialogState.branches,
          currentBranchId = 1L,
          facts = "",
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
}
