package com.example.aiadventchallenge.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.SimpleAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Агент». Вызывает только агента (agent.process), не обращается к ChatRepository напрямую.
 */
class AgentViewModel(
  private val agent: SimpleAgent = SimpleAgent(ChatRepository())
) : ViewModel() {

  private val _uiState = MutableStateFlow(AgentUiState())
  val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

  private var dialogState: AgentDialogState = AgentDialogState()

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
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = e.message ?: e.toString()
          )
        }
    }
  }
}
