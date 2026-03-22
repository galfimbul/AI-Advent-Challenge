package com.example.aiadventchallenge.data.rag

/**
 * Normalizes Ollama base URL from [secret.properties] / BuildConfig (same rules as doc-index
 * [resolveOllamaBaseUrl] for an explicit host string).
 */
object OllamaHostResolver {

  fun normalize(raw: String): String {
    val t = raw.trim().trimEnd('/')
    if (t.isEmpty()) return ""
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
  }
}
