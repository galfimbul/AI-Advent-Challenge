package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AgentTaskMemoryDao {

  @Query("SELECT * FROM agent_task_memories ORDER BY id")
  suspend fun getAll(): List<AgentTaskMemoryEntity>

  @Query("SELECT * FROM agent_task_memories WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): AgentTaskMemoryEntity?

  @Insert
  suspend fun insert(entity: AgentTaskMemoryEntity): Long

  @Query("UPDATE agent_task_memories SET name = :name, content = :content WHERE id = :id")
  suspend fun updateNameAndContent(id: Long, name: String, content: String)

  @Query("UPDATE agent_task_memories SET content = :content WHERE id = :id")
  suspend fun updateContent(id: Long, content: String)

  @Query("DELETE FROM agent_task_memories WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM agent_task_memories")
  suspend fun deleteAll()
}
