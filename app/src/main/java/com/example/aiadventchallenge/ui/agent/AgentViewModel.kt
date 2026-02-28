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
        dialogState = storage.load()
        _uiState.value = _uiState.value.copy(messages = dialogState.messages)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load dialog", e)
      }
    }
    viewModelScope.launch {
      try {
        val settings = compressionPreferences.getSettings()
        _uiState.value = _uiState.value.copy(
          useCompression = settings.useCompression,
          lastN = settings.lastN
        )
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to load compression settings", e)
      }
    }
  }

  fun updateRequest(text: String) {
    _uiState.value = _uiState.value.copy(request = text, error = null)
  }

  fun sendRequest() {
    val request = _uiState.value.request.trim()
    if (request.isEmpty()) return

    val useCompression = _uiState.value.useCompression
    val lastN = _uiState.value.lastN

    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      agent.process(dialogState, request, useCompression = useCompression, lastN = lastN)
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
            lastTokensModeCompression = useCompression
          )
          try {
            storage.save(dialogState)
            if (useCompression) {
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

  fun setUseCompression(value: Boolean) {
    _uiState.value = _uiState.value.copy(useCompression = value)
    viewModelScope.launch {
      try {
        compressionPreferences.setUseCompression(value)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to save useCompression", e)
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

  /** Тест превышения контекста: отправляет запрос с искусственно раздутым промптом. Только для debug. */
  fun sendContextOverflowTest() {
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      agent.process(dialogState, "Тест: превышение контекста", forceContextOverflow = true)
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
          dialogState = AgentDialogState(summaries = dialogState.summaries, messages = dialogState.messages + testMsg)
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
        dialogState = AgentDialogState()
        _uiState.value = _uiState.value.copy(messages = emptyList())
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to clear dialog", e)
      }
    }
  }
}
