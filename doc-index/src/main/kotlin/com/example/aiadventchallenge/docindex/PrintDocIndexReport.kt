package com.example.aiadventchallenge.docindex

import java.io.File
import java.sql.DriverManager

fun main(args: Array<String>) {
  var dbPath: String? = null
  var reportOut: String? = null
  var i = 0
  while (i < args.size) {
    when (args[i]) {
      "--db" -> dbPath = args.getOrNull(++i) ?: error("--db requires path")
      "--report-out" -> reportOut = args.getOrNull(++i) ?: error("--report-out requires path")
      else -> error("Unknown argument: ${args[i]}")
    }
    i++
  }
  val dbFile = File(dbPath ?: error("Missing --db"))
  if (!dbFile.isFile) {
    error("DB file not found: ${dbFile.absolutePath}")
  }
  val reportFile = File(reportOut ?: error("Missing --report-out"))
  reportFile.parentFile?.mkdirs()
  val report = StringBuilder()
  val url = "jdbc:sqlite:${dbFile.absolutePath}"
  DriverManager.getConnection(url).use { conn ->
    conn.createStatement().use { st ->
      report.appendLine("# Doc index report")
      report.appendLine()
      report.appendLine("Database: `${dbFile.absolutePath}`")
      report.appendLine()
      report.appendLine("## index_meta")
      report.appendLine()
      st.executeQuery("SELECT key, value FROM index_meta ORDER BY key").use { rs ->
        while (rs.next()) {
          val line = "- **${rs.getString(1)}**: ${rs.getString(2)}"
          report.appendLine(line)
          println(line)
        }
      }
      report.appendLine()
      report.appendLine("## Aggregates by chunking_strategy")
      report.appendLine()
      val aggSql =
        """
        SELECT chunking_strategy,
               COUNT(*) AS n,
               MIN(LENGTH(text)) AS min_len,
               AVG(LENGTH(text)) AS avg_len,
               MAX(LENGTH(text)) AS max_len
        FROM chunks
        GROUP BY chunking_strategy
        ORDER BY chunking_strategy
        """.trimIndent()
      val aggHeader = "| strategy | count | min_len | avg_len | max_len |"
      val aggSep = "| --- | ---: | ---: | ---: | ---: |"
      report.appendLine(aggHeader)
      report.appendLine(aggSep)
      println(aggHeader)
      println(aggSep)
      st.executeQuery(aggSql).use { rs ->
        while (rs.next()) {
          val line =
            "| ${rs.getString(1)} | ${rs.getInt(2)} | ${rs.getInt(3)} | " +
              "%.1f".format(rs.getDouble(4)) + " | ${rs.getInt(5)} |"
          println(line)
          report.appendLine(line)
        }
      }
      report.appendLine()
      println()
      report.appendLine("## Sample chunks (up to 3 per strategy)")
      report.appendLine()
      for (strategy in listOf("FIXED_WINDOW", "STRUCTURE")) {
        report.appendLine("### $strategy")
        report.appendLine()
        val sampleSql =
          """
          SELECT id, title_file, section, LENGTH(text) AS text_len, text
          FROM chunks
          WHERE chunking_strategy = ?
          LIMIT 3
          """.trimIndent()
        conn.prepareStatement(sampleSql).use { ps ->
          ps.setString(1, strategy)
          ps.executeQuery().use { rs ->
            var n = 0
            while (rs.next()) {
              n++
              val id = rs.getString(1)
              val file = rs.getString(2)
              val section = rs.getString(3)
              val len = rs.getInt(4)
              val text = rs.getString(5)
              val preview = text.replace("\n", " ").take(280)
              val block =
                """
                **$n.** `$id`
                - file: `$file`
                - section: $section
                - length: $len
                - preview: $preview

                """.trimIndent()
              report.appendLine(block)
              println(block)
            }
          }
        }
      }
    }
  }
  reportFile.writeText(report.toString())
  println()
  println("Report written to ${reportFile.absolutePath}")
}
