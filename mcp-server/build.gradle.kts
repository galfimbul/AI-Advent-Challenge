plugins {
  kotlin("jvm")
  application
}

group = "com.example.aiadventchallenge"
version = "1.0.0"

application {
  mainClass.set("com.example.mcpserver.MainKt")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.mcp.kotlin.sdk.server)
  // MCP SDK 0.9.0 собран с Ktor 3.2.3; 3.4.x даёт NoSuchMethodError в Route.sse
  implementation(libs.ktor.server.core.v32)
  implementation(libs.ktor.server.netty.v32)
  implementation(libs.ktor.server.sse.v32)
  implementation(libs.ktor.server.call.logging.v32)
  implementation(libs.ktor.server.content.negotiation.v32)
  implementation(libs.ktor.serialization.kotlinx.json.v32)
}
