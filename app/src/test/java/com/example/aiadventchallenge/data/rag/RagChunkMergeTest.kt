package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk
import org.junit.Assert.assertEquals
import org.junit.Test

class RagChunkMergeTest {

  @Test
  fun merge_keepsHigherScorePerId() {
    val a = chunk("id1", 0.5f)
    val b = chunk("id1", 0.9f)
    val c = chunk("id2", 0.8f)
    val merged = RagChunkMerge.mergeByBestScorePerId(listOf(a, b, c), maxResults = 10)
    assertEquals(2, merged.size)
    assertEquals(0.9f, merged.first { it.id == "id1" }.score, 0.001f)
  }

  @Test
  fun merge_respectsMaxResults() {
    val list = listOf(chunk("a", 1f), chunk("b", 0.9f), chunk("c", 0.8f))
    val merged = RagChunkMerge.mergeByBestScorePerId(list, maxResults = 2)
    assertEquals(2, merged.size)
    assertEquals("a", merged[0].id)
    assertEquals("b", merged[1].id)
  }

  private fun chunk(id: String, score: Float) =
    IndexedChunk(
      id = id,
      source = "s",
      titleFile = "f.md",
      section = null,
      text = "t",
      score = score,
    )
}
