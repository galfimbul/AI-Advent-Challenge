package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_facts")
data class AgentFactsEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val factsText: String = ""
)
