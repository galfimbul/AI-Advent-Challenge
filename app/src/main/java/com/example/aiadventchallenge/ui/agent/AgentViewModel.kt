package com.example.aiadventchallenge.ui.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.SimpleAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_TAG = "AgentViewModel"

/**
 * ViewModel экрана «Агент». Вызывает только агента (agent.process), не обращается к ChatRepository напрямую.
 * Диалог загружается из Room при создании и сохраняется после каждого ответа.
 */
class AgentViewModel(
  private val agent: SimpleAgent,
  private val storage: AgentDialogStorage
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
  }

  fun updateRequest(text: String) {
    _uiState.value = _uiState.value.copy(request = text, error = null)
  }

  fun sendRequest() {
    val request = _uiState.value.request.trim()
    if (request.isEmpty()) return

    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      agent.process(dialogState, request)
        .onSuccess { agentResponse ->
          dialogState = agentResponse.dialog
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            messages = agentResponse.dialog.messages,
            request = "",
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
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = e.message ?: e.toString()
          )
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
