package com.example.aiadventchallenge.data.rag

import android.util.Log
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

class OllamaEmbeddingClient(
  baseUrl: String,
  private val model: String = RagEmbeddingConstants.OLLAMA_MODEL,
  private val gson: Gson = Gson(),
) {
  private val base = baseUrl.trim().trimEnd('/')

  init {
    require(base.isNotEmpty()) { "Ollama base URL must not be empty" }
  }

  private val client =
    OkHttpClient.Builder()
      .callTimeout(120, TimeUnit.SECONDS)
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .build()

  fun embed(text: String): FloatArray {
    val url = "$base/api/embeddings"
    val preview = text.take(LOG_PROMPT_PREVIEW_CHARS).let { p ->
      if (text.length > LOG_PROMPT_PREVIEW_CHARS) "$p…" else p
    }
    Log.d(
      LOG_TAG,
      "POST embeddings url=$url model=$model promptChars=${text.length} promptPreview=${preview.replace('\n', ' ')}",
    )
    val body =
      gson.toJson(EmbeddingsRequest(model = model, prompt = text))
        .toRequestBody(JSON)
    val request =
      Request.Builder()
        .url(url)
        .post(body)
        .build()
    client.newCall(request).execute().use { response ->
      val responseBody = response.body.string()
      if (!response.isSuccessful) {
        Log.e(
          LOG_TAG,
          "embeddings HTTP ${response.code} ${response.message} bodyChars=${responseBody.length} body=${responseBody.take(500)}",
        )
        throw IllegalStateException(
          "Ollama embeddings failed: HTTP ${response.code} ${response.message}. Body: $responseBody. " +
            "Is Ollama running? Try: ollama pull $model",
        )
      }
      val parsed = gson.fromJson(responseBody, EmbeddingsResponse::class.java)
      val emb = parsed.embedding
      if (emb.isEmpty()) {
        Log.e(LOG_TAG, "embeddings success but empty vector, raw=${responseBody.take(300)}")
        throw IllegalStateException("Ollama returned empty embedding for model $model")
      }
      Log.d(LOG_TAG, "embeddings OK dim=${emb.size} responseChars=${responseBody.length}")
      return FloatArray(emb.size) { i -> emb[i].toFloat() }
    }
  }

  private companion object {
    private const val LOG_TAG = "OllamaRag"
    private const val LOG_PROMPT_PREVIEW_CHARS = 400
  }
}
