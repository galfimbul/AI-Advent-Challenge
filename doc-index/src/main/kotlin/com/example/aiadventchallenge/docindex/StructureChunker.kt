package com.example.aiadventchallenge.docindex

import java.security.MessageDigest

/** ATX-заголовки Markdown уровней 1–6 (как в CommonMark). */
private val MD_HEADER = Regex("^#{1,6}\\s+(.+)$")

class StructureChunker(
  private val maxSectionChars: Int = ModelConstants.STRUCTURE_MAX_SECTION_CHARS,
  private val subWindow: Int = ModelConstants.STRUCTURE_SUB_WINDOW,
  private val subOverlap: Int = ModelConstants.STRUCTURE_SUB_OVERLAP,
  /** Запас под строку раздела в начале [TextChunk.text] (для эмбеддинга). */
  private val headingLineReserve: Int = ModelConstants.STRUCTURE_HEADING_LINE_RESERVE,
  private val source: String = ModelConstants.SOURCE_LABEL,
) {

  fun chunk(doc: CorpusDocument): List<TextChunk> {
    val raw = normalizeNewlines(doc.text)
    if (raw.isEmpty()) return emptyList()
    return if (doc.titleFile.endsWith(".md", ignoreCase = true)) {
      chunkMarkdown(doc.titleFile, raw)
    } else {
      chunkPlainFile(doc.titleFile, raw)
    }
  }

  private fun chunkMarkdown(titleFile: String, text: String): List<TextChunk> {
    val lines = text.lines()
    data class Section(val heading: String, val body: String)
    val sections = ArrayList<Section>()
    var currentHeading = ""
    val bodyBuf = StringBuilder()
    fun emit() {
      val body = bodyBuf.toString().trimEnd()
      bodyBuf.clear()
      if (body.isEmpty() && currentHeading.isEmpty()) return
      val label = currentHeading.ifEmpty { "(preamble)" }
      sections.add(Section(label, body))
    }
    for (line in lines) {
      val m = MD_HEADER.matchEntire(line)
      if (m != null) {
        emit()
        currentHeading = m.groupValues[1].trim()
      } else {
        bodyBuf.append(line).append('\n')
      }
    }
    emit()
    val pathHash = shortHash(titleFile)
    val out = ArrayList<TextChunk>()
    var sectionIndex = 0
    for (sec in sections) {
      val body = sec.body
      val headingLabel = sec.heading
      val bodyBudget = (maxSectionChars - headingLineReserve).coerceAtLeast(subWindow)
      val pieces = splitOversizedSection(body, bodyBudget, subWindow, subOverlap)
      pieces.forEachIndexed { i, piece ->
        val sectionLabel =
          if (pieces.size == 1) headingLabel
          else "$headingLabel · part ${i + 1}/${pieces.size}"
        out.add(
          TextChunk(
            id = "STRUCTURE_${pathHash}_${sectionIndex}_$i",
            strategy = ChunkingStrategy.STRUCTURE,
            source = source,
            titleFile = titleFile,
            section = sectionLabel,
            text = structureChunkText(sectionLabel, piece),
          ),
        )
      }
      sectionIndex++
    }
    return out
  }

  private fun chunkPlainFile(titleFile: String, text: String): List<TextChunk> {
    val pathHash = shortHash(titleFile)
    val baseName = titleFile.substringAfterLast('/')
    val bodyBudget = (maxSectionChars - headingLineReserve).coerceAtLeast(subWindow)
    val pieces = splitOversizedSection(text, bodyBudget, subWindow, subOverlap)
    return pieces.mapIndexed { i, piece ->
      val sectionLabel =
        if (pieces.size == 1) baseName
        else "$baseName · part ${i + 1}/${pieces.size}"
      TextChunk(
        id = "STRUCTURE_${pathHash}_0_$i",
        strategy = ChunkingStrategy.STRUCTURE,
        source = source,
        titleFile = titleFile,
        section = sectionLabel,
        text = structureChunkText(sectionLabel, piece),
      )
    }
  }
}

/**
 * Тело чанка для индекса: первая строка — подпись раздела, чтобы эмбеддинг учитывал заголовок
 * (в [TextChunk.text] раньше был только body под ## в исходнике).
 */
internal fun structureChunkText(sectionLabel: String, body: String): String {
  val b = body.trimStart()
  if (sectionLabel.isBlank()) return b
  return "$sectionLabel\n\n$b"
}

/**
 * Длинные секции: сначала склеиваем абзацы (двойной перевод строки) до лимита, затем при необходимости
 * окно с перекрытием [splitFixedWindows] (в т.ч. для одного гигантского абзаца).
 */
internal fun splitOversizedSection(
  body: String,
  maxSectionChars: Int,
  subWindow: Int,
  subOverlap: Int,
): List<String> {
  if (body.isEmpty()) return emptyList()
  if (body.length <= maxSectionChars) return listOf(body)
  val paras =
    body.split(Regex("\n{2,}"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  if (paras.isEmpty()) {
    val t = body.trim()
    return when {
      t.isEmpty() -> emptyList()
      t.length <= maxSectionChars -> listOf(t)
      else -> splitFixedWindows(t, subWindow, subOverlap)
    }
  }
  val buckets = ArrayList<String>()
  var bucket = StringBuilder()
  fun flushBucket() {
    if (bucket.isNotEmpty()) {
      buckets.add(bucket.toString())
      bucket = StringBuilder()
    }
  }
  for (p in paras) {
    val needSep = bucket.isNotEmpty()
    val addLen = p.length + if (needSep) 2 else 0
    if (bucket.length + addLen <= maxSectionChars) {
      if (needSep) bucket.append("\n\n")
      bucket.append(p)
    } else {
      flushBucket()
      if (p.length <= maxSectionChars) {
        bucket.append(p)
      } else {
        buckets.addAll(splitFixedWindows(p, subWindow, subOverlap))
      }
    }
  }
  flushBucket()
  return buckets.flatMap { bucketText ->
    when {
      bucketText.length <= maxSectionChars -> listOf(bucketText)
      else -> splitFixedWindows(bucketText, subWindow, subOverlap)
    }
  }
}

private fun shortHash(s: String): String {
  val md = MessageDigest.getInstance("SHA-256")
  val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
  return bytes.joinToString("") { b -> "%02x".format(b) }.take(12)
}
