package com.example.aiadventchallenge.data.openai

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class ChatCompletionRequest(
  /** gpt-4.1-2025-04-14 / gpt-4o — без reasoning. gpt-5 / o1 — часть токенов уходит в рассуждения. */
  val model: String = "gpt-4.1",
  val messages: List<ChatMessage>,
  /** Максимальное количество токенов в ответе. Ограничивает длину ответа. */
  @SerializedName("max_completion_tokens") val maxCompletionTokens: Int? = null,
  /** Список последовательностей, при встрече которых генерация останавливается. */
  val stop: List<String>? = null,
  /** Случайность выбора токенов (OpenAI: 0–2). null — дефолт API. */
  val temperature: Float? = null,
  /** Инструменты (function calling). При наличии API может вернуть tool_calls в message. */
  val tools: List<ChatTool>? = null
)

/** Описание инструмента в формате OpenAI (type "function"). */
data class ChatTool(
  val type: String = "function",
  val function: ChatToolFunction
)

data class ChatToolFunction(
  val name: String,
  val description: String? = null,
  /** JSON Schema: type object, properties, required. */
  val parameters: JsonObject? = null
)

/**
 * Сообщение в диалоге. Поддерживает роли user/assistant/system и tool.
 * - user/system: content обязателен.
 * - assistant: content и/или tool_calls (после вызова инструментов).
 * - tool: tool_call_id и content (результат вызова).
 */
data class ChatMessage(
  val role: String,
  val content: String? = null,
  /** Только для role=assistant: вызовы инструментов. */
  @SerializedName("tool_calls") val toolCalls: List<OutgoingToolCall>? = null,
  /** Только для role=tool: id вызова из ответа модели. */
  @SerializedName("tool_call_id") val toolCallId: String? = null
) {
  companion object {
    fun user(content: String) = ChatMessage(role = "user", content = content)
    fun system(content: String) = ChatMessage(role = "system", content = content)
    fun assistant(content: String? = null, toolCalls: List<OutgoingToolCall>? = null) =
      ChatMessage(role = "assistant", content = content, toolCalls = toolCalls)
    fun tool(toolCallId: String, content: String) =
      ChatMessage(role = "tool", content = content, toolCallId = toolCallId)
  }
}

/** Формрование сообщения assistant с tool_calls для отправки в API. */
data class OutgoingToolCall(
  val id: String,
  val type: String = "function",
  val function: OutgoingToolCallFunction
)

data class OutgoingToolCallFunction(
  val name: String,
  val arguments: String
)

data class ChatCompletionResponse(
  val id: String? = null,
  val choices: List<Choice>? = null,
  val error: ApiError? = null,
  val usage: Usage? = null
)

data class Usage(
  @SerializedName("prompt_tokens") val promptTokens: Int? = null,
  @SerializedName("completion_tokens") val completionTokens: Int? = null,
  @SerializedName("total_tokens") val totalTokens: Int? = null,
  @SerializedName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null
)

data class CompletionTokensDetails(
  @SerializedName("reasoning_tokens") val reasoningTokens: Int? = null,
  @SerializedName("audio_tokens") val audioTokens: Int? = null
)

data class Choice(
  val index: Int? = null,
  val message: MessageContent? = null,
  @SerializedName("finish_reason") val finishReason: String? = null
)

/** Вызов инструмента в ответе API (choices[].message.tool_calls[]). */
data class ToolCall(
  val id: String? = null,
  val type: String? = null,
  val function: ToolCallFunction? = null
)

data class ToolCallFunction(
  val name: String? = null,
  val arguments: String? = null
)

data class MessageContent(
  val role: String? = null,
  val content: String? = null,
  /** Вызовы инструментов в ответе модели. */
  @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null
) {
  companion object {
    /** Извлекает текст из content: строка или массив блоков с полем "text". */
    fun extractContent(json: JsonElement?): String? = when {
      json == null || !json.isJsonPrimitive && !json.isJsonArray -> null
      json.isJsonPrimitive && json.asJsonPrimitive.isString -> json.asString
      json.isJsonArray -> {
        val arr = json.asJsonArray
        arr.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
          ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
      }
      else -> null
    }
  }
}

/** Десериализатор для MessageContent: content (строка/массив с text), tool_calls. */
class MessageContentDeserializer : JsonDeserializer<MessageContent> {
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MessageContent {
    val obj = json.asJsonObject
    val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString
    val contentJson = obj.get("content")
    val content = MessageContent.extractContent(contentJson)
    val toolCallsJson = obj.get("tool_calls")
    val toolCalls = toolCallsJson?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
      if (!el.isJsonObject) return@mapNotNull null
      val t = el.asJsonObject
      val func = t.get("function")?.takeIf { it.isJsonObject }?.asJsonObject
      ToolCall(
        id = t.get("id")?.takeIf { it.isJsonPrimitive }?.asString,
        type = t.get("type")?.takeIf { it.isJsonPrimitive }?.asString,
        function = func?.let { f ->
          ToolCallFunction(
            name = f.get("name")?.takeIf { it.isJsonPrimitive }?.asString,
            arguments = f.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString
          )
        }
      )
    }
    return MessageContent(role = role, content = content, toolCalls = toolCalls)
  }
}

data class ApiError(
  val message: String? = null,
  val type: String? = null,
  val code: String? = null
)
