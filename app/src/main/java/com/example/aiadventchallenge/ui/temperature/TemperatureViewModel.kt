package com.example.aiadventchallenge.ui.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.domain.TEMPERATURE_PRESETS
import com.example.aiadventchallenge.domain.MAX_TEMP
import com.example.aiadventchallenge.domain.MIN_TEMP
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TemperatureViewModel(
  private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

  private val _uiState = MutableStateFlow(TemperatureUiState())
  val uiState: StateFlow<TemperatureUiState> = _uiState.asStateFlow()

  fun updatePrompt(text: String) {
    _uiState.value = _uiState.value.copy(
      prompt = text,
      runs = emptyList(),
      comparisonText = null,
      error = null
    )
  }

  fun updateSlider(value: Float) {
    _uiState.value = _uiState.value.copy(
      sliderTemperature = value.coerceIn(MIN_TEMP, MAX_TEMP)
    )
  }

  private fun addOrUpdateRun(temp: Float, response: String) {
    val current = _uiState.value.runs
    val updated = current.filter { it.temp != temp } + TemperatureRun(temp, response)
    _uiState.value = _uiState.value.copy(
      runs = updated.sortedBy { it.temp }
    )
  }

  fun runTemperature(temp: Float) {
    val prompt = _uiState.value.prompt.trim()
    if (prompt.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(loadingTemperature = temp, error = null)
      repository.sendWithTemperature(prompt, temp)
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(loadingTemperature = null)
          addOrUpdateRun(temp, result.content)
        }
        .onFailure { e ->
          _uiState.value = _uiState.value.copy(
            loadingTemperature = null,
            error = e.message ?: "Ошибка запроса"
          )
        }
    }
  }

  fun runWithSlider() {
    runTemperature(_uiState.value.sliderTemperature)
  }

  fun runAll() {
    val prompt = _uiState.value.prompt.trim()
    if (prompt.isEmpty()) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isRunningAll = true, error = null)
      val presets = TEMPERATURE_PRESETS.map { it.first }
      val results = coroutineScope {
        presets.map { temp ->
          async { temp to repository.sendWithTemperature(prompt, temp) }
        }.awaitAll()
      }
      val currentRuns = _uiState.value.runs.toMutableList()
      var firstError: String? = null
      results.forEach { (temp, result) ->
        result.onSuccess { r ->
          currentRuns.removeAll { it.temp == temp }
          currentRuns.add(TemperatureRun(temp, r.content))
        }.onFailure { e -> if (firstError == null) firstError = e.message ?: "Ошибка запроса" }
      }
      _uiState.value = _uiState.value.copy(
        isRunningAll = false,
        runs = currentRuns.sortedBy { it.temp },
        error = firstError
      )
    }
  }

  fun compare() {
    if (!_uiState.value.hasAtLeastTwoRuns()) return
    viewModelScope.launch {
      val state = _uiState.value
      _uiState.value = state.copy(comparisonText = null, error = null, isComparing = true)
      val runs = state.runs.map { it.temp to it.response }
      repository.compareTemperatureResponses(state.prompt.trim(), runs)
        .onSuccess { result ->
          _uiState.value = _uiState.value.copy(
            comparisonText = result.content,
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
