package com.example.aiadventchallenge.docindex

data class TextChunk(
  val id: String,
  val strategy: ChunkingStrategy,
  val source: String,
  val titleFile: String,
  val section: String,
  val text: String,
)
