package com.example.aiadventchallenge.ui.agent

import com.example.aiadventchallenge.domain.agent.AgentMessage

/**
 * Состояние экрана «Агент»: история диалога (чат), ввод запроса, загрузка, ошибка, токены последнего ответа,
 * настройки сжатия контекста (useCompression, lastN).
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
  /** Режим, в котором получены последние токены (для подписи в UI). */
  val lastTokensModeCompression: Boolean? = null,
  /** Сообщение для тоста (одноразовое, показывается и сбрасывается UI). */
  val toastMessage: String? = null
)
