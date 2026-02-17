package com.example.aiadventchallenge.data

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.openai.ChatCompletionRequest
import com.example.aiadventchallenge.data.openai.ChatMessage
import com.example.aiadventchallenge.data.openai.OpenAiApi
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
  val finishReason: String? = null
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
  ): Result<ChatResponse> = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.OPENAI_API_KEY
    if (apiKey.isNullOrEmpty()) {
      return@withContext Result.failure(SecurityException("OPENAI_API_KEY не задан. Скопируйте secret.properties.example в secret.properties и подставьте свой ключ."))
    }
    try {
      val request = ChatCompletionRequest(
        messages = listOf(
          ChatMessage(role = "system", content = SYSTEM_MESSAGE),
          ChatMessage(role = "user", content = userMessage)
        ),
        maxCompletionTokens = maxTokens, // null = без ограничения, иначе указанное значение
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
}
