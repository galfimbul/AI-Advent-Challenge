package com.example.aiadventchallenge.ui.discussion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.domain.ReasoningMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscussionViewModel(
  private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(DiscussionUiState())
  val uiState: StateFlow<DiscussionUiState> = _uiState.asStateFlow()

  fun updateTask(text: String) {
    _uiState.value = _uiState.value.copy(task = text, error = null)
  }

  fun runMode(mode: ReasoningMode) {
    val task = _uiState.value.task.trim()
    if (task.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(loadingReasoningMode = mode, error = null)
      repository.solveWithReasoningMode(task, mode)
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(
            loadingReasoningMode = null,
            responseDirect = if (mode == ReasoningMode.Direct) result.content else _uiState.value.responseDirect,
            responseStepByStep = if (mode == ReasoningMode.StepByStep) result.content else _uiState.value.responseStepByStep,
            responseSelfPrompt = if (mode == ReasoningMode.SelfPrompt) result.content else _uiState.value.responseSelfPrompt,
            selfPromptGenerated = if (mode == ReasoningMode.SelfPrompt) result.generatedPrompt else _uiState.value.selfPromptGenerated,
            responseExperts = if (mode == ReasoningMode.Experts) result.content else _uiState.value.responseExperts,
            error = null
          )
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            loadingReasoningMode = null,
            error = e.message ?: "Ошибка запроса"
          )
        }
    }
  }

  fun runAll() {
    val task = _uiState.value.task.trim()
    if (task.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRunningAll = true, error = null)
      val results = coroutineScope {
        ReasoningMode.entries.map { mode ->
          async { mode to repository.solveWithReasoningMode(task, mode) }
        }.awaitAll()
      }
      var state = _uiState.value.copy(isRunningAll = false, loadingReasoningMode = null)
      var firstError: String? = null
      results.forEach { (mode, result) ->
        result.onSuccess { r ->
          state = state.copy(
            responseDirect = if (mode == ReasoningMode.Direct) r.content else state.responseDirect,
            responseStepByStep = if (mode == ReasoningMode.StepByStep) r.content else state.responseStepByStep,
            responseSelfPrompt = if (mode == ReasoningMode.SelfPrompt) r.content else state.responseSelfPrompt,
            selfPromptGenerated = if (mode == ReasoningMode.SelfPrompt) r.generatedPrompt else state.selfPromptGenerated,
            responseExperts = if (mode == ReasoningMode.Experts) r.content else state.responseExperts
          )
        }.onFailure { e ->
          if (firstError == null) firstError = e.message ?: "Ошибка запроса"
        }
      }
      _uiState.value = state.copy(error = firstError)
    }
  }

  fun compare() {
    if (!_uiState.value.hasAtLeastTwoResponses()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        comparisonText = null,
        error = null,
        isComparing = true
      )
      val state = _uiState.value
      repository.compareResponses(
        task = state.task.trim(),
        direct = state.responseDirect.orEmpty(),
        stepByStep = state.responseStepByStep.orEmpty(),
        selfPrompt = state.responseSelfPrompt.orEmpty(),
        experts = state.responseExperts.orEmpty()
      )
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(
            comparisonText = result.content,
            error = null,
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
