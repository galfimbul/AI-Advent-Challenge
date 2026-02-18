package com.example.aiadventchallenge.data

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.openai.ChatCompletionRequest
import com.example.aiadventchallenge.data.openai.ChatMessage
import com.example.aiadventchallenge.data.openai.OpenAiApi
import com.example.aiadventchallenge.domain.ReasoningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.aiadventchallenge.data.openai.MessageContentDeserializer
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Результат запроса: текст ответа + использование токенов и причина завершения. */
data class ChatResponse(
  val content: String,
  val promptTokens: Int? = null,
  val completionTokens: Int? = null,
  val totalTokens: Int? = null,
  val finishReason: String? = null,
  /** Для способа «Свой промпт» — текст промпта, сгенерированного моделью в первом запросе. */
  val generatedPrompt: String? = null
)

private const val LOG_TAG = "OpenAI"
private const val TIMEOUT_SECONDS = 60L

// Ограничение длины ответа в токенах (примерно 200-300 слов)
const val MAX_TOKENS = 256

const val STOP_SEQUENCE = "Закончил ответ"

// System message задает формат и стиль ответа модели
private const val SYSTEM_MESSAGE =
  "Отвечай кратко и по делу. Завершай ответ естественно, без дополнительных пояснений."

class ChatRepository {

  private val api: OpenAiApi by lazy {
    val logging = HttpLoggingInterceptor { message -> Log.d(LOG_TAG, message) }
    logging.level = HttpLoggingInterceptor.Level.BODY
    val client = OkHttpClient.Builder()
      .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .addInterceptor(logging)
      .build()
    val gson = GsonBuilder()
      .registerTypeAdapter(
        com.example.aiadventchallenge.data.openai.MessageContent::class.java,
        MessageContentDeserializer()
      )
      .create()
    Retrofit.Builder()
      .baseUrl("https://api.openai.com/")
      .client(client)
      .addConverterFactory(GsonConverterFactory.create(gson))
      .build()
      .create(OpenAiApi::class.java)
  }

  suspend fun sendMessage(
    userMessage: String,
    maxTokens: Int? = null,
    stopPhrases: List<String>?
  ): Result<ChatResponse> = sendWithMessages(
    messages = listOf(
      ChatMessage(role = "system", content = SYSTEM_MESSAGE),
      ChatMessage(role = "user", content = userMessage)
    ),
    maxTokens = maxTokens,
    stopPhrases = stopPhrases
  )

  private suspend fun sendWithMessages(
    messages: List<ChatMessage>,
    maxTokens: Int? = null,
    stopPhrases: List<String>? = null
  ): Result<ChatResponse> = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.OPENAI_API_KEY
    if (apiKey.isNullOrEmpty()) {
      return@withContext Result.failure(SecurityException("OPENAI_API_KEY не задан. Скопируйте secret.properties.example в secret.properties и подставьте свой ключ."))
    }
    try {
      val request = ChatCompletionRequest(
        messages = messages,
        maxCompletionTokens = maxTokens,
        stop = stopPhrases
      )
      val response = api.createChatCompletion(
        authorization = "Bearer $apiKey",
        request = request
      )
      if (!response.isSuccessful) {
        val errorBody = response.errorBody()?.string() ?: "Unknown error"
        return@withContext Result.failure(IOException("HTTP ${response.code()}: $errorBody"))
      }
      val body = response.body()
      val choice = body?.choices?.firstOrNull()
      val content = choice?.message?.content?.takeIf { it.isNotBlank() }
      val finishReason = choice?.finishReason
      val usage = body?.usage
      val reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0
      val completionTokens = usage?.completionTokens ?: 0

      val usageInfo = ChatResponse(
        content = "",
        promptTokens = usage?.promptTokens,
        completionTokens = usage?.completionTokens,
        totalTokens = usage?.totalTokens,
        finishReason = finishReason
      )
      when {
        content != null -> Result.success(usageInfo.copy(content = content))
        body?.error != null -> Result.failure(IOException(body.error.message ?: "Ошибка API"))
        finishReason == "length" && completionTokens > 0 -> {
          val msg = buildString {
            append("Модель использовала все $completionTokens токенов на рассуждения (reasoning), ")
            append("видимый ответ пуст. Увеличьте лимит токенов (например 500–1000), чтобы получить текст ответа.")
            if (reasoningTokens > 0) append(" [reasoning_tokens: $reasoningTokens]")
          }
          Result.success(usageInfo.copy(content = msg))
        }
        else -> {
          Log.w(LOG_TAG, "Пустой content. body=$body choices=${body?.choices}")
          Result.failure(IOException("Пустой ответ от API. Проверьте Logcat (тег OpenAI) для структуры ответа."))
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private val discussionMaxTokens: Int? = 768  // +50% к 512 для пошаговых и длинных ответов

  suspend fun solveWithReasoningMode(task: String, mode: ReasoningMode): Result<ChatResponse> =
    withContext(Dispatchers.IO) {
      when (mode) {
        ReasoningMode.Direct -> sendWithMessages(
          messages = listOf(ChatMessage(role = "user", content = task)),
          maxTokens = discussionMaxTokens,
          stopPhrases = null
        )
        ReasoningMode.StepByStep -> sendWithMessages(
          messages = listOf(
            ChatMessage(role = "system", content = "Решай задачу пошагово."),
            ChatMessage(role = "user", content = task)
          ),
          maxTokens = discussionMaxTokens,
          stopPhrases = null
        )
        ReasoningMode.SelfPrompt -> {
          val promptRequest = "Составь краткий промпт (инструкцию) для решения следующей задачи. Задача: $task. Выведи только текст промпта."
          val first = sendWithMessages(
            messages = listOf(ChatMessage(role = "user", content = promptRequest)),
            maxTokens = 300,
            stopPhrases = null
          ).getOrElse { return@withContext Result.failure(it) }
          val prompt = first.content.trim()
          if (prompt.isBlank()) return@withContext Result.failure(IOException("Пустой промпт от модели"))
          sendWithMessages(
            messages = listOf(
              ChatMessage(role = "system", content = prompt),
              ChatMessage(role = "user", content = task)
            ),
            maxTokens = discussionMaxTokens,
            stopPhrases = null
          ).map { it.copy(generatedPrompt = prompt) }
        }
        ReasoningMode.Experts -> sendWithMessages(
          messages = listOf(
            ChatMessage(
              role = "system",
              content = "Ты — группа экспертов: аналитик, инженер и критик. Реши задачу так, чтобы каждый эксперт дал свой ответ: сначала аналитик, затем инженер, затем критик. Подписывай ответы (Аналитик:, Инженер:, Критик:)."
            ),
            ChatMessage(role = "user", content = "Реши задачу, получи ответ от каждого эксперта: $task")
          ),
          maxTokens = discussionMaxTokens,
          stopPhrases = null
        )
      }
    }

  suspend fun compareResponses(
    task: String,
    direct: String,
    stepByStep: String,
    selfPrompt: String,
    experts: String
  ): Result<ChatResponse> = withContext(Dispatchers.IO) {
    fun orPlaceholder(s: String) = s.ifBlank { "(нет ответа)" }
    val userContent = buildString {
      append("Задача:\n$task\n\n")
      append("Ответы четырьмя способами:\n\n")
      append("1. Прямой ответ:\n${orPlaceholder(direct)}\n\n")
      append("2. Пошагово:\n${orPlaceholder(stepByStep)}\n\n")
      append("3. Свой промпт:\n${orPlaceholder(selfPrompt)}\n\n")
      append("4. Эксперты:\n${orPlaceholder(experts)}\n\n")
      append("Сравни эти ответы: отличаются ли они, какой способ дал наиболее точный и обоснованный результат? Ответь кратко.")
    }
    sendWithMessages(
      messages = listOf(ChatMessage(role = "user", content = userContent)),
      maxTokens = 768,
      stopPhrases = null
    )
  }
}
