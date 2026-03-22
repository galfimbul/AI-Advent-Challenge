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
  fun embedPrompt_expandsWhenAgentScreenDataFlowTopic() {
    val q = "Где описан поток данных экрана «Агент» (от UI до API)?"
    val expanded = RagRetrievalEnhancement.embedPromptForSearch(q)
    assertNotEquals(q, expanded)
    assertTrue(expanded.contains("AgentScreen", ignoreCase = true))
    assertTrue(expanded.contains("OpenAiApi", ignoreCase = true))
  }

  @Test
  fun embedPrompt_expandsWhenContextStrategyTopic() {
    val q = "Какие четыре стратегии контекста есть у агента и как они называются в коде?"
    val expanded = RagRetrievalEnhancement.embedPromptForSearch(q)
    assertNotEquals(q, expanded)
    assertTrue(expanded.contains("SlidingWindow", ignoreCase = true))
    assertTrue(expanded.contains("ContextStrategy", ignoreCase = true))
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

  @Test
  fun rerank_prefersArchitectureAgentFlowOverNavPlans() {
    val noise =
      IndexedChunk(
        id = "home",
        source = "x",
        titleFile = "docs/HOME_SCREEN_PLAN.md",
        section = "Расширение в будущем",
        text = "Новый экран → маршрут в NavHost и HomeSection",
        score = 0.84f,
      )
    val signal =
      IndexedChunk(
        id = "arch",
        source = "x",
        titleFile = "ARCHITECTURE.md",
        section = "Экран «Агент»",
        text =
          "Поток данных от UI до вызова OpenAI API: AgentScreen → AgentViewModel → " +
            "Agent.process → ChatRepository.sendMessage → OpenAiApi.",
        score = 0.58f,
      )
    val q = "Где описан поток данных экрана «Агент» (от UI до API)?"
    val out = RagRetrievalEnhancement.rerankChunks(q, listOf(noise, signal), finalCount = 2)
    assertTrue(
      "expected [arch,home] got ${out.map { it.id }}",
      out.size == 2 && out[0].id == "arch" && out[1].id == "home",
    )
  }

  @Test
  fun rerank_prefersChunkWithContextStrategyWhenQueryAboutAgentContext() {
    val noise =
      IndexedChunk(
        id = "net",
        source = "x",
        titleFile = "README.md",
        section = "Проблемы с сетью",
        text = "Проверьте подключение к интернету и Logcat",
        score = 0.82f,
      )
    val signal =
      IndexedChunk(
        id = "ctx",
        source = "x",
        titleFile = "ContextStrategy.kt",
        section = "стратегии контекста",
        text =
          "enum class ContextStrategy { SlidingWindow, StickyFacts, Branching, Summary } " +
            "context_strategy скользящее окно; см. также sliding window и sticky facts.",
        score = 0.55f,
      )
    val q = "Какие четыре стратегии контекста есть у агента и как они называются в коде?"
    val out = RagRetrievalEnhancement.rerankChunks(q, listOf(noise, signal), finalCount = 2)
    assertTrue(
      "expected [ctx,net] got ${out.map { it.id }}",
      out.size == 2 && out[0].id == "ctx" && out[1].id == "net",
    )
  }
}
