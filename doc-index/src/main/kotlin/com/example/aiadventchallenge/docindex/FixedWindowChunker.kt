package com.example.aiadventchallenge.docindex

import java.security.MessageDigest

class FixedWindowChunker(
  private val windowSize: Int = ModelConstants.FIXED_WINDOW_SIZE,
  private val overlap: Int = ModelConstants.FIXED_WINDOW_OVERLAP,
  private val source: String = ModelConstants.SOURCE_LABEL,
) {

  fun chunk(doc: CorpusDocument): List<TextChunk> {
    val text = normalizeNewlines(doc.text)
    if (text.isEmpty()) return emptyList()
    val pathHash = shortHash(doc.titleFile)
    val windows = splitFixedWindows(text, windowSize, overlap)
    return windows.mapIndexed { index, window ->
      val part = "${index + 1}/${windows.size}"
      TextChunk(
        id = "FIXED_WINDOW_${pathHash}_$index",
        strategy = ChunkingStrategy.FIXED_WINDOW,
        source = source,
        titleFile = doc.titleFile,
        section = "part:$part",
        text = window,
      )
    }
  }
}

fun splitFixedWindows(text: String, windowSize: Int, overlap: Int): List<String> {
  require(windowSize > 0)
  require(overlap < windowSize)
  if (text.isEmpty()) return emptyList()
  val step = windowSize - overlap
  val out = ArrayList<String>()
  var start = 0
  while (start < text.length) {
    val end = minOf(start + windowSize, text.length)
    out.add(text.substring(start, end))
    if (end >= text.length) break
    start += step
  }
  return out
}

private fun shortHash(s: String): String {
  val md = MessageDigest.getInstance("SHA-256")
  val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
  return bytes.joinToString("") { b -> "%02x".format(b) }.take(12)
}
