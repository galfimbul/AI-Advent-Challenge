package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentFactsDao {

  @Query("SELECT * FROM agent_facts LIMIT 1")
  suspend fun getFacts(): AgentFactsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: AgentFactsEntity)

  @Query("DELETE FROM agent_facts")
  suspend fun deleteAll()
}
