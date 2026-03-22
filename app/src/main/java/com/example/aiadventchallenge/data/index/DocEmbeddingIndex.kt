package com.example.aiadventchallenge.data.index

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class DocEmbeddingIndex(
  private val database: SQLiteDatabase,
  private val expectedDim: Int,
) {

  fun search(
    queryEmbedding: FloatArray,
    strategy: ChunkingStrategy,
    topK: Int,
  ): List<IndexedChunk> {
    require(queryEmbedding.size == expectedDim) {
      "Query embedding dim ${queryEmbedding.size} != index dim $expectedDim"
    }
    require(topK > 0)
    val sql =
      """
      SELECT id, source, title_file, section, text, embedding
      FROM chunks
      WHERE chunking_strategy = ?
      """.trimIndent()
    val hits = ArrayList<IndexedChunk>()
    database.rawQuery(sql, arrayOf(strategy.wireName)).use { cursor ->
      val idCol = cursor.getColumnIndexOrThrow("id")
      val sourceCol = cursor.getColumnIndexOrThrow("source")
      val fileCol = cursor.getColumnIndexOrThrow("title_file")
      val sectionCol = cursor.getColumnIndexOrThrow("section")
      val textCol = cursor.getColumnIndexOrThrow("text")
      val embCol = cursor.getColumnIndexOrThrow("embedding")
      while (cursor.moveToNext()) {
        val blob = cursor.getBlob(embCol) ?: continue
        val vec = EmbeddingVector.fromLittleEndianBlob(blob)
        if (vec.size != expectedDim) continue
        val score = VectorMath.cosineSimilarity(queryEmbedding, vec)
        val chunk =
          IndexedChunk(
            id = cursor.getString(idCol),
            source = cursor.getString(sourceCol),
            titleFile = cursor.getString(fileCol),
            section =
              if (cursor.isNull(sectionCol)) null
              else cursor.getString(sectionCol),
            text = cursor.getString(textCol),
            score = score,
          )
        hits.add(chunk)
      }
    }
    return hits.sortedByDescending { it.score }.take(topK)
  }

  fun close() {
    database.close()
  }

  companion object {
    fun openFromAssets(context: Context): DocEmbeddingIndex {
      val dbFile = copyAssetDbIfNeeded(context)
      val db =
        SQLiteDatabase.openDatabase(
          dbFile.absolutePath,
          null,
          SQLiteDatabase.OPEN_READONLY,
        )
      val dim = readEmbeddingDim(db)
      return DocEmbeddingIndex(db, dim)
    }

    private fun copyAssetDbIfNeeded(context: Context): File {
      val out = File(context.filesDir, DocIndexAssetConstants.ASSET_FILE_NAME)
      val name = DocIndexAssetConstants.ASSET_FILE_NAME
      context.assets.openFd(name).use { afd ->
        val assetLen = afd.length
        if (!out.exists() || out.length() != assetLen) {
          context.assets.open(name).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
          }
        }
      }
      return out
    }

    private fun readEmbeddingDim(db: SQLiteDatabase): Int {
      db.rawQuery(
        "SELECT value FROM index_meta WHERE key = ? LIMIT 1",
        arrayOf("embedding_dim"),
      ).use { c ->
        if (!c.moveToFirst()) {
          error("doc_index.sqlite missing index_meta.embedding_dim")
        }
        return c.getString(0).toInt()
      }
    }
  }
}
