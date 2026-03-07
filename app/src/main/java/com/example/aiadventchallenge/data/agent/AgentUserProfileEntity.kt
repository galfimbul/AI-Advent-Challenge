package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_user_profiles")
data class AgentUserProfileEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val preferences: String
)
