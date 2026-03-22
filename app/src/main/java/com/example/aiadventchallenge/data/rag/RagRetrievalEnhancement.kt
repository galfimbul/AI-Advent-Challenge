package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk

/**
 * Improves recall when pure embedding similarity confuses intent (e.g. «модель» → ChatGPT, not nomic-embed).
 */
internal object RagRetrievalEnhancement {

  /**
   * Extra text appended only for the Ollama embedding call (not shown to the user or ChatGPT).
   * Pulls vectors toward chunks that mention tooling named in the repo docs.
   */
  fun embedPromptForSearch(userQuery: String): String {
    if (!queryHintsIndexingOrEmbeddings(userQuery)) return userQuery
    return userQuery.trim() + EMBED_BRIDGE_SUFFIX
  }

  /**
   * Re-ranks merged candidates: cosine score + small lexical bonus when the question is about indexing/RAG/embeddings.
   */
  fun rerankChunks(query: String, chunks: List<IndexedChunk>, finalCount: Int): List<IndexedChunk> {
    if (chunks.isEmpty() || finalCount <= 0) return chunks.take(finalCount)
    val useLexical = queryHintsIndexingOrEmbeddings(query)
    val ranked =
      chunks
        .map { c ->
          val bonus = if (useLexical) lexicalBonus(c) else 0f
          Triple(c, c.score + bonus, bonus)
        }
        .sortedWith(
          compareByDescending<Triple<IndexedChunk, Float, Float>> { it.second }
            .thenByDescending { it.third },
        )
        .take(finalCount)
    return ranked.map { it.first }
  }

  fun formatScoreForLog(query: String, chunk: IndexedChunk): String {
    val b = if (queryHintsIndexingOrEmbeddings(query)) lexicalBonus(chunk) else 0f
    return "cos=${"%.4f".format(chunk.score)} eff=${"%.4f".format(chunk.score + b)} lex=+${"%.4f".format(b)}"
  }

  private fun queryHintsIndexingOrEmbeddings(q: String): Boolean {
    val lower = q.lowercase()
    if (QUERY_HINT_SUBSTRINGS.any { lower.contains(it) }) return true
    return Regex("\\brag\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
  }

  private fun lexicalBonus(chunk: IndexedChunk): Float {
    val hay =
      "${chunk.titleFile}\n${chunk.section ?: ""}\n${chunk.text}"
        .lowercase()
    var bonus = 0f
    if (hay.contains("nomic-embed-text")) {
      bonus += BONUS_NOMIC_MODEL
    }
    for (term in CHUNK_LEXICAL_TERMS) {
      if (hay.contains(term)) bonus += BONUS_PER_TERM
    }
    return bonus.coerceAtMost(BONUS_CAP)
  }

  private const val EMBED_BRIDGE_SUFFIX =
    "\nOllama nomic-embed-text embeddings API doc-index doc_index sqlite indexing chunking ModelConstants VECTOR search cosine"

  private val QUERY_HINT_SUBSTRINGS =
    listOf(
      "эмбеддинг",
      "эмбед",
      "embedding",
      "embeddings",
      "вектор",
      "vector",
      "индекс",
      "индексац",
      "indexing",
      "ollama",
      "nomic",
      "чанк",
      "chunk",
      "sqlite",
      "косинус",
      "cosine",
      "doc-index",
      "doc_index",
      "док-индекс",
    )

  private val CHUNK_LEXICAL_TERMS =
    listOf(
      "nomic-embed-text",
      "nomic",
      "/api/embeddings",
      "ollama",
      "doc_index",
      "doc-index",
      "docindex",
      "modelconstants",
      "chunking_strategy",
      "embedding_dim",
      "индексац",
      "эмбеддинг",
      "embedding",
      "builddocindex",
      "doc_index.sqlite",
    )

  private const val BONUS_NOMIC_MODEL = 0.22f
  private const val BONUS_PER_TERM = 0.03f
  private const val BONUS_CAP = 0.48f
}
