package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk

object RagMarkdownFormatter {

  const val MAX_CONTEXT_CHARS = 10_000

  /**
   * Builds a markdown block for the prompt from retrieved chunks (file title, optional section, body).
   */
  fun formatChunks(chunks: List<IndexedChunk>): String {
    if (chunks.isEmpty()) return ""
    val sb = StringBuilder()
    var used = 0
    for (chunk in chunks) {
      val header = "### ${chunk.titleFile}\n"
      val sectionLine =
        if (!chunk.section.isNullOrBlank()) {
          "**Раздел:** ${chunk.section}\n"
        } else {
          ""
        }
      val body = chunk.text.trim()
      val block = buildString {
        append(header)
        append(sectionLine)
        append(body)
        append("\n\n")
      }
      if (used + block.length > MAX_CONTEXT_CHARS) {
        val remaining = MAX_CONTEXT_CHARS - used
        if (remaining > 120) {
          val part = block.take(remaining)
          sb.append(part)
          if (part.length < block.length) sb.append("…")
          sb.append("\n")
        }
        break
      }
      sb.append(block)
      used += block.length
    }
    return sb.toString().trimEnd()
  }
}
