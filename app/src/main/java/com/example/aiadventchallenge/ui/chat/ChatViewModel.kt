package com.example.aiadventchallenge.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.MAX_TOKENS
import com.example.aiadventchallenge.data.STOP_SEQUENCE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
  val query: String = "",
  val response: String = "",
  val isLoading: Boolean = false,
  val error: String? = null,
  /** Сырая строка из поля ввода; позволяет свободно вводить "100" поверх "256". */
  val maxTokensInput: String = "256",
  val unlimitedTokens: Boolean = false,
  val promptTokens: Int? = null,
  val completionTokens: Int? = null,
  val totalTokens: Int? = null,
  val finishReason: String? = null
)

class ChatViewModel(
  private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  fun updateQuery(text: String) {
    _uiState.value = _uiState.value.copy(query = text, error = null)
  }

  fun updateMaxTokens(value: String) {
    if (value.isEmpty() || value.all { it.isDigit() }) {
      _uiState.value = _uiState.value.copy(maxTokensInput = value, unlimitedTokens = false)
    }
  }

  fun setUnlimitedTokens(unlimited: Boolean) {
    _uiState.value = _uiState.value.copy(
      unlimitedTokens = unlimited,
      maxTokensInput = if (unlimited) "" else "256"
    )
  }

  fun sendRequest() {
    val query = _uiState.value.query.trim()
    if (query.isEmpty()) return
    val maxTokens = when {
      _uiState.value.unlimitedTokens -> null
      else -> _uiState.value.maxTokensInput.toIntOrNull()?.takeIf { it > 0 } ?: MAX_TOKENS
    }
    val stopPhrases = when {
      _uiState.value.unlimitedTokens -> null
      else -> listOf(STOP_SEQUENCE)
    }

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      repository.sendMessage(query, systemMessage = null, maxTokens = maxTokens, stopPhrases = stopPhrases)
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(
            response = result.content,
            promptTokens = result.promptTokens,
            completionTokens = result.completionTokens,
            totalTokens = result.totalTokens,
            finishReason = result.finishReason,
            isLoading = false,
            error = null
          )
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = e.message ?: "Ошибка запроса"
          )
        }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }
}
