package com.example.aiadventchallenge.data.index

data class IndexedChunk(
  val id: String,
  val source: String,
  val titleFile: String,
  val section: String?,
  val text: String,
  val score: Float,
)
