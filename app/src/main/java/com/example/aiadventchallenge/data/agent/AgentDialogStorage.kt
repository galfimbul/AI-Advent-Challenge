package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentDialogStorage(
  private val messageDao: AgentMessageDao,
  private val summaryDao: AgentSummaryDao
) {

  suspend fun load(): AgentDialogState = withContext(Dispatchers.IO) {
    val entities = messageDao.getAllMessages()
    val messages = entities.map { entity ->
      AgentMessage(
        role = when (entity.role) {
          "User" -> AgentRole.User
          else -> AgentRole.Assistant
        },
        text = entity.text
      )
    }
    val summaries = summaryDao.getAllSummaries().map { it.text }
    AgentDialogState(summaries = summaries, messages = messages)
  }

  suspend fun save(dialog: AgentDialogState) = withContext(Dispatchers.IO) {
    messageDao.deleteAll()
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
      messageDao.insertAll(entities)
    }
  }

  suspend fun insertSummary(text: String, sortOrder: Int) = withContext(Dispatchers.IO) {
    summaryDao.insert(AgentSummaryEntity(text = text, sortOrder = sortOrder))
  }

  suspend fun clear() = withContext(Dispatchers.IO) {
    messageDao.deleteAll()
    summaryDao.deleteAll()
  }
}
