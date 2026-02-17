package com.example.aiadventchallenge.data.openai

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class ChatCompletionRequest(
  /** gpt-4.1-2025-04-14 / gpt-4o — без reasoning. gpt-5 / o1 — часть токенов уходит в рассуждения. */
  val model: String = "gpt-4.1",
  val messages: List<ChatMessage>,
  /** Максимальное количество токенов в ответе. Ограничивает длину ответа. */
  @SerializedName("max_completion_tokens") val maxCompletionTokens: Int? = null,
  /** Список последовательностей, при встрече которых генерация останавливается. */
  val stop: List<String>? = null
)

data class ChatMessage(
  val role: String,
  val content: String
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

data class MessageContent(
  val role: String? = null,
  val content: String? = null
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

/** Десериализатор для MessageContent: content может быть строкой или массивом с полем "text". */
class MessageContentDeserializer : JsonDeserializer<MessageContent> {
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MessageContent {
    val obj = json.asJsonObject
    val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString
    val contentJson = obj.get("content")
    val content = MessageContent.extractContent(contentJson)
    return MessageContent(role = role, content = content)
  }
}

data class ApiError(
  val message: String? = null,
  val type: String? = null,
  val code: String? = null
)
