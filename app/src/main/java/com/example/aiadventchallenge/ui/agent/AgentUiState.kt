package com.example.aiadventchallenge.ui.agent

import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.BranchInfo
import com.example.aiadventchallenge.domain.agent.ContextStrategy

/**
 * Состояние экрана «Агент»: история диалога (чат), ввод запроса, загрузка, ошибка, токены,
 * стратегия контекста (contextStrategy), ветки, facts.
 */
data class AgentUiState(
  val messages: List<AgentMessage> = emptyList(),
  val request: String = "",
  val isLoading: Boolean = false,
  val error: String? = null,
  val promptTokens: Int? = null,
  val completionTokens: Int? = null,
  val totalTokens: Int? = null,
  val useCompression: Boolean = false,
  val lastN: Int = 10,
  val contextStrategy: ContextStrategy = ContextStrategy.SlidingWindow,
  val currentBranchId: Long = 1L,
  val branches: List<BranchInfo> = emptyList(),
  val facts: String = "",
  /** Показать диалог ввода имени при создании ветки. */
  val showCreateBranchDialog: Boolean = false,
  /** Ввод имени новой ветки в диалоге. */
  val createBranchNameInput: String = "Ветка 2",
  val lastTokensModeCompression: Boolean? = null,
  val toastMessage: String? = null,
  val settingsSheetOpen: Boolean = false
)
