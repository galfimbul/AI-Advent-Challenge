package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_summaries")
data class AgentSummaryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val text: String,
  val sortOrder: Int
)
