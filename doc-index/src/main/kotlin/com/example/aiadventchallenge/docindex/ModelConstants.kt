package com.example.aiadventchallenge.docindex

object ModelConstants {
  const val OLLAMA_MODEL = "nomic-embed-text"
  const val DEFAULT_OLLAMA_BASE = "http://127.0.0.1:11434"
  const val SOURCE_LABEL = "repo:AIAdventChallenge"

  const val FIXED_WINDOW_SIZE = 1200
  const val FIXED_WINDOW_OVERLAP = 200

  const val STRUCTURE_MAX_SECTION_CHARS = 4000
  const val STRUCTURE_SUB_WINDOW = 1200
  const val STRUCTURE_SUB_OVERLAP = 200

  /**
   * Ollama returns HTTP 500 "input length exceeds the context length" if the prompt is too long
   * in **tokens** (code/Cyrillic expand faster than plain English). We split again before embed.
   */
  const val MAX_EMBEDDING_INPUT_CHARS = 2048
  const val EMBEDDING_INPUT_OVERLAP = 256

  const val SCHEMA_VERSION = "1"
}
