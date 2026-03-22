package com.example.aiadventchallenge.data.index

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingVector {
  fun fromLittleEndianBlob(blob: ByteArray): FloatArray {
    val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
    val n = blob.size / 4
    val out = FloatArray(n)
    for (i in 0 until n) {
      out[i] = buffer.getFloat()
    }
    return out
  }
}

object VectorMath {
  fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vector size mismatch: ${a.size} vs ${b.size}" }
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
      val x = a[i].toDouble()
      val y = b[i].toDouble()
      dot += x * y
      na += x * x
      nb += y * y
    }
    val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
    if (denom == 0.0) return 0f
    return (dot / denom).toFloat()
  }
}
