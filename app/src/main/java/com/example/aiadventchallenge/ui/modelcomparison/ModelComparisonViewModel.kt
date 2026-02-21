package com.example.aiadventchallenge.ui.modelcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.ModelRunResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelComparisonViewModel(
  private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(ModelComparisonUiState())
  val uiState: StateFlow<ModelComparisonUiState> = _uiState.asStateFlow()

  fun updatePrompt(text: String) {
    _uiState.value = _uiState.value.copy(
      prompt = text,
      runs = emptyList(),
      conclusionText = null,
      error = null
    )
  }

  fun runAllThree() {
    val prompt = _uiState.value.prompt.trim()
    if (prompt.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null, runs = emptyList())
      val models = ChatRepository.MODELS_FOR_COMPARISON
      val results = coroutineScope {
        models.map { (modelId, displayName) ->
          async { repository.runWithModel(prompt, modelId, displayName) }
        }.awaitAll()
      }
      val runs = mutableListOf<ModelRunResult>()
      var firstError: String? = null
      results.forEachIndexed { index, result ->
        result
          .onSuccess { runs.add(it) }
          .onFailure { e -> if (firstError == null) firstError = e.message ?: "Ошибка запроса" }
      }
      _uiState.value = _uiState.value.copy(
        isLoading = false,
        runs = runs,
        error = firstError
      )
    }
  }

  fun compare() {
    if (!_uiState.value.hasAllThreeRuns()) return
    viewModelScope.launch {
      val state = _uiState.value
      _uiState.value = state.copy(conclusionText = null, error = null, isComparing = true)
      repository.compareModelResponses(state.prompt.trim(), state.runs)
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(
            conclusionText = result.content,
            isComparing = false
          )
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            error = e.message ?: "Ошибка сравнения",
            isComparing = false
          )
        }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }
}
