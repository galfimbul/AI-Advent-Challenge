package com.example.mcpserver

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.request.uri
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val reminderStorage = ReminderStorage()

private fun formatScheduledAt(scheduledAtIso: String, timezoneId: String?): String {
  return try {
    val instant = Instant.parse(scheduledAtIso)
    if (!timezoneId.isNullOrBlank()) {
      val zone = ZoneId.of(timezoneId)
      val zdt = ZonedDateTime.ofInstant(instant, zone)
      zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzzz", Locale.US))
    } else {
      scheduledAtIso
    }
  } catch (_: Exception) {
    scheduledAtIso
  }
}

private fun log(msg: String) {
  val ts = Instant.now().toString()
  println("[$ts] [MCP] $msg")
}

fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  log("Starting server on port $port")
  embeddedServer(Netty, port = port) {
    module()
  }.start(wait = true)
}

fun Application.module() {
  log("Application module loaded, registering MCP at /mcp")
  install(ContentNegotiation) {
    json(McpJson)
  }
  intercept(ApplicationCallPipeline.Call) {
    if (call.request.uri.startsWith("/mcp")) {
      val accept = call.request.header(HttpHeaders.Accept)
      log("INCOMING Accept: [${accept?.replace(Char(0), '?') ?: "null"}]")
    }
    proceed()
  }
  mcpStreamableHttp(
    path = "/mcp",
    enableDnsRebindingProtection = false,
    allowedHosts = null,
    allowedOrigins = null,
    eventStore = null
  ) {
    Server(
      serverInfo = Implementation("aiadvent-mock-mcp", "1.0.0"),
      options = ServerOptions(
        ServerCapabilities(
          tools = ServerCapabilities.Tools(listChanged = true)
        )
      )
    ) {
      addTool(
        name = "mock_echo",
        description = "Echo back the given message with an ISO-8601 timestamp. Mock tool for Day 17.",
        inputSchema = ToolSchema(
          properties = buildJsonObject {
            put("message", buildJsonObject { put("type", "string") })
          }
        )
      ) { request ->
        val raw = request.params.arguments?.get("message")?.toString()
        val message = raw?.trim('"') ?: ""
        val timestamp = Instant.now().toString()
        val result = "Echo: $message | Время: $timestamp"
        CallToolResult(content = listOf(TextContent(result)))
      }
      addTool(
        name = "register_reminder",
        description = "Register a reminder: store message and scheduled time (now + in_minutes). Day 18.",
        inputSchema = ToolSchema(
          properties = buildJsonObject {
            put("message", buildJsonObject { put("type", "string") })
            put("in_minutes", buildJsonObject { put("type", "number") })
          }
        )
      ) { request ->
        val args = request.params.arguments
        val rawMessage = args?.get("message")?.toString()
        val message = rawMessage?.trim('"') ?: ""
        val inMinutes = (args?.get("in_minutes")?.toString()?.toDoubleOrNull() ?: 0.0).toInt().coerceIn(0, 60 * 24 * 365)
        val scheduledAt = Instant.now().plusSeconds(inMinutes * 60L)
        val id = reminderStorage.insert(message, scheduledAt)
        val result = "Напоминание запланировано (id=$id) на $scheduledAt: $message"
        CallToolResult(content = listOf(TextContent(result)))
      }
      addTool(
        name = "get_reminders",
        description = "Return list of all registered reminders (id, scheduled_at, message). Optional: timezone (IANA, e.g. Europe/Moscow) to show times in user's timezone.",
        inputSchema = ToolSchema(
          properties = buildJsonObject {
            put("timezone", buildJsonObject { put("type", "string") })
          }
        )
      ) { request ->
        val args = request.params.arguments
        val timezoneId = args?.get("timezone")?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        val list = reminderStorage.getAll()
        val result = if (list.isEmpty()) {
          "Нет запланированных напоминаний."
        } else {
          list.joinToString("\n") { r ->
            val atFormatted = formatScheduledAt(r.scheduledAt, timezoneId)
            "${r.id}. [$atFormatted] ${r.message}"
          }
        }
        CallToolResult(content = listOf(TextContent(result)))
      }
    }
  }
}
