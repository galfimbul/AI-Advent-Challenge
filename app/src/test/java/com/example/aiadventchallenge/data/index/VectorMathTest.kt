package com.example.aiadventchallenge.data.index

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorMathTest {

  @Test
  fun cosine_identical_isOne() {
    val v = floatArrayOf(1f, 2f, 3f)
    assertEquals(1f, VectorMath.cosineSimilarity(v, v), 1e-5f)
  }

  @Test
  fun cosine_orthogonal_isZero() {
    val a = floatArrayOf(1f, 0f)
    val b = floatArrayOf(0f, 1f)
    assertEquals(0f, VectorMath.cosineSimilarity(a, b), 1e-5f)
  }

  @Test
  fun topK_byCosine_ordersCorrectly() {
    val query = floatArrayOf(1f, 0f, 0f)
    val candidates =
      listOf(
        "ortho" to floatArrayOf(0f, 1f, 0f),
        "same" to floatArrayOf(1f, 0f, 0f),
        "partial" to floatArrayOf(0.7f, 0.7f, 0f),
      )
    val top =
      candidates
        .map { (id, v) -> id to VectorMath.cosineSimilarity(query, v) }
        .sortedByDescending { it.second }
        .take(2)
    assertEquals("same", top[0].first)
    assertEquals(1f, top[0].second, 1e-5f)
    assertEquals("partial", top[1].first)
  }

  @Test
  fun blob_roundTrip() {
    val original = floatArrayOf(0.5f, -1.25f, 3f)
    val buffer = ByteBuffer.allocate(original.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (f in original) {
      buffer.putFloat(f)
    }
    val parsed = EmbeddingVector.fromLittleEndianBlob(buffer.array())
    assertEquals(original.size, parsed.size)
    for (i in original.indices) {
      assertEquals(original[i], parsed[i], 1e-5f)
    }
  }
}
