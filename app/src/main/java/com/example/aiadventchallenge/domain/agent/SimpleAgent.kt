package com.example.aiadventchallenge.domain.agent

import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.ChatResponse

enum class AgentRole {
  User,
  Assistant
}

data class AgentMessage(
  val role: AgentRole,
  val text: String
)

data class AgentDialogState(
  val summaries: List<String> = emptyList(),
  val messages: List<AgentMessage> = emptyList()
)

data class AgentResponse(
  val reply: String,
  val dialog: AgentDialogState,
  val raw: ChatResponse
)

/**
 * Простой агент поверх ChatRepository:
 * принимает историю диалога и новый запрос, сам формирует промпт с контекстом,
 * вызывает LLM и возвращает обновлённый диалог.
 */
class SimpleAgent(
  private val repository: ChatRepository = ChatRepository()
) {

  suspend fun process(
    dialog: AgentDialogState,
    userRequest: String,
    useCompression: Boolean = false,
    lastN: Int = 10,
    forceContextOverflow: Boolean = false
  ): Result<AgentResponse> {
    val trimmed = userRequest.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Пустой запрос"))
    }

    val historyText = when {
      forceContextOverflow -> {
        buildString {
          dialog.messages.forEach { msg ->
            val prefix = when (msg.role) {
              AgentRole.User -> "Пользователь"
              AgentRole.Assistant -> "Агент"
            }
            append(prefix).append(": ").append(msg.text).append('\n')
          }
          val chunk = " заполнение контекста для теста. "
          repeat(20_000) { append(chunk) }
        }
      }
      useCompression -> {
        val lastMessages = dialog.messages.takeLast(lastN.coerceAtLeast(1))
        buildString {
          if (dialog.summaries.isNotEmpty()) {
            append("Краткое содержание более ранней части диалога:\n")
            append(dialog.summaries.joinToString("\n\n"))
            append("\n\n")
          }
          append("Актуальная часть диалога:\n")
          lastMessages.forEach { msg ->
            val prefix = when (msg.role) {
              AgentRole.User -> "Пользователь"
              AgentRole.Assistant -> "Агент"
            }
            append(prefix).append(": ").append(msg.text).append('\n')
          }
        }.ifBlank { "(пока нет сообщений)" }
      }
      else -> {
        buildString {
          dialog.messages.forEach { msg ->
            val prefix = when (msg.role) {
              AgentRole.User -> "Пользователь"
              AgentRole.Assistant -> "Агент"
            }
            append(prefix).append(": ").append(msg.text).append('\n')
          }
        }.ifBlank { "(пока нет сообщений)" }
      }
    }

    val prompt = buildString {
      append("История диалога между пользователем и агентом:\n")
      append(historyText)
      append("\n\nНовый запрос пользователя:\n")
      append(trimmed)
      append("\n\nДай развёрнутый, но по существу ответ, учитывая контекст беседы.")
    }

    return repository.sendMessage(
      userMessage = prompt,
      maxTokens = null,
      stopPhrases = null
    ).map { chatResponse ->
      val newMessages = dialog.messages +
        AgentMessage(AgentRole.User, trimmed) +
        AgentMessage(AgentRole.Assistant, chatResponse.content)
      AgentResponse(
        reply = chatResponse.content,
        dialog = AgentDialogState(summaries = dialog.summaries, messages = newMessages),
        raw = chatResponse
      )
    }
  }
}

