package com.example.aiadventchallenge.docindex

/**
 * Splits [text] so each piece fits Ollama embedding context (see [ModelConstants.MAX_EMBEDDING_INPUT_CHARS]).
 */
fun splitTextForOllamaEmbedding(
  text: String,
  maxChars: Int = ModelConstants.MAX_EMBEDDING_INPUT_CHARS,
  overlap: Int = ModelConstants.EMBEDDING_INPUT_OVERLAP,
): List<String> {
  if (text.isEmpty()) return emptyList()
  if (text.length <= maxChars) return listOf(text)
  require(overlap < maxChars)
  return splitFixedWindows(text, maxChars, overlap)
}
