package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val role: String,
  val text: String,
  val sortOrder: Int
)
