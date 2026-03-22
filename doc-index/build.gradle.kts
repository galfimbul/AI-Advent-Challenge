import org.gradle.api.tasks.testing.Test

plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.okhttp)
  implementation(libs.gson)
  implementation(libs.sqlite.jdbc)
  testImplementation(libs.junit)
}

application {
  mainClass = "com.example.aiadventchallenge.docindex.IndexMainKt"
}

val repoRoot = rootProject.layout.projectDirectory.asFile
val indexOutputFile = layout.buildDirectory.file("doc_index.sqlite")
val reportOutputDir = layout.buildDirectory.dir("reports")
val reportOutputFile = layout.buildDirectory.file("reports/doc_index_report.md")

tasks.register<JavaExec>("buildDocIndex") {
  group = "doc index"
  description = "Build doc_index.sqlite via Ollama embeddings (requires Ollama running)."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.example.aiadventchallenge.docindex.IndexMainKt")
  workingDir = repoRoot
  args(
    "--repo-root",
    repoRoot.absolutePath,
    "--output",
    indexOutputFile.get().asFile.absolutePath,
  )
  environment("OLLAMA_HOST", System.getenv("OLLAMA_HOST") ?: "")
  outputs.file(indexOutputFile)
}

tasks.register<JavaExec>("printDocIndexReport") {
  group = "doc index"
  description = "Print and write doc index summary (runs buildDocIndex first)."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.example.aiadventchallenge.docindex.PrintDocIndexReportKt")
  workingDir = repoRoot
  dependsOn("buildDocIndex")
  args(
    "--db",
    indexOutputFile.get().asFile.absolutePath,
    "--report-out",
    reportOutputFile.get().asFile.absolutePath,
  )
  inputs.file(indexOutputFile)
  outputs.file(reportOutputFile)
  doFirst {
    reportOutputDir.get().asFile.mkdirs()
  }
}

tasks.named<Test>("test") {
  useJUnit()
}

tasks.named<JavaExec>("run") {
  workingDir = repoRoot
  environment("OLLAMA_HOST", System.getenv("OLLAMA_HOST") ?: "")
  args(
    "--repo-root",
    repoRoot.absolutePath,
    "--output",
    indexOutputFile.get().asFile.absolutePath,
  )
}
