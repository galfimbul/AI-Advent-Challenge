package com.example.aiadventchallenge.data.rag

import android.content.Context
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
      return@withContext Result.failure(IllegalArgumentException("Пустой запрос"))
    }
    var index: DocEmbeddingIndex? = null
    try {
      index = DocEmbeddingIndex.openFromAssets(context)
      val vector = embeddingClient.embed(query)
      val hits = index.search(vector, ChunkingStrategy.STRUCTURE, topK)
      val md = RagMarkdownFormatter.formatChunks(hits)
      if (md.isBlank()) {
        return@withContext Result.failure(IllegalStateException("Индекс не вернул фрагментов"))
      }
      Result.success(md)
    } catch (e: Exception) {
      Result.failure(e)
    } finally {
      index?.close()
    }
  }
}
