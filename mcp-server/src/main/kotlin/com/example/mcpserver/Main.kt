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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    }
  }
}
