package com.example.aiadventchallenge.data.openai

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
  val model: String = "gpt-5",
  val messages: List<ChatMessage>
)

data class ChatMessage(
  val role: String,
  val content: String
)

data class ChatCompletionResponse(
  val id: String? = null,
  val choices: List<Choice>? = null,
  val error: ApiError? = null
)

data class Choice(
  val index: Int? = null,
  val message: MessageContent? = null,
  @SerializedName("finish_reason") val finishReason: String? = null
)

data class MessageContent(
  val role: String? = null,
  val content: String? = null
)

data class ApiError(
  val message: String? = null,
  val type: String? = null,
  val code: String? = null
)
