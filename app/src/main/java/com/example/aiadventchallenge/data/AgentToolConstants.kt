package com.example.aiadventchallenge.data

/**
 * Единый источник имён инструментов и параметров для агента (OpenAI tools и MCP).
 * Использовать в AgentTools.kt и SimpleAgent.runToolCall вместо хардкода строк.
 */
object AgentToolConstants {

  /** Имена инструментов (передаются в OpenAI API и в runToolCall). */
  object ToolNames {
    const val MOCK_ECHO = "mock_echo"
    const val GET_CURRENT_WEATHER = "get_current_weather"
    const val SCHEDULE_REMINDER = "schedule_reminder"
    const val GET_REMINDERS = "get_reminders"
  }

  /** Имена инструментов на MCP-сервере (могут отличаться от имён для модели). */
  object McpToolNames {
    const val MOCK_ECHO = "mock_echo"
    const val REGISTER_REMINDER = "register_reminder"
    const val GET_REMINDERS = "get_reminders"
  }

  /** Имена параметров инструментов (ключи в JSON аргументах). */
  object ParamNames {
    const val MESSAGE = "message"
    const val CITY = "city"
    const val LANG = "lang"
    const val IN_MINUTES = "in_minutes"
  }
}
