package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagRetrievalEnhancementTest {

  @Test
  fun embedPrompt_expandsWhenEmbeddingTopic() {
    val q = "Какая модель эмбеддингов используется при индексации и для RAG?"
    val expanded = RagRetrievalEnhancement.embedPromptForSearch(q)
    assertNotEquals(q, expanded)
    assertTrue(expanded.contains("nomic-embed-text", ignoreCase = true))
  }

  @Test
  fun embedPrompt_unchangedForUnrelatedQuestion() {
    val q = "Как дела?"
    assertTrue(RagRetrievalEnhancement.embedPromptForSearch(q) == q)
  }

  @Test
  fun rerank_prefersChunkWithNomicWhenQueryAboutEmbeddings() {
    val noise =
      IndexedChunk(
        id = "a",
        source = "x",
        titleFile = "README.md",
        section = "Сеть",
        text = "Проверьте интернет и Logcat",
        score = 0.92f,
      )
    val signal =
      IndexedChunk(
        id = "b",
        source = "x",
        titleFile = "doc-index/README.md",
        section = "x",
        text = "Ollama model nomic-embed-text for embeddings",
        score = 0.55f,
      )
    val q = "Какая модель эмбеддингов используется при индексации и для RAG?"
    val out = RagRetrievalEnhancement.rerankChunks(q, listOf(noise, signal), finalCount = 2)
    assertTrue(
      "expected [b,a] got ${out.map { it.id }}",
      out.size == 2 && out[0].id == "b" && out[1].id == "a",
    )
  }
}
