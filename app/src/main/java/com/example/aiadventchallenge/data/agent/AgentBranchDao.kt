package com.example.aiadventchallenge.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentBranchDao {

  @Query("SELECT * FROM agent_branches ORDER BY id ASC")
  suspend fun getAllBranches(): List<AgentBranchEntity>

  @Query("SELECT * FROM agent_branches WHERE id = :branchId LIMIT 1")
  suspend fun getBranchById(branchId: Long): AgentBranchEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: AgentBranchEntity): Long

  @Query("DELETE FROM agent_branches")
  suspend fun deleteAll()
}
