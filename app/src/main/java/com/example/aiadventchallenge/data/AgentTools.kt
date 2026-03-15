package com.example.aiadventchallenge.data

import com.example.aiadventchallenge.data.openai.ChatTool
import com.example.aiadventchallenge.data.openai.ChatToolFunction
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Список инструментов для агента в формате OpenAI (function calling).
 * Имена и параметры — из [AgentToolConstants]. Передавать в API только при BuildConfig.MCP_CUSTOM_SERVER_URL.isNotBlank().
 */
object AgentTools {

  private fun buildMockEchoParameters(): JsonObject {
    val params = JsonObject()
    params.addProperty("type", "object")
    val properties = JsonObject()
    properties.add(AgentToolConstants.ParamNames.MESSAGE, JsonObject().apply { addProperty("type", "string") })
    params.add("properties", properties)
    params.add("required", JsonArray().apply { add(AgentToolConstants.ParamNames.MESSAGE) })
    return params
  }

  private fun buildGetCurrentWeatherParameters(): JsonObject {
    val params = JsonObject()
    params.addProperty("type", "object")
    val properties = JsonObject()
    properties.add(AgentToolConstants.ParamNames.CITY, JsonObject().apply { addProperty("type", "string") })
    properties.add(AgentToolConstants.ParamNames.LANG, JsonObject().apply { addProperty("type", "string") })
    params.add("properties", properties)
    params.add("required", JsonArray().apply { add(AgentToolConstants.ParamNames.CITY) })
    return params
  }

  private fun buildScheduleReminderParameters(): JsonObject {
    val params = JsonObject()
    params.addProperty("type", "object")
    val properties = JsonObject()
    properties.add(AgentToolConstants.ParamNames.MESSAGE, JsonObject().apply { addProperty("type", "string") })
    properties.add(AgentToolConstants.ParamNames.IN_MINUTES, JsonObject().apply { addProperty("type", "number") })
    params.add("properties", properties)
    params.add("required", JsonArray().apply {
      add(AgentToolConstants.ParamNames.MESSAGE)
      add(AgentToolConstants.ParamNames.IN_MINUTES)
    })
    return params
  }

  /** Список инструментов: mock_echo, get_current_weather, schedule_reminder, get_reminders (при включённом MCP). */
  val tools: List<ChatTool> = listOf(
    ChatTool(
      function = ChatToolFunction(
        name = AgentToolConstants.ToolNames.MOCK_ECHO,
        description = "Эхо сообщения с меткой времени (ISO-8601). Параметр: message (строка).",
        parameters = buildMockEchoParameters()
      )
    ),
    ChatTool(
      function = ChatToolFunction(
        name = AgentToolConstants.ToolNames.GET_CURRENT_WEATHER,
        description = "Текущая погода в городе. Параметры: city (название города), lang (код языка, например ru).",
        parameters = buildGetCurrentWeatherParameters()
      )
    ),
    ChatTool(
      function = ChatToolFunction(
        name = AgentToolConstants.ToolNames.SCHEDULE_REMINDER,
        description = "Запланировать напоминание через N минут. Параметры: message (текст напоминания), in_minutes (через сколько минут напомнить).",
        parameters = buildScheduleReminderParameters()
      )
    ),
    ChatTool(
      function = ChatToolFunction(
        name = AgentToolConstants.ToolNames.GET_REMINDERS,
        description = "Получить список запланированных напоминаний с сервера (что и когда должно было напомнить).",
        parameters = JsonObject().apply {
          addProperty("type", "object")
          add("properties", JsonObject())
        }
      )
    )
  )
}
