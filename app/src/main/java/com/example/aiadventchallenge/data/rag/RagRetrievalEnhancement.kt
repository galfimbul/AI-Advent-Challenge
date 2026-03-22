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
    var out = userQuery.trim()
    if (queryHintsIndexingOrEmbeddings(out)) out += EMBED_BRIDGE_SUFFIX
    if (queryHintsAgentContextStrategy(out)) out += CONTEXT_STRATEGY_EMBED_BRIDGE
    if (queryHintsAgentScreenDataFlow(out)) out += AGENT_UI_TO_API_EMBED_BRIDGE
    return out
  }

  /**
   * Re-ranks merged candidates: cosine score + small lexical bonus when the question is about
   * indexing/RAG/embeddings, agent context strategies (enum ContextStrategy, День 10), or the Agent
   * screen data path UI → API (ARCHITECTURE.md).
   */
  fun rerankChunks(query: String, chunks: List<IndexedChunk>, finalCount: Int): List<IndexedChunk> {
    if (chunks.isEmpty() || finalCount <= 0) return chunks.take(finalCount)
    val ranked =
      chunks
        .map { c ->
          val bonus = lexicalBonus(query, c)
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
    val b = lexicalBonus(query, chunk)
    return "cos=${"%.4f".format(chunk.score)} eff=${"%.4f".format(chunk.score + b)} lex=+${"%.4f".format(b)}"
  }

  private fun queryHintsIndexingOrEmbeddings(q: String): Boolean {
    val lower = q.lowercase()
    if (QUERY_HINT_SUBSTRINGS.any { lower.contains(it) }) return true
    return Regex("\\brag\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
  }

  /**
   * Вопросы про четыре режима контекста агента (День 10): SlidingWindow, StickyFacts, Branching, Summary.
   */
  private fun queryHintsAgentContextStrategy(q: String): Boolean {
    val lower = q.lowercase()
    val hasStrategyWord = lower.contains("стратег")
    val looksLikeAgentContext =
      (hasStrategyWord && (lower.contains("контекст") || lower.contains("агент"))) ||
        lower.contains("contextstrategy") ||
        (lower.contains("скользящ") && lower.contains("окн")) ||
        lower.contains("stickyfacts") ||
        lower.contains("slidingwindow") ||
        (lower.contains("ветк") && lower.contains("диалог")) ||
        (lower.contains("сжати") && lower.contains("контекст"))
    return looksLikeAgentContext
  }

  /**
   * Вопросы вида «где описан поток данных экрана Агент от UI до API», «архитектура экрана агента».
   */
  private fun queryHintsAgentScreenDataFlow(q: String): Boolean {
    val l = q.lowercase()
    val mentionsAgent = l.contains("агент") || Regex("\\bagent\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
    if (!mentionsAgent) return false
    val flowOrUiApi =
      (l.contains("поток") && (l.contains("данн") || l.contains("ui") || l.contains("api"))) ||
        l.contains("data flow") ||
        (l.contains("ui") && l.contains("api")) ||
        (l.contains("от ") && l.contains("api"))
    val whereInDocs =
      (l.contains("где") || l.contains("описан") || l.contains("описано")) &&
        (l.contains("поток") || l.contains("архитектур") || l.contains("architecture"))
    val screenPlusTech =
      l.contains("экран") && (l.contains("поток") || l.contains("ui") || l.contains("api") || l.contains("viewmodel"))
    val archAgent = l.contains("архитектур") || l.contains("architecture")
    return flowOrUiApi || whereInDocs || screenPlusTech || archAgent
  }

  private fun lexicalBonus(query: String, chunk: IndexedChunk): Float {
    val hay =
      "${chunk.titleFile}\n${chunk.section ?: ""}\n${chunk.text}"
        .lowercase()
    var bonus = 0f
    if (queryHintsIndexingOrEmbeddings(query)) {
      if (hay.contains("nomic-embed-text")) {
        bonus += BONUS_NOMIC_MODEL
      }
      for (term in INDEXING_CHUNK_TERMS) {
        if (hay.contains(term)) bonus += BONUS_PER_TERM
      }
    }
    if (queryHintsAgentContextStrategy(query)) {
      for (term in CONTEXT_STRATEGY_CHUNK_TERMS) {
        if (hay.contains(term)) bonus += BONUS_PER_TERM
      }
    }
    if (queryHintsAgentScreenDataFlow(query)) {
      for (term in AGENT_UI_FLOW_CHUNK_TERMS) {
        if (hay.contains(term)) bonus += BONUS_PER_TERM
      }
    }
    return bonus.coerceAtMost(BONUS_CAP)
  }

  private const val EMBED_BRIDGE_SUFFIX =
    "\nOllama nomic-embed-text embeddings API doc-index doc_index sqlite indexing chunking ModelConstants VECTOR search cosine"

  private const val CONTEXT_STRATEGY_EMBED_BRIDGE =
    "\nContextStrategy enum SlidingWindow StickyFacts Branching Summary agent context День 10 data_store context_strategy"

  private const val AGENT_UI_TO_API_EMBED_BRIDGE =
    "\nARCHITECTURE.md Экран Агент AgentScreen AgentViewModel Agent.process ChatRepository.sendMessage OpenAiApi UI API поток данных"

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

  private val INDEXING_CHUNK_TERMS =
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

  /** Уникальные маркеры enum / доков про стратегии контекста агента. */
  private val CONTEXT_STRATEGY_CHUNK_TERMS =
    listOf(
      "contextstrategy",
      "slidingwindow",
      "stickyfacts",
      "branching",
      "enum class contextstrategy",
      "скользящее окно",
      "context_strategy",
      "стратегии контекста",
      "sliding window",
      "sticky facts",
    )

  /** Маркеры раздела ARCHITECTURE «Экран Агент» / цепочки до API. */
  private val AGENT_UI_FLOW_CHUNK_TERMS =
    listOf(
      "agentscreen",
      "agentviewmodel",
      "agent.process",
      "chatrepository.sendmessage",
      "openaiapi",
      "экран «агент»",
      "поток данных",
      "от ui до",
      "ui до вызова",
    )

  private const val BONUS_NOMIC_MODEL = 0.22f
  private const val BONUS_PER_TERM = 0.03f
  private const val BONUS_CAP = 0.48f
}
