package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentSummaryDao {

  @Query("SELECT * FROM agent_summaries ORDER BY sortOrder ASC")
  suspend fun getAllSummaries(): List<AgentSummaryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: AgentSummaryEntity)

  @Query("DELETE FROM agent_summaries")
  suspend fun deleteAll()
}
