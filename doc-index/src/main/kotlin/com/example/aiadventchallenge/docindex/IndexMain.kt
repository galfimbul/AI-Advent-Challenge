package com.example.aiadventchallenge.docindex

import java.io.File

data class IndexCliArgs(
  val repoRoot: String,
  val output: String,
  val ollamaBase: String?,
)

fun parseIndexArgs(args: Array<String>): IndexCliArgs {
  var repoRoot: String? = null
  var output: String? = null
  var ollamaBase: String? = null
  var i = 0
  while (i < args.size) {
    when (args[i]) {
      "--repo-root" -> {
        repoRoot = args.getOrNull(++i) ?: error("--repo-root requires a path")
      }
      "--output" -> {
        output = args.getOrNull(++i) ?: error("--output requires a path")
      }
      "--ollama-base" -> {
        ollamaBase = args.getOrNull(++i) ?: error("--ollama-base requires a URL")
      }
      else -> error("Unknown argument: ${args[i]}")
    }
    i++
  }
  return IndexCliArgs(
    repoRoot = repoRoot ?: error("Missing --repo-root"),
    output = output ?: error("Missing --output"),
    ollamaBase = ollamaBase,
  )
}

fun main(args: Array<String>) {
  val cli = parseIndexArgs(args)
  val repoDir = File(cli.repoRoot)
  if (!repoDir.isDirectory) {
    error("Repo root is not a directory: ${repoDir.absolutePath}")
  }
  val documents = CorpusScanner(repoDir).loadDocuments()
  if (documents.isEmpty()) {
    error("No corpus documents found under ${repoDir.absolutePath}")
  }
  val fixedChunker = FixedWindowChunker()
  val structureChunker = StructureChunker()
  val chunks = ArrayList<TextChunk>()
  for (doc in documents) {
    chunks.addAll(fixedChunker.chunk(doc))
  }
  for (doc in documents) {
    chunks.addAll(structureChunker.chunk(doc))
  }
  if (chunks.isEmpty()) {
    error("Chunking produced zero chunks")
  }
  val baseUrl =
    resolveOllamaBaseUrl(cli.ollamaBase, System.getenv("OLLAMA_HOST"))
  val client = OllamaEmbeddingsClient(baseUrl = baseUrl)
  val outFile = File(cli.output)
  val writer = SqliteIndexWriter(outFile)
  var embeddingDim: Int? = null
  var rowsWritten = 0
  writer.useWriter {
    var logicalIndex = 0
    for (chunk in chunks) {
      val pieces = splitTextForOllamaEmbedding(chunk.text)
      if (pieces.isEmpty()) continue
      pieces.forEachIndexed { pi, piece ->
        val embedding = client.embed(piece)
        if (embeddingDim == null) {
          embeddingDim = embedding.size
        } else {
          require(embeddingDim == embedding.size) {
            "Embedding dimension mismatch: expected $embeddingDim, got ${embedding.size}"
          }
        }
        val rowId =
          if (pieces.size == 1) {
            chunk.id
          } else {
            "${chunk.id}__emb_$pi"
          }
        val rowSection =
          if (pieces.size == 1) {
            chunk.section
          } else {
            "${chunk.section} · embed ${pi + 1}/${pieces.size}"
          }
        insertChunk(
          id = rowId,
          strategy = chunk.strategy,
          source = chunk.source,
          titleFile = chunk.titleFile,
          section = rowSection,
          text = piece,
          embedding = embedding,
          model = ModelConstants.OLLAMA_MODEL,
        )
        rowsWritten++
        if (rowsWritten % 20 == 0) {
          println("Indexed $rowsWritten rows (${logicalIndex + 1} / ${chunks.size} logical chunks)…")
        }
        Thread.sleep(5)
      }
      logicalIndex++
    }
    writeStandardMeta(ModelConstants.OLLAMA_MODEL, embeddingDim!!)
  }
  println(
    "Done. Wrote $rowsWritten DB rows from ${chunks.size} logical chunks to ${outFile.absolutePath} (dim=$embeddingDim).",
  )
}
