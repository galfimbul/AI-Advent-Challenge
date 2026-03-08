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

/** Информация о ветке диалога (Branching). */
data class BranchInfo(
  val id: Long,
  val name: String
)

data class AgentDialogState(
  val summaries: List<String> = emptyList(),
  val messages: List<AgentMessage> = emptyList(),
  /** Текстовый блок фактов (StickyFacts). */
  val facts: String = "",
  /** Текущая ветка (Branching). */
  val currentBranchId: Long = 0L,
  /** Список веток (id, name) для переключателя. */
  val branches: List<BranchInfo> = emptyList(),
  /** Id задачи, подключённой к этой ветке диалога. null — не подключена. */
  val loadedTaskId: Long? = null
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
    contextStrategy: ContextStrategy = ContextStrategy.SlidingWindow,
    lastN: Int = 10,
    forceContextOverflow: Boolean = false,
    longTermMemory: String = "",
    taskMemory: String? = null,
    taskState: TaskState? = null,
    userProfile: String = "",
    invariantsText: String = ""
  ): Result<AgentResponse> {
    val trimmed = userRequest.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Пустой запрос"))
    }

    val historyText = when {
      forceContextOverflow -> buildOverflowHistory(dialog.messages)
      contextStrategy == ContextStrategy.SlidingWindow -> buildMessagesBlock(dialog.messages.takeLast(lastN.coerceAtLeast(1)))
      contextStrategy == ContextStrategy.StickyFacts -> buildFactsAndMessages(dialog.facts, dialog.messages.takeLast(lastN.coerceAtLeast(1)))
      contextStrategy == ContextStrategy.Branching -> buildMessagesBlock(dialog.messages)
      contextStrategy == ContextStrategy.Summary -> buildSummaryAndMessages(dialog.summaries, dialog.messages.takeLast(lastN.coerceAtLeast(1)))
      else -> buildMessagesBlock(dialog.messages)
    }

    val prompt = buildString {
      if (userProfile.isNotBlank()) {
        append("Профиль пользователя (учитывай в ответах: стиль, формат, ограничения):\n")
        append(userProfile)
        append("\n\n")
      }
      if (longTermMemory.isNotBlank()) {
        append("Долговременная память (профиль, решения, знания):\n")
        append(longTermMemory)
        append("\n\n")
      }
      if (!taskMemory.isNullOrBlank()) {
        append("Память текущей задачи:\n")
        append(taskMemory)
        if (taskState != null) {
          append("\nСостояние задачи: этап — ")
          append(taskState.stage.displayName())
          append(", ожидаемое действие: ")
          append(taskState.stage.expectedActionText())
          append(".")
          if (taskState.isPaused) {
            append(" Задача на паузе (пользователь покинул экран).")
          } else {
            append(" Задача активна. Продолжай с этого места, не повторяй предыдущие объяснения.")
          }
          append("\n")
        }
        append("\n")
      }
      if (contextStrategy == ContextStrategy.StickyFacts) {
        append("Учитывай блок «Факты» как источник целей, ограничений и договорённостей; не противоречь им в ответе.\n\n")
      }
      append("История диалога между пользователем и агентом:\n")
      append(historyText)
      append("\n\nНовый запрос пользователя:\n")
      append(trimmed)
      append("\n\nДай развёрнутый, но по существу ответ, учитывая контекст беседы.")
    }

    val invariantsBlock = buildString {
      append("\n\nИнварианты (правила, которые нельзя нарушать):\n")
      append(if (invariantsText.isNotBlank()) invariantsText else "Ограничений на ответ нет")
      append("\n\nЭти инварианты нельзя нарушать ни при каких условиях. Если запрос пользователя противоречит любому инварианту — откажи в выполнении и чётко объясни, какой инвариант нарушен и почему ты не можешь выполнить запрос.")
    }
    val systemMessage = buildString {
      append(ChatRepository.DEFAULT_SYSTEM_MESSAGE)
      if (userProfile.isNotBlank()) {
        append("\n\nУчитывай предпочтения пользователя (стиль, формат, ограничения):\n")
        append(userProfile)
      }
      append(invariantsBlock)
    }
    return repository.sendMessage(
      userMessage = prompt,
      systemMessage = systemMessage,
      maxTokens = null,
      stopPhrases = null
    ).map { chatResponse ->
      val newMessages = dialog.messages +
        AgentMessage(AgentRole.User, trimmed) +
        AgentMessage(AgentRole.Assistant, chatResponse.content)
      AgentResponse(
        reply = chatResponse.content,
        dialog = dialog.copy(messages = newMessages),
        raw = chatResponse
      )
    }
  }

  private fun buildOverflowHistory(messages: List<AgentMessage>): String = buildString {
    messages.forEach { msg ->
      val prefix = when (msg.role) {
        AgentRole.User -> "Пользователь"
        AgentRole.Assistant -> "Агент"
      }
      append(prefix).append(": ").append(msg.text).append('\n')
    }
    val chunk = " заполнение контекста для теста. "
    repeat(20_000) { append(chunk) }
  }

  private fun buildMessagesBlock(messages: List<AgentMessage>): String = buildString {
    messages.forEach { msg ->
      val prefix = when (msg.role) {
        AgentRole.User -> "Пользователь"
        AgentRole.Assistant -> "Агент"
      }
      append(prefix).append(": ").append(msg.text).append('\n')
    }
  }.ifBlank { "(пока нет сообщений)" }

  private fun buildFactsAndMessages(facts: String, messages: List<AgentMessage>): String = buildString {
    append("Факты из диалога (цели, ограничения, договорённости):\n")
    append(if (facts.isNotBlank()) facts else "(пока нет извлечённых фактов)")
    append("\n\nПоследние сообщения:\n")
    append(buildMessagesBlock(messages))
  }

  private fun buildSummaryAndMessages(summaries: List<String>, messages: List<AgentMessage>): String = buildString {
    if (summaries.isNotEmpty()) {
      append("Краткое содержание более ранней части диалога:\n")
      append(summaries.joinToString("\n\n"))
      append("\n\n")
    }
    append("Актуальная часть диалога:\n")
    append(buildMessagesBlock(messages))
  }.ifBlank { "(пока нет сообщений)" }
}

