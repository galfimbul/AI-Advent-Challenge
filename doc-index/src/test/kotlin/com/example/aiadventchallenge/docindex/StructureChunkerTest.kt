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
    val oneChunk = chunks.first { it.section == "One" }
    assertTrue(oneChunk.text.startsWith("One\n\n"))
    assertTrue(oneChunk.text.contains("Body one"))
  }

  @Test
  fun markdown_h4StartsNewSection() {
    val md =
      """
      ## Parent
      Under parent

      #### Child
      Under child
      """.trimIndent()
    val doc = CorpusDocument("h.md", md)
    val chunks = StructureChunker().chunk(doc)
    assertTrue(chunks.any { it.section == "Child" })
    val child = chunks.first { it.section == "Child" }
    assertTrue(child.text.startsWith("Child\n\n"))
    assertTrue(child.text.contains("Under child"))
  }

  @Test
  fun splitOversizedSection_splitsWhenTooLong() {
    val body = "Z".repeat(5000)
    val parts = splitOversizedSection(body, maxSectionChars = 4000, subWindow = 1200, subOverlap = 200)
    assertTrue(parts.size > 1)
  }

  @Test
  fun splitOversizedSection_prefersParagraphBoundaries() {
    val p1 = "A".repeat(1500)
    val p2 = "B".repeat(1500)
    val p3 = "C".repeat(1500)
    val body = "$p1\n\n$p2\n\n$p3"
    val parts = splitOversizedSection(body, maxSectionChars = 4000, subWindow = 1200, subOverlap = 200)
    assertEquals(2, parts.size)
    assertTrue(parts[0].contains("AAAA"))
    assertTrue(parts[0].contains("BBBB"))
    assertTrue(parts[1].contains("CCCC"))
  }

  @Test
  fun kotlin_file_singleChunkWhenSmall() {
    val doc = CorpusDocument("app/src/main/java/A.kt", "fun x() = 1\n")
    val chunks = StructureChunker().chunk(doc)
    assertEquals(1, chunks.size)
    assertEquals(ChunkingStrategy.STRUCTURE, chunks[0].strategy)
    assertTrue(chunks[0].text.startsWith("A.kt\n\n"))
    assertTrue(chunks[0].text.contains("fun x()"))
  }
}
