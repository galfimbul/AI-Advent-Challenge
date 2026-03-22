package com.example.aiadventchallenge.data.rag

import android.content.Context
import android.util.Log
import com.example.aiadventchallenge.data.index.ChunkingStrategy
import com.example.aiadventchallenge.data.index.DocEmbeddingIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RagContextBuilder(
  private val context: Context,
  private val embeddingClient: OllamaEmbeddingClient,
  private val topK: Int = 5,
) {

  suspend fun buildContext(userQuery: String): Result<String> = withContext(Dispatchers.IO) {
    val query = userQuery.trim()
    if (query.isEmpty()) {
      Log.w(LOG_TAG, "buildContext: empty query")
      return@withContext Result.failure(IllegalArgumentException("Пустой запрос"))
    }
    var index: DocEmbeddingIndex? = null
    try {
      index = DocEmbeddingIndex.openFromAssets(context)
      val vector = embeddingClient.embed(query)
      val hits = index.search(vector, ChunkingStrategy.STRUCTURE, topK)
      val md = RagMarkdownFormatter.formatChunks(hits)
      if (md.isBlank()) {
        Log.e(LOG_TAG, "buildContext: index returned no markdown fragments (topK=$topK)")
        return@withContext Result.failure(IllegalStateException("Индекс не вернул фрагментов"))
      }
      Result.success(md)
    } catch (e: Exception) {
      Log.e(LOG_TAG, "buildContext failed (index/open/embed/search)", e)
      Result.failure(e)
    } finally {
      index?.close()
    }
  }

  private companion object {
    private const val LOG_TAG = "RagContextBuilder"
  }
}
