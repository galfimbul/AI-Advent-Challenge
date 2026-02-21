package com.example.aiadventchallenge.ui.temperature

data class TemperatureRun(
  val temp: Float,
  val response: String
)

data class TemperatureUiState(
  val prompt: String = "",
  val sliderTemperature: Float = 0.7f,
  val runs: List<TemperatureRun> = emptyList(),
  /** Температура, для которой идёт загрузка (null — не идёт одиночный запрос). */
  val loadingTemperature: Float? = null,
  val isRunningAll: Boolean = false,
  val isComparing: Boolean = false,
  val comparisonText: String? = null,
  val error: String? = null
) {
  fun hasAtLeastTwoRuns(): Boolean = runs.size >= 2
}
