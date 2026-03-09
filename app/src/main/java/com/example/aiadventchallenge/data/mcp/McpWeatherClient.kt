package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP-клиент для сервера погоды (Apify Weather MCP Server).
 *
 * Подключение по Streamable HTTP к https://jiri-spilka--weather-mcp-server.apify.actor/mcp.
 * Токен Apify передаётся в URL (?token=...) или через BuildConfig.APIFY_API_KEY.
 */
object McpWeatherClient {

  private const val LOG_TAG = "McpWeatherClient"
  private const val MCP_WEATHER_BASE_URL = "https://jiri-spilka--weather-mcp-server.apify.actor/mcp"

  private val mutex = Mutex()
  private var client: Client? = null
  private var httpClient: HttpClient? = null

  data class ToolInfo(
    val name: String,
    val description: String?
  )

  data class WeatherResult(
    val rawText: String
  )

  /**
   * Лениво инициализирует MCP-клиент и подключается к серверу, если это ещё не сделано.
   */
  private suspend fun getClient(): Client {
    return mutex.withLock {
      client?.let { return it }

      val ktorClient = HttpClient(OkHttp) {
        install(SSE)
        install(HttpTimeout) {
          connectTimeoutMillis = 30_000
          socketTimeoutMillis = 60_000
          requestTimeoutMillis = 90_000
        }
      }

      val mcpClient = Client(
        clientInfo = Implementation(
          name = "ai-advent-android-agent",
          version = "1.0.0"
        )
      )

      val token = BuildConfig.APIFY_API_KEY
      val url = if (token.isNotBlank()) "$MCP_WEATHER_BASE_URL?token=$token" else MCP_WEATHER_BASE_URL
      val transport = StreamableHttpClientTransport(
        client = ktorClient,
        url = url
      )

      try {
        mcpClient.connect(transport)
      } catch (e: Exception) {
        Log.e(LOG_TAG, "Failed to connect MCP client", e)
        ktorClient.close()
        throw e
      }

      httpClient = ktorClient
      client = mcpClient
      mcpClient
    }
  }

  suspend fun listTools(): List<ToolInfo> {
    val c = getClient()
    val toolsResult = c.listTools()
    return toolsResult.tools.map { tool ->
      ToolInfo(
        name = tool.name,
        description = tool.description
      )
    }
  }

  /**
   * Запрос текущей погоды по названию города.
   * Язык ответа задаётся параметром lang (ru = русский), если сервер поддерживает.
   */
  suspend fun getWeather(city: String): WeatherResult {
    val c = getClient()

    val args = buildMap<String, Any> {
      put("city", city)
      put("lang", "ru")
    }

    val result = c.callTool("get_current_weather", args)

    val text = result.content
      .mapNotNull { content ->
        (content as? TextContent)?.text
      }
      .joinToString(separator = "\n")
      .ifBlank { "Сервер погоды не вернул текстового ответа." }

    return WeatherResult(rawText = text)
  }

  /**
   * Освобождение ресурсов Ktor-клиента (на всякий случай, если понадобится).
   */
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
}

