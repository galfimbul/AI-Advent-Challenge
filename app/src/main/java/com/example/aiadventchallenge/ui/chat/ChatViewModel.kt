package com.example.aiadventchallenge.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
  val query: String = "",
  val response: String = "",
  val isLoading: Boolean = false,
  val error: String? = null
)

class ChatViewModel(
  private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  fun updateQuery(text: String) {
    _uiState.value = _uiState.value.copy(query = text, error = null)
  }

  fun sendRequest() {
    val query = _uiState.value.query.trim()
    if (query.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      repository.sendMessage(query)
        .onSuccess { answer ->
          _uiState.value = _uiState.value.copy(
            response = answer,
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
