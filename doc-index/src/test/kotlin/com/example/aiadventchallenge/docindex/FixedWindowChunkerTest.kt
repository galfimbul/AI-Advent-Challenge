package com.example.aiadventchallenge.docindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedWindowChunkerTest {

  @Test
  fun splitFixedWindows_nonOverlapping() {
    val text = "a".repeat(25)
    val parts = splitFixedWindows(text, windowSize = 10, overlap = 0)
    assertEquals(listOf("a".repeat(10), "a".repeat(10), "a".repeat(5)), parts)
  }

  @Test
  fun splitFixedWindows_withOverlap() {
    val text = "0123456789ABCDEF"
    val parts = splitFixedWindows(text, windowSize = 6, overlap = 2)
    assertTrue(parts[0].startsWith("012345"))
    assertEquals(4, parts.size)
  }

  @Test
  fun chunk_countsSections() {
    val doc = CorpusDocument("x.md", "0123456789".repeat(200))
    val chunks = FixedWindowChunker(windowSize = 50, overlap = 10).chunk(doc)
    assertTrue(chunks.isNotEmpty())
    assertTrue(chunks.all { it.strategy == ChunkingStrategy.FIXED_WINDOW })
    assertTrue(chunks.all { it.section.startsWith("part:") })
  }
}
