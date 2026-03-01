package com.example.aiadventchallenge.data.agent

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    AgentMessageEntity::class,
    AgentSummaryEntity::class,
    AgentFactsEntity::class,
    AgentBranchEntity::class
  ],
  version = 3
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun agentMessageDao(): AgentMessageDao
  abstract fun agentSummaryDao(): AgentSummaryDao
  abstract fun agentFactsDao(): AgentFactsDao
  abstract fun agentBranchDao(): AgentBranchDao
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

val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_facts (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        factsText TEXT NOT NULL DEFAULT ''
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_branches (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        checkpointAt INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    db.execSQL("INSERT INTO agent_branches (id, name, checkpointAt) VALUES (1, 'Основная', 0)")
    db.execSQL("ALTER TABLE agent_messages ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
  }
}
