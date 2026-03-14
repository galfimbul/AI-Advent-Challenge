package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import okhttp3.Interceptor
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP-клиент для своего MCP-сервера (День 17).
 * Подключение по Streamable HTTP; инструмент mock_echo.
 */
object McpCustomClient {

  private const val LOG_TAG = "McpCustomClient"

  private val mutex = Mutex()
  private var client: Client? = null
  private var httpClient: HttpClient? = null

  private suspend fun getClient(): Client {
    return mutex.withLock {
      client?.let { return it }

      val baseUrl = BuildConfig.MCP_CUSTOM_SERVER_URL.trim().trimEnd('/')
      if (baseUrl.isBlank()) {
        throw IllegalStateException("MCP_CUSTOM_SERVER_URL не задан в secret.properties")
      }
      val url = if (baseUrl.endsWith("/mcp")) baseUrl else "$baseUrl/mcp"

      val acceptHeader = "application/json, text/event-stream"
      val ktorClient = HttpClient(OkHttp) {
        install(SSE)
        install(HttpTimeout) {
          connectTimeoutMillis = 30_000
          socketTimeoutMillis = 60_000
          requestTimeoutMillis = 90_000
        }
        engine {
          addInterceptor(Interceptor { chain ->
            chain.proceed(
              chain.request().newBuilder()
                .addHeader("Accept", acceptHeader)
                .build()
            )
          })
        }
      }

      val mcpClient = Client(
        clientInfo = Implementation(
          name = "ai-advent-android-agent",
          version = "1.0.0"
        )
      )

      val transport = StreamableHttpClientTransport(
        client = ktorClient,
        url = url
      )

      try {
        mcpClient.connect(transport)
      } catch (e: Exception) {
        logMcpError(LOG_TAG, "connect", e)
        ktorClient.close()
        throw e
      }

      httpClient = ktorClient
      client = mcpClient
      mcpClient
    }
  }

  /**
   * Вызывает инструмент mock_echo на кастомном MCP-сервере (стриминг).
   */
  suspend fun callMockEcho(message: String): String {
    val c = getClient()
    val result = try {
      c.callTool("mock_echo", mapOf("message" to message))
    } catch (e: Exception) {
      logMcpError(LOG_TAG, "callTool mock_echo", e)
      throw e
    }
    val text = result.content
      .mapNotNull { content -> (content as? TextContent)?.text }
      .joinToString(separator = "\n")
      .ifBlank { "Сервер не вернул текстового ответа." }
    return text
  }

  suspend fun close() {
    mutex.withLock {
      try {
        httpClient?.close()
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to close HttpClient", e)
      } finally {
        httpClient = null
        client = null
      }
    }
  }

  /** Логирует MCP/Streamable HTTP ошибки с причиной (для отладки VPS/nginx). */
  private fun logMcpError(tag: String, context: String, e: Exception) {
    val msg = e.message ?: ""
    val cause = e.cause
    val causeMsg = cause?.message ?: ""
    Log.e(tag, "Failed to $context: $msg", e)
    if (cause != null && causeMsg.isNotBlank()) {
      Log.e(tag, "Caused by: $causeMsg")
    }
    if (e is StreamableHttpError) {
      Log.e(tag, "StreamableHttpError (часто: неверный ответ через nginx/прокси или таймаут)")
    }
  }
}
