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
    AgentBranchEntity::class,
    AgentLongTermMemoryEntity::class,
    AgentTaskMemoryEntity::class,
    AgentUserProfileEntity::class
  ],
  version = 8
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun agentMessageDao(): AgentMessageDao
  abstract fun agentSummaryDao(): AgentSummaryDao
  abstract fun agentFactsDao(): AgentFactsDao
  abstract fun agentBranchDao(): AgentBranchDao
  abstract fun agentLongTermMemoryDao(): AgentLongTermMemoryDao
  abstract fun agentTaskMemoryDao(): AgentTaskMemoryDao
  abstract fun agentUserProfileDao(): AgentUserProfileDao
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

val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_long_term_memory (
        id INTEGER PRIMARY KEY NOT NULL,
        content TEXT NOT NULL DEFAULT ''
      )
      """.trimIndent()
    )
    db.execSQL("INSERT OR IGNORE INTO agent_long_term_memory (id, content) VALUES (1, '')")
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_task_memories (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        content TEXT NOT NULL
      )
      """.trimIndent()
    )
  }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE agent_branches ADD COLUMN loadedTaskId INTEGER NULL")
  }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS agent_user_profiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        preferences TEXT NOT NULL DEFAULT ''
      )
      """.trimIndent()
    )
  }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE agent_task_memories ADD COLUMN stage TEXT NOT NULL DEFAULT 'planning'")
    db.execSQL("ALTER TABLE agent_task_memories ADD COLUMN currentStep INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE agent_task_memories ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
  }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE agent_branches ADD COLUMN stage TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE agent_branches ADD COLUMN currentStep INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE agent_branches ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
  }
}
