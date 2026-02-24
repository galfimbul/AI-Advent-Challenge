package com.example.aiadventchallenge.data.agent

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [AgentMessageEntity::class],
  version = 1
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun agentMessageDao(): AgentMessageDao
}
