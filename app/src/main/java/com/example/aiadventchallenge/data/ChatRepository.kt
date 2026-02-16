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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "OpenAI"
private const val TIMEOUT_SECONDS = 60L

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
    Retrofit.Builder()
      .baseUrl("https://api.openai.com/")
      .client(client)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
      .create(OpenAiApi::class.java)
  }

  suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.OPENAI_API_KEY
    if (apiKey.isNullOrEmpty()) {
      return@withContext Result.failure(SecurityException("OPENAI_API_KEY не задан. Скопируйте secret.properties.example в secret.properties и подставьте свой ключ."))
    }
    try {
      val request = ChatCompletionRequest(
        messages = listOf(ChatMessage(role = "user", content = userMessage))
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
      val content = body?.choices?.firstOrNull()?.message?.content
      if (content != null) {
        Result.success(content)
      } else {
        Result.failure(IOException(body?.error?.message ?: "Пустой ответ от API"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
