package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.index.IndexedChunk

internal object RagChunkMerge {

  /**
   * Merges hits from several searches: keeps best-scoring row per chunk [id], then top [maxResults] overall.
   */
  fun mergeByBestScorePerId(chunks: Iterable<IndexedChunk>, maxResults: Int): List<IndexedChunk> {
    require(maxResults > 0)
    val seen = HashSet<String>()
    val out = ArrayList<IndexedChunk>(maxResults)
    for (c in chunks.sortedByDescending { it.score }) {
      if (!seen.add(c.id)) continue
      out.add(c)
      if (out.size >= maxResults) break
    }
    return out
  }
}
