package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk
import org.junit.Assert.assertTrue
import org.junit.Test

class RagMarkdownFormatterTest {

  @Test
  fun formatChunks_includesTitleSectionAndBody() {
    val chunks =
      listOf(
        IndexedChunk(
          id = "1",
          source = "docs/X.md",
          titleFile = "ARCHITECTURE.md",
          section = "## Agent",
          text = "  Hello world  ",
          score = 0.9f,
        ),
      )
    val md = RagMarkdownFormatter.formatChunks(chunks)
    assertTrue(md.contains("### ARCHITECTURE.md"))
    assertTrue(md.contains("**Раздел:** ## Agent"))
    assertTrue(md.contains("Hello world"))
  }

  @Test
  fun formatChunks_omitsSectionLineWhenNull() {
    val chunks =
      listOf(
        IndexedChunk(
          id = "1",
          source = "x",
          titleFile = "README.md",
          section = null,
          text = "Body",
          score = 1f,
        ),
      )
    val md = RagMarkdownFormatter.formatChunks(chunks)
    assertTrue(md.contains("### README.md"))
    assertTrue(md.contains("Body"))
    assertTrue(!md.contains("**Раздел:**"))
  }

  @Test
  fun formatChunks_truncatesWhenOverMaxChars() {
    val longText = "a".repeat(RagMarkdownFormatter.MAX_CONTEXT_CHARS + 500)
    val chunks =
      listOf(
        IndexedChunk(
          id = "1",
          source = "x",
          titleFile = "BIG.md",
          section = null,
          text = longText,
          score = 1f,
        ),
      )
    val md = RagMarkdownFormatter.formatChunks(chunks)
    assertTrue(md.length <= RagMarkdownFormatter.MAX_CONTEXT_CHARS + 50)
    assertTrue(md.endsWith("…") || md.length <= RagMarkdownFormatter.MAX_CONTEXT_CHARS)
  }
}
