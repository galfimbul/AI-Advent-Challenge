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
  /** Top hits per strategy before merge (wider pool for re-ranking). */
  private val topKPerStrategy: Int = 15,
  /** Max unique chunks after merge, before lexical re-rank. */
  private val maxMergedCandidates: Int = 45,
  /** Chunks passed to the LLM after re-rank. */
  private val finalChunkCount: Int = 10,
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
      Log.d(
        LOG_TAG,
        "buildContext: queryChars=${query.length} topKPerStrategy=$topKPerStrategy mergeCap=$maxMergedCandidates final=$finalChunkCount (STRUCTURE+FIXED_WINDOW + rerank)",
      )
      val embedInput = RagRetrievalEnhancement.embedPromptForSearch(query)
      if (embedInput != query) {
        Log.d(LOG_TAG, "buildContext: embedding input uses lexical bridge (Ollama only, not sent to ChatGPT)")
      }
      val vector = embeddingClient.embed(embedInput)
      Log.d(LOG_TAG, "buildContext: queryEmbeddingDim=${vector.size}")
      val structHits = index.search(vector, ChunkingStrategy.STRUCTURE, topKPerStrategy)
      val fixedHits = index.search(vector, ChunkingStrategy.FIXED_WINDOW, topKPerStrategy)
      Log.d(LOG_TAG, "buildContext: rawHits structure=${structHits.size} fixedWindow=${fixedHits.size}")
      val merged =
        RagChunkMerge.mergeByBestScorePerId(structHits + fixedHits, maxMergedCandidates)
      Log.d(LOG_TAG, "buildContext: mergedUnique=${merged.size}")
      val hits = RagRetrievalEnhancement.rerankChunks(query, merged, finalChunkCount)
      hits.forEachIndexed { i, chunk ->
        val textPreview = chunk.text.replace('\n', ' ').take(LOG_CHUNK_TEXT_PREVIEW).let { t ->
          if (chunk.text.length > LOG_CHUNK_TEXT_PREVIEW) "$t…" else t
        }
        Log.d(
          LOG_TAG,
          "hit[$i] ${RagRetrievalEnhancement.formatScoreForLog(query, chunk)} file=${chunk.titleFile} section=${chunk.section ?: "—"} id=${chunk.id} textPreview=$textPreview",
        )
      }
      if (hits.isEmpty()) {
        Log.w(LOG_TAG, "buildContext: search returned zero rows (check index / embedding_dim)")
      }
      val md = RagMarkdownFormatter.formatChunks(hits)
      if (md.isBlank()) {
        Log.e(LOG_TAG, "buildContext: index returned no markdown fragments (merged=${hits.size})")
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
