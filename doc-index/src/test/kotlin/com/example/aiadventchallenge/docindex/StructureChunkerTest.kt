package com.example.aiadventchallenge.docindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureChunkerTest {

  @Test
  fun markdown_splitsByHeadings() {
    val md =
      """
      Intro line

      ## One
      Body one

      ### Two
      Body two
      """.trimIndent()
    val doc = CorpusDocument("f.md", md)
    val chunks = StructureChunker().chunk(doc)
    val sections = chunks.map { it.section }.toSet()
    assertTrue(sections.any { it.contains("One") })
    assertTrue(sections.any { it.contains("Two") })
  }

  @Test
  fun splitOversizedSection_splitsWhenTooLong() {
    val body = "Z".repeat(5000)
    val parts = splitOversizedSection(body, maxSectionChars = 4000, subWindow = 1200, subOverlap = 200)
    assertTrue(parts.size > 1)
  }

  @Test
  fun kotlin_file_singleChunkWhenSmall() {
    val doc = CorpusDocument("app/src/main/java/A.kt", "fun x() = 1\n")
    val chunks = StructureChunker().chunk(doc)
    assertEquals(1, chunks.size)
    assertEquals(ChunkingStrategy.STRUCTURE, chunks[0].strategy)
  }
}
