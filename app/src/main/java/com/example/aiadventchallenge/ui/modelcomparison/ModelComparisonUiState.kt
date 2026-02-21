package com.example.aiadventchallenge.ui.modelcomparison

import com.example.aiadventchallenge.data.ModelRunResult

data class ModelComparisonUiState(
  val prompt: String = "",
  val runs: List<ModelRunResult> = emptyList(),
  val isLoading: Boolean = false,
  val isComparing: Boolean = false,
  val error: String? = null,
  val conclusionText: String? = null
) {
  fun hasAllThreeRuns(): Boolean = runs.size >= 3
}
