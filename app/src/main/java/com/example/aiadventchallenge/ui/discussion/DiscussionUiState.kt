package com.example.aiadventchallenge.ui.discussion

import com.example.aiadventchallenge.domain.ReasoningMode

data class DiscussionUiState(
  val task: String = "",
  val responseDirect: String? = null,
  val responseStepByStep: String? = null,
  val responseSelfPrompt: String? = null,
  /** Промпт, сгенерированный моделью для способа «Свой промпт» (первый запрос). */
  val selfPromptGenerated: String? = null,
  val responseExperts: String? = null,
  /** Режим рассуждения, для которого идёт загрузка (null — не загружается или запущены все). */
  val loadingReasoningMode: ReasoningMode? = null,
  /** Идёт параллельный запуск всех 4 способов («Запустить все»). */
  val isRunningAll: Boolean = false,
  /** Идёт запрос сравнения ответов. */
  val isComparing: Boolean = false,
  val comparisonText: String? = null,
  val error: String? = null
) {
  fun responseFor(mode: ReasoningMode): String? = when (mode) {
    ReasoningMode.Direct -> responseDirect
    ReasoningMode.StepByStep -> responseStepByStep
    ReasoningMode.SelfPrompt -> responseSelfPrompt
    ReasoningMode.Experts -> responseExperts
  }

  fun hasAtLeastTwoResponses(): Boolean =
    listOf(responseDirect, responseStepByStep, responseSelfPrompt, responseExperts)
      .count { !it.isNullOrBlank() } >= 2
}
