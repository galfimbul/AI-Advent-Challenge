package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentLongTermMemoryDao {

  @Query("SELECT * FROM agent_long_term_memory WHERE id = 1 LIMIT 1")
  suspend fun get(): AgentLongTermMemoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: AgentLongTermMemoryEntity)
}
