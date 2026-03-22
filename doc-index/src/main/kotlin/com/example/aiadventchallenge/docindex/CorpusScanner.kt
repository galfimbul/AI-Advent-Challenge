package com.example.aiadventchallenge.docindex

import java.io.File
import java.nio.charset.StandardCharsets

class CorpusScanner(private val repoRoot: File) {

  fun loadDocuments(): List<CorpusDocument> {
    val files = linkedSetOf<File>()
    repoRoot.listFiles()?.forEach { f ->
      if (f.isFile && f.extension.equals("md", ignoreCase = true)) {
        files.add(f)
      }
    }
    val docsDir = File(repoRoot, "docs")
    if (docsDir.isDirectory) {
      docsDir.walkTopDown().forEach { f ->
        if (f.isFile && f.extension.equals("md", ignoreCase = true)) {
          files.add(f)
        }
      }
    }
    val docIndexDocs = File(repoRoot, "doc-index")
    if (docIndexDocs.isDirectory) {
      docIndexDocs.walkTopDown().forEach { f ->
        if (f.isFile && f.extension.equals("md", ignoreCase = true)) {
          files.add(f)
        }
      }
    }
    val appGradle = File(repoRoot, "app/build.gradle.kts")
    if (appGradle.isFile) {
      files.add(appGradle)
    }
    val docIndexKotlin = File(repoRoot, "doc-index/src/main/kotlin")
    if (docIndexKotlin.isDirectory) {
      docIndexKotlin.walkTopDown().forEach { f ->
        if (f.isFile && f.extension.equals("kt", ignoreCase = true)) {
          files.add(f)
        }
      }
    }
    val kotlinRoot = File(repoRoot, "app/src/main/java")
    if (kotlinRoot.isDirectory) {
      kotlinRoot.walkTopDown().forEach { f ->
        if (f.isFile && f.extension.equals("kt", ignoreCase = true)) {
          files.add(f)
        }
      }
    }
    val secretExample = File(repoRoot, "secret.properties.example")
    if (secretExample.isFile) {
      files.add(secretExample)
    }
    return files.sortedBy { it.invariantSeparatorsPath }.map { file ->
      val rel = relativize(file)
      val text = file.readText(StandardCharsets.UTF_8)
      CorpusDocument(titleFile = rel, text = text)
    }
  }

  private fun relativize(file: File): String {
    val rootPath = repoRoot.toPath().toAbsolutePath().normalize()
    val filePath = file.toPath().toAbsolutePath().normalize()
    return rootPath.relativize(filePath).toString().replace('\\', '/')
  }
}
