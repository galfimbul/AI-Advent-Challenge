package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AgentUserProfileDao {

  @Query("SELECT * FROM agent_user_profiles ORDER BY id")
  suspend fun getAll(): List<AgentUserProfileEntity>

  @Query("SELECT * FROM agent_user_profiles WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): AgentUserProfileEntity?

  @Insert
  suspend fun insert(entity: AgentUserProfileEntity): Long

  @Query("UPDATE agent_user_profiles SET name = :name, preferences = :preferences WHERE id = :id")
  suspend fun update(id: Long, name: String, preferences: String)

  @Query("DELETE FROM agent_user_profiles WHERE id = :id")
  suspend fun deleteById(id: Long)
}
