package com.example.aiadventchallenge.docindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingInputSplitTest {

  @Test
  fun shortText_singlePiece() {
    val t = "hello"
    assertEquals(listOf(t), splitTextForOllamaEmbedding(t, maxChars = 100, overlap = 10))
  }

  @Test
  fun longText_multiplePieces() {
    val t = "a".repeat(5000)
    val parts = splitTextForOllamaEmbedding(t, maxChars = 1000, overlap = 100)
    assertTrue(parts.size > 1)
    assertTrue(parts.all { it.length <= 1000 })
  }
}
