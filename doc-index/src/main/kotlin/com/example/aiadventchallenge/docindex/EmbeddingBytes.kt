package com.example.aiadventchallenge.docindex

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun floatArrayToLittleEndianBlob(values: FloatArray): ByteArray {
  val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
  for (f in values) {
    buffer.putFloat(f)
  }
  return buffer.array()
}

fun littleEndianBlobToFloatArray(blob: ByteArray): FloatArray {
  val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
  val n = blob.size / 4
  val out = FloatArray(n)
  for (i in 0 until n) {
    out[i] = buffer.getFloat()
  }
  return out
}
