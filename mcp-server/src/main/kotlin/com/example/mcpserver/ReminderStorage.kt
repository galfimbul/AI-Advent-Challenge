package com.example.mcpserver

import java.sql.DriverManager
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Хранение напоминаний в SQLite (День 18).
 * Таблица: id, message, scheduled_at (ISO-8601), created_at (ISO-8601).
 */
class ReminderStorage(dbPath: String = "reminders.db") {

  private val lock = ReentrantLock()
  private val connection by lazy {
    val c = DriverManager.getConnection("jdbc:sqlite:$dbPath")
    initTable(c)
    c
  }

  private fun initTable(conn: java.sql.Connection) {
    conn.createStatement().executeUpdate(
      """
      CREATE TABLE IF NOT EXISTS reminders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        message TEXT NOT NULL,
        scheduled_at TEXT NOT NULL,
        created_at TEXT NOT NULL
      )
      """.trimIndent()
    )
  }

  data class Reminder(val id: Long, val message: String, val scheduledAt: String, val createdAt: String)

  fun insert(message: String, scheduledAt: Instant): Long = lock.withLock {
    val now = Instant.now().toString()
    val at = scheduledAt.toString()
    connection.prepareStatement(
      "INSERT INTO reminders (message, scheduled_at, created_at) VALUES (?, ?, ?)"
    ).use { stmt ->
      stmt.setString(1, message)
      stmt.setString(2, at)
      stmt.setString(3, now)
      stmt.executeUpdate()
    }
    connection.createStatement().executeQuery("SELECT last_insert_rowid()").use { rs ->
      if (rs.next()) rs.getLong(1) else 0L
    }
  }

  fun getAll(): List<Reminder> = lock.withLock {
    connection.createStatement().executeQuery(
      "SELECT id, message, scheduled_at, created_at FROM reminders ORDER BY scheduled_at ASC"
    ).use { rs ->
      val list = mutableListOf<Reminder>()
      while (rs.next()) {
        list.add(
          Reminder(
            id = rs.getLong("id"),
            message = rs.getString("message"),
            scheduledAt = rs.getString("scheduled_at"),
            createdAt = rs.getString("created_at")
          )
        )
      }
      list
    }
  }
}
