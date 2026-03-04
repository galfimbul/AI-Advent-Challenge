package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_long_term_memory")
data class AgentLongTermMemoryEntity(
  @PrimaryKey
  val id: Long = 1L,
  val content: String = ""
)
