package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentMessageDao {

  @Query("SELECT * FROM agent_messages ORDER BY sortOrder ASC")
  suspend fun getAllMessages(): List<AgentMessageEntity>

  @Query("SELECT * FROM agent_messages WHERE branchId = :branchId ORDER BY sortOrder ASC")
  suspend fun getMessagesByBranch(branchId: Long): List<AgentMessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(entities: List<AgentMessageEntity>)

  @Query("DELETE FROM agent_messages")
  suspend fun deleteAll()

  @Query("DELETE FROM agent_messages WHERE branchId = :branchId")
  suspend fun deleteByBranch(branchId: Long)
}
