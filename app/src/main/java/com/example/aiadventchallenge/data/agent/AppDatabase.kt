package com.example.aiadventchallenge.data.agent

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [AgentMessageEntity::class, AgentSummaryEntity::class],
  version = 2
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun agentMessageDao(): AgentMessageDao
  abstract fun agentSummaryDao(): AgentSummaryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_summaries (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        text TEXT NOT NULL,
        sortOrder INTEGER NOT NULL
      )
      """.trimIndent()
    )
  }
}
