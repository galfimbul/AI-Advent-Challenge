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
      Log.d(LOG_TAG, "buildContext: queryChars=${query.length} strategy=STRUCTURE topK=$topK")
      val vector = embeddingClient.embed(query)
      Log.d(LOG_TAG, "buildContext: queryEmbeddingDim=${vector.size}")
      val hits = index.search(vector, ChunkingStrategy.STRUCTURE, topK)
      hits.forEachIndexed { i, chunk ->
        val textPreview = chunk.text.replace('\n', ' ').take(LOG_CHUNK_TEXT_PREVIEW).let { t ->
          if (chunk.text.length > LOG_CHUNK_TEXT_PREVIEW) "$t…" else t
        }
        Log.d(
          LOG_TAG,
          "hit[$i] score=${"%.4f".format(chunk.score)} file=${chunk.titleFile} section=${chunk.section ?: "—"} id=${chunk.id} textPreview=$textPreview",
        )
      }
      if (hits.isEmpty()) {
        Log.w(LOG_TAG, "buildContext: search returned zero rows (check index / embedding_dim)")
      }
      val md = RagMarkdownFormatter.formatChunks(hits)
      if (md.isBlank()) {
        Log.e(LOG_TAG, "buildContext: index returned no markdown fragments (topK=$topK)")
        return@withContext Result.failure(IllegalStateException("Индекс не вернул фрагментов"))
      }
      Log.d(
        LOG_TAG,
        "buildContext: ragMarkdownChars=${md.length} head=${md.take(LOG_MARKDOWN_HEAD).replace('\n', '|')}",
      )
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
    private const val LOG_CHUNK_TEXT_PREVIEW = 200
    private const val LOG_MARKDOWN_HEAD = 600
  }
}
