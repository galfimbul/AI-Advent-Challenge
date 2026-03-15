package com.example.aiadventchallenge.domain.agent

import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.AgentToolConstants
import com.example.aiadventchallenge.data.AgentTools
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.ChatResponse
import com.example.aiadventchallenge.data.ChatResponseWithToolCalls
import com.example.aiadventchallenge.data.mcp.McpCustomClient
import com.example.aiadventchallenge.data.mcp.McpWeatherClient
import com.example.aiadventchallenge.data.openai.ChatMessage
import com.example.aiadventchallenge.data.openai.ChatTool
import com.example.aiadventchallenge.data.openai.OutgoingToolCall
import com.example.aiadventchallenge.data.openai.OutgoingToolCallFunction
import com.example.aiadventchallenge.data.openai.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject

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

private const val MAX_TOOL_ROUNDS = 5

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
    invariantsText: String = "",
    reminderScheduler: ReminderScheduler? = null,
    userTimezone: String = ""
  ): Result<AgentResponse> {
    val trimmed = userRequest.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Пустой запрос"))
    }

    val tools = if (BuildConfig.MCP_CUSTOM_SERVER_URL.isNotBlank()) AgentTools.tools else null
    if (!tools.isNullOrEmpty()) {
      return processWithTools(
        dialog = dialog,
        userRequest = trimmed,
        contextStrategy = contextStrategy,
        lastN = lastN,
        longTermMemory = longTermMemory,
        taskMemory = taskMemory,
        taskState = taskState,
        userProfile = userProfile,
        invariantsText = invariantsText,
        tools = tools,
        reminderScheduler = reminderScheduler,
        userTimezone = userTimezone
      )
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
      if (taskState != null) {
        append("Состояние задачи: этап — ")
        append(taskState.stage.displayName())
        append(", ожидаемое действие: ")
        append(taskState.stage.expectedActionText())
        append(".")
        if (taskState.isPaused) {
          append(" Задача на паузе (пользователь покинул экран).")
        } else {
          append(" Задача активна. Продолжай с этого места, не повторяй предыдущие объяснения.")
        }
        append("\nСТРОГОЕ ПРАВИЛО: ты не можешь выполнять работу, относящуюся к другому этапу. ")
        append("Переход между этапами возможен ТОЛЬКО по команде пользователя /confirm. ")
        append("Если пользователь просит сделать что-то из следующего этапа — откажи и объясни, ")
        append("что сначала нужно подтвердить текущий этап командой /confirm. ")
        append("На этапе Планирование — только составляй план, НЕ выполняй его. ")
        append("На этапе Выполнение — только выполняй шаги утверждённого плана. ")
        append("На этапе Проверка — только проверяй результат выполнения. ")
        append("После завершения работы на текущем этапе — всегда напоминай пользователю подтвердить командой /confirm.")
        append("\n\n")
      }
      if (!taskMemory.isNullOrBlank()) {
        append("Память текущей задачи:\n")
        append(taskMemory)
        append("\n\n")
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

  private suspend fun processWithTools(
    dialog: AgentDialogState,
    userRequest: String,
    contextStrategy: ContextStrategy,
    lastN: Int,
    longTermMemory: String,
    taskMemory: String?,
    taskState: TaskState?,
    userProfile: String,
    invariantsText: String,
    tools: List<ChatTool>,
    reminderScheduler: ReminderScheduler?,
    userTimezone: String
  ): Result<AgentResponse> {
    val historyMessages = when (contextStrategy) {
      ContextStrategy.SlidingWindow -> dialog.messages.takeLast(lastN.coerceAtLeast(1))
      ContextStrategy.Branching -> dialog.messages
      ContextStrategy.StickyFacts,
      ContextStrategy.Summary -> dialog.messages.takeLast(lastN.coerceAtLeast(1))
      else -> dialog.messages.takeLast(lastN.coerceAtLeast(1))
    }
    val systemContent = buildSystemMessageWithInvariants(
      userProfile = userProfile,
      invariantsText = invariantsText,
      taskState = taskState,
      taskMemory = taskMemory,
      longTermMemory = longTermMemory,
      includeToolsHint = true
    )
    val apiMessages = mutableListOf<ChatMessage>()
    apiMessages.add(ChatMessage.system(systemContent))
    historyMessages.forEach { msg ->
      when (msg.role) {
        AgentRole.User -> apiMessages.add(ChatMessage.user(msg.text))
        AgentRole.Assistant -> apiMessages.add(ChatMessage.assistant(content = msg.text))
      }
    }
    apiMessages.add(ChatMessage.user(userRequest))

    var lastResponse: ChatResponseWithToolCalls? = null
    var currentMessages = apiMessages.toList()
    var round = 0
    while (round < MAX_TOOL_ROUNDS) {
      val result = repository.sendOneCompletion(
        messages = currentMessages,
        tools = tools,
        maxTokens = null,
        stopPhrases = null
      )
      val response = result.getOrElse { return Result.failure(it) }
      lastResponse = response
      if (response.toolCalls.isNullOrEmpty()) {
        break
      }
      val outgoingCalls = response.toolCalls.map { tc ->
        OutgoingToolCall(
          id = tc.id ?: "",
          type = tc.type ?: "function",
          function = OutgoingToolCallFunction(
            name = tc.function?.name ?: "",
            arguments = tc.function?.arguments ?: "{}"
          )
        )
      }
      currentMessages = currentMessages + ChatMessage.assistant(
        content = response.content,
        toolCalls = outgoingCalls
      )
      response.toolCalls.forEach { tc ->
        val toolResult = runToolCall(tc, reminderScheduler, userTimezone)
        currentMessages = currentMessages + ChatMessage.tool(tc.id ?: "", toolResult)
      }
      round++
    }

    val finalContent = lastResponse?.content?.takeIf { it.isNotBlank() }
      ?: "Не удалось получить ответ."
    val newMessages = dialog.messages +
      AgentMessage(AgentRole.User, userRequest) +
      AgentMessage(AgentRole.Assistant, finalContent)
    val chatResponse = ChatResponse(
      content = finalContent,
      promptTokens = lastResponse?.promptTokens,
      completionTokens = lastResponse?.completionTokens,
      totalTokens = lastResponse?.totalTokens,
      finishReason = lastResponse?.finishReason
    )
    return Result.success(
      AgentResponse(
        reply = finalContent,
        dialog = dialog.copy(messages = newMessages),
        raw = chatResponse
      )
    )
  }

  private fun buildSystemMessageWithInvariants(
    userProfile: String,
    invariantsText: String,
    taskState: TaskState?,
    taskMemory: String?,
    longTermMemory: String,
    includeToolsHint: Boolean
  ): String = buildString {
    append(ChatRepository.DEFAULT_SYSTEM_MESSAGE)
    if (includeToolsHint) {
      append(" У тебя есть инструменты: mock_echo (эхо с меткой времени), get_current_weather (погода в городе), schedule_reminder (напомнить через N минут — параметры message и in_minutes), get_reminders (список запланированных напоминаний). Используй их по запросу пользователя.")
    }
    if (userProfile.isNotBlank()) {
      append("\n\nУчитывай предпочтения пользователя (стиль, формат, ограничения):\n")
      append(userProfile)
    }
    if (longTermMemory.isNotBlank()) {
      append("\n\nДолговременная память:\n")
      append(longTermMemory)
    }
    if (taskState != null) {
      append("\n\nСостояние задачи: этап — ")
      append(taskState.stage.displayName())
      append(", ожидаемое действие: ")
      append(taskState.stage.expectedActionText())
      append(".")
      append(" СТРОГОЕ ПРАВИЛО: переход между этапами только по /confirm.")
    }
    if (!taskMemory.isNullOrBlank()) {
      append("\n\nПамять текущей задачи:\n")
      append(taskMemory)
    }
    append("\n\nИнварианты (не нарушать):\n")
    append(if (invariantsText.isNotBlank()) invariantsText else "Ограничений нет.")
  }

  private suspend fun runToolCall(tc: ToolCall, reminderScheduler: ReminderScheduler?, userTimezone: String): String {
    val name = tc.function?.name ?: return "Ошибка: нет имени инструмента"
    val argsJson = tc.function?.arguments ?: "{}"
    val args = try {
      Gson().fromJson(argsJson, JsonObject::class.java) ?: JsonObject()
    } catch (_: Exception) {
      JsonObject()
    }
    fun getString(key: String): String =
      args.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString ?: ""
    fun getInt(key: String): Int =
      try {
        args.get(key)?.getAsInt() ?: 0
      } catch (_: Exception) {
        0
      }
    return try {
      when (name) {
        AgentToolConstants.ToolNames.MOCK_ECHO -> {
          McpCustomClient.callMockEcho(getString(AgentToolConstants.ParamNames.MESSAGE))
        }
        AgentToolConstants.ToolNames.GET_CURRENT_WEATHER -> {
          val city = getString(AgentToolConstants.ParamNames.CITY)
          McpWeatherClient.getWeather(city).rawText
        }
        AgentToolConstants.ToolNames.SCHEDULE_REMINDER -> {
          val message = getString(AgentToolConstants.ParamNames.MESSAGE)
          val inMinutes = getInt(AgentToolConstants.ParamNames.IN_MINUTES).coerceIn(0, 60 * 24 * 365)
          val serverResult = McpCustomClient.callTool(
            AgentToolConstants.McpToolNames.REGISTER_REMINDER,
            mapOf(
              AgentToolConstants.ParamNames.MESSAGE to message,
              AgentToolConstants.ParamNames.IN_MINUTES to inMinutes
            )
          )
          reminderScheduler?.scheduleReminder(inMinutes, message)
          serverResult
        }
        AgentToolConstants.ToolNames.GET_REMINDERS -> {
          val args = if (userTimezone.isNotBlank()) mapOf("timezone" to userTimezone) else emptyMap()
          McpCustomClient.callTool(AgentToolConstants.McpToolNames.GET_REMINDERS, args)
        }
        else -> "Неизвестный инструмент: $name"
      }
    } catch (e: Exception) {
      "Ошибка вызова $name: ${e.message ?: e.toString()}"
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

