package com.example.aiadventchallenge.data

import com.example.aiadventchallenge.data.openai.ChatTool
import com.example.aiadventchallenge.data.openai.ChatToolFunction
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Статический список инструментов для агента в формате OpenAI (function calling).
 * mock_echo — кастомный MCP; get_current_weather — погода.
 * Передавать в API только при BuildConfig.MCP_CUSTOM_SERVER_URL.isNotBlank().
 */
object AgentTools {

  private val mockEchoParameters: JsonObject
    get() {
      val params = JsonObject()
      params.addProperty("type", "object")
      val properties = JsonObject()
      properties.add("message", JsonObject().apply { addProperty("type", "string") })
      params.add("properties", properties)
      params.add("required", JsonArray().apply { add("message") })
      return params
    }

  private val getCurrentWeatherParameters: JsonObject
    get() {
      val params = JsonObject()
      params.addProperty("type", "object")
      val properties = JsonObject()
      properties.add("city", JsonObject().apply { addProperty("type", "string") })
      properties.add("lang", JsonObject().apply { addProperty("type", "string") })
      params.add("properties", properties)
      params.add("required", JsonArray().apply { add("city") })
      return params
    }

  /** Список инструментов: mock_echo и get_current_weather (для передачи в API при включённом MCP). */
  val tools: List<ChatTool> = listOf(
    ChatTool(
      function = ChatToolFunction(
        name = "mock_echo",
        description = "Эхо сообщения с меткой времени (ISO-8601). Параметр: message (строка).",
        parameters = mockEchoParameters
      )
    ),
    ChatTool(
      function = ChatToolFunction(
        name = "get_current_weather",
        description = "Текущая погода в городе. Параметры: city (название города), lang (код языка, например ru).",
        parameters = getCurrentWeatherParameters
      )
    )
  )
}
