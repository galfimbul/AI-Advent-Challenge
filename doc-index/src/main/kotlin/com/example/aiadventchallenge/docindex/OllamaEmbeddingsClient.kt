package com.example.aiadventchallenge.docindex

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON = "application/json; charset=utf-8".toMediaType()

private data class EmbeddingsRequest(
  val model: String,
  val prompt: String,
)

private data class EmbeddingsResponse(
  val embedding: List<Double>,
)

class OllamaEmbeddingsClient(
  baseUrl: String,
  private val model: String = ModelConstants.OLLAMA_MODEL,
  private val gson: Gson = Gson(),
) {
  private val base = baseUrl.trimEnd('/')
  private val client =
    OkHttpClient.Builder()
      .callTimeout(120, TimeUnit.SECONDS)
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .build()

  fun embed(text: String): FloatArray {
    val body =
      gson.toJson(EmbeddingsRequest(model = model, prompt = text))
        .toRequestBody(JSON)
    val request =
      Request.Builder()
        .url("$base/api/embeddings")
        .post(body)
        .build()
    client.newCall(request).execute().use { response ->
      val responseBody = response.body.string()
      if (!response.isSuccessful) {
        throw IllegalStateException(
          "Ollama embeddings failed: HTTP ${response.code} ${response.message}. Body: $responseBody. " +
            "Is Ollama running? Try: ollama pull $model",
        )
      }
      val parsed = gson.fromJson(responseBody, EmbeddingsResponse::class.java)
      val emb = parsed.embedding
      if (emb.isEmpty()) {
        throw IllegalStateException("Ollama returned empty embedding for model $model")
      }
      return FloatArray(emb.size) { i -> emb[i].toFloat() }
    }
  }
}

fun resolveOllamaBaseUrl(explicit: String?, envOllamaHost: String?): String {
  val trimmed = explicit?.trim().orEmpty()
  if (trimmed.isNotEmpty()) return trimmed.trimEnd('/')
  val host = envOllamaHost?.trim().orEmpty()
  if (host.isNotEmpty()) {
    val h = host.trimEnd('/')
    return if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
  }
  return ModelConstants.DEFAULT_OLLAMA_BASE
}
