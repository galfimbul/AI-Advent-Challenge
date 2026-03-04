package com.example.aiadventchallenge.data.agent

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_branches")
data class AgentBranchEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  /** sortOrder последнего сообщения на момент создания ветки (для второй ветки). */
  val checkpointAt: Int = 0,
  /** Id задачи, подключённой к этому диалогу (ветке). null — не подключена. */
  val loadedTaskId: Long? = null
)
