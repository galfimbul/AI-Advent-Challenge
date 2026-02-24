package com.example.aiadventchallenge.ui.agent

import com.example.aiadventchallenge.domain.agent.AgentMessage

/**
 * Состояние экрана «Агент»: история диалога (чат), ввод запроса, загрузка, ошибка, токены последнего ответа.
 */
data class AgentUiState(
  val messages: List<AgentMessage> = emptyList(),
  val request: String = "",
  val isLoading: Boolean = false,
  val error: String? = null,
  val promptTokens: Int? = null,
  val completionTokens: Int? = null,
  val totalTokens: Int? = null
)
