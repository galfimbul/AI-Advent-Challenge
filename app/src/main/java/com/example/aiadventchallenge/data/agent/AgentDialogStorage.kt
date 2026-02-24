package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentDialogStorage(
  private val dao: AgentMessageDao
) {

  suspend fun load(): AgentDialogState = withContext(Dispatchers.IO) {
    val entities = dao.getAllMessages()
    val messages = entities.map { entity ->
      AgentMessage(
        role = when (entity.role) {
          "User" -> AgentRole.User
          else -> AgentRole.Assistant
        },
        text = entity.text
      )
    }
    AgentDialogState(messages = messages)
  }

  suspend fun save(dialog: AgentDialogState) = withContext(Dispatchers.IO) {
    dao.deleteAll()
    if (dialog.messages.isNotEmpty()) {
      val entities = dialog.messages.mapIndexed { index, msg ->
        AgentMessageEntity(
          role = when (msg.role) {
            AgentRole.User -> "User"
            AgentRole.Assistant -> "Assistant"
          },
          text = msg.text,
          sortOrder = index
        )
      }
      dao.insertAll(entities)
    }
  }

  suspend fun clear() = withContext(Dispatchers.IO) {
    dao.deleteAll()
  }
}
