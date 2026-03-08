package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_task_memories")
data class AgentTaskMemoryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val content: String,
  /** Этап задачи: planning | execution | validation | done (День 13). */
  val stage: String = "planning",
  val currentStep: Int = 0,
  /** 0 = активна, 1 = на паузе. */
  val isPaused: Int = 0
)
