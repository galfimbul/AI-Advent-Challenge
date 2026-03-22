package com.example.aiadventchallenge.docindex

import java.security.MessageDigest

private val MD_HEADER = Regex("^#{1,3}\\s+(.+)$")

class StructureChunker(
  private val maxSectionChars: Int = ModelConstants.STRUCTURE_MAX_SECTION_CHARS,
  private val subWindow: Int = ModelConstants.STRUCTURE_SUB_WINDOW,
  private val subOverlap: Int = ModelConstants.STRUCTURE_SUB_OVERLAP,
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
      val pieces = splitOversizedSection(body, maxSectionChars, subWindow, subOverlap)
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
            text = piece,
          ),
        )
      }
      sectionIndex++
    }
    return out
  }

  private fun chunkPlainFile(titleFile: String, text: String): List<TextChunk> {
    val pathHash = shortHash(titleFile)
    val pieces = splitOversizedSection(text, maxSectionChars, subWindow, subOverlap)
    return pieces.mapIndexed { i, piece ->
      val sectionLabel =
        if (pieces.size == 1) titleFile.substringAfterLast('/')
        else "${titleFile.substringAfterLast('/')} · part ${i + 1}/${pieces.size}"
      TextChunk(
        id = "STRUCTURE_${pathHash}_0_$i",
        strategy = ChunkingStrategy.STRUCTURE,
        source = source,
        titleFile = titleFile,
        section = sectionLabel,
        text = piece,
      )
    }
  }
}

internal fun splitOversizedSection(
  body: String,
  maxSectionChars: Int,
  subWindow: Int,
  subOverlap: Int,
): List<String> {
  if (body.length <= maxSectionChars) {
    return if (body.isEmpty()) emptyList() else listOf(body)
  }
  return splitFixedWindows(body, subWindow, subOverlap)
}

private fun shortHash(s: String): String {
  val md = MessageDigest.getInstance("SHA-256")
  val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
  return bytes.joinToString("") { b -> "%02x".format(b) }.take(12)
}
