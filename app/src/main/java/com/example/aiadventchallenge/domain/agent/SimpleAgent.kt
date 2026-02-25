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
    forceContextOverflow: Boolean = false
  ): Result<AgentResponse> {
    val trimmed = userRequest.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Пустой запрос"))
    }

    val historyText = buildString {
      dialog.messages.forEach { msg ->
        val prefix = when (msg.role) {
          AgentRole.User -> "Пользователь"
          AgentRole.Assistant -> "Агент"
        }
        append(prefix)
        append(": ")
        append(msg.text)
        append('\n')
      }
    }.ifBlank { "(пока нет сообщений)" }

    val prompt = buildString {
      append("История диалога между пользователем и агентом:\n")
      append(historyText)
      append("\n\nНовый запрос пользователя:\n")
      append(trimmed)
      append("\n\nДай развёрнутый, но по существу ответ, учитывая контекст беседы.")
      if (forceContextOverflow) {
        val chunk = " заполнение контекста для теста. "
        repeat(20_000) { append(chunk) } // ~500k символов → превышение лимита контекста API
      }
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
        dialog = AgentDialogState(newMessages),
        raw = chatResponse
      )
    }
  }
}

