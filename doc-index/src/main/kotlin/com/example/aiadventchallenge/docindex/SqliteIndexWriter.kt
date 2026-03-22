package com.example.aiadventchallenge.docindex

import java.io.File
import java.sql.DriverManager
import java.time.Instant

class SqliteIndexWriter(dbFile: File) {

  private val url = "jdbc:sqlite:${dbFile.absolutePath}"

  init {
    dbFile.parentFile?.mkdirs()
    if (dbFile.exists()) {
      dbFile.delete()
    }
  }

  fun useWriter(block: SqliteIndexWriterSession.() -> Unit) {
    DriverManager.getConnection(url).use { conn ->
      conn.autoCommit = false
      conn.createStatement().use { st ->
        st.executeUpdate("DROP TABLE IF EXISTS chunks")
        st.executeUpdate("DROP TABLE IF EXISTS index_meta")
        st.executeUpdate(
          """
          CREATE TABLE chunks (
            id TEXT PRIMARY KEY,
            chunking_strategy TEXT NOT NULL,
            source TEXT NOT NULL,
            title_file TEXT NOT NULL,
            section TEXT,
            text TEXT NOT NULL,
            embedding BLOB NOT NULL,
            embedding_dim INTEGER NOT NULL,
            model TEXT NOT NULL
          )
          """.trimIndent(),
        )
        st.executeUpdate(
          """
          CREATE INDEX idx_chunks_strategy ON chunks(chunking_strategy)
          """.trimIndent(),
        )
        st.executeUpdate(
          """
          CREATE TABLE index_meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
          )
          """.trimIndent(),
        )
      }
      val session = SqliteIndexWriterSession(conn)
      try {
        session.block()
        conn.commit()
      } finally {
        session.closeStatements()
      }
    }
  }
}

class SqliteIndexWriterSession(private val conn: java.sql.Connection) {

  private val insertChunk =
    conn.prepareStatement(
      """
      INSERT INTO chunks (
        id, chunking_strategy, source, title_file, section, text, embedding, embedding_dim, model
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
    )

  private val metaInsert =
    conn.prepareStatement(
      """
      INSERT INTO index_meta (key, value) VALUES (?, ?)
      """.trimIndent(),
    )

  fun insertMeta(key: String, value: String) {
    metaInsert.setString(1, key)
    metaInsert.setString(2, value)
    metaInsert.executeUpdate()
  }

  fun insertChunk(
    id: String,
    strategy: ChunkingStrategy,
    source: String,
    titleFile: String,
    section: String?,
    text: String,
    embedding: FloatArray,
    model: String,
  ) {
    val blob = floatArrayToLittleEndianBlob(embedding)
    insertChunk.setString(1, id)
    insertChunk.setString(2, strategy.wireName)
    insertChunk.setString(3, source)
    insertChunk.setString(4, titleFile)
    insertChunk.setString(5, section)
    insertChunk.setString(6, text)
    insertChunk.setBytes(7, blob)
    insertChunk.setInt(8, embedding.size)
    insertChunk.setString(9, model)
    insertChunk.executeUpdate()
  }

  fun closeStatements() {
    insertChunk.close()
    metaInsert.close()
  }
}

fun SqliteIndexWriterSession.writeStandardMeta(
  model: String,
  embeddingDim: Int,
) {
  insertMeta("schema_version", ModelConstants.SCHEMA_VERSION)
  insertMeta("model", model)
  insertMeta("embedding_dim", embeddingDim.toString())
  insertMeta("built_at", Instant.now().toString())
  insertMeta("fixed_window_size", ModelConstants.FIXED_WINDOW_SIZE.toString())
  insertMeta("fixed_window_overlap", ModelConstants.FIXED_WINDOW_OVERLAP.toString())
  insertMeta("structure_max_section_chars", ModelConstants.STRUCTURE_MAX_SECTION_CHARS.toString())
  insertMeta("max_embedding_input_chars", ModelConstants.MAX_EMBEDDING_INPUT_CHARS.toString())
  insertMeta("embedding_input_overlap", ModelConstants.EMBEDDING_INPUT_OVERLAP.toString())
}
