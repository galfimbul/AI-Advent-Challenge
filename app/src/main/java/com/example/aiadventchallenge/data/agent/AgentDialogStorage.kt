package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.domain.agent.BranchInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentDialogStorage(
  private val messageDao: AgentMessageDao,
  private val summaryDao: AgentSummaryDao,
  private val factsDao: AgentFactsDao,
  private val branchDao: AgentBranchDao
) {

  suspend fun load(currentBranchId: Long = 1L): AgentDialogState = withContext(Dispatchers.IO) {
    var branches = branchDao.getAllBranches()
    if (branches.isEmpty()) {
      branchDao.insert(AgentBranchEntity(id = 1L, name = "Основная", checkpointAt = 0))
      branches = branchDao.getAllBranches()
    }
    val branchId = if (currentBranchId in branches.map { it.id }) currentBranchId else branches.first().id
    val entities = messageDao.getMessagesByBranch(branchId)
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
    val facts = factsDao.getFacts()?.factsText ?: ""
    AgentDialogState(
      summaries = summaries,
      messages = messages,
      facts = facts,
      currentBranchId = branchId,
      branches = branches.map { BranchInfo(it.id, it.name) }
    )
  }

  suspend fun save(dialog: AgentDialogState) = withContext(Dispatchers.IO) {
    messageDao.deleteByBranch(dialog.currentBranchId)
    if (dialog.messages.isNotEmpty()) {
      val entities = dialog.messages.mapIndexed { index, msg ->
        AgentMessageEntity(
          role = when (msg.role) {
            AgentRole.User -> "User"
            AgentRole.Assistant -> "Assistant"
          },
          text = msg.text,
          sortOrder = index,
          branchId = dialog.currentBranchId
        )
      }
      messageDao.insertAll(entities)
    }
  }

  suspend fun saveFacts(factsText: String) = withContext(Dispatchers.IO) {
    val existing = factsDao.getFacts()
    if (existing != null) {
      factsDao.insert(AgentFactsEntity(id = existing.id, factsText = factsText))
    } else {
      factsDao.insert(AgentFactsEntity(factsText = factsText))
    }
  }

  suspend fun insertSummary(text: String, sortOrder: Int) = withContext(Dispatchers.IO) {
    summaryDao.insert(AgentSummaryEntity(text = text, sortOrder = sortOrder))
  }

  /** Создаёт вторую ветку от последнего сообщения текущей ветки. Возвращает id новой ветки. */
  suspend fun createBranch(currentBranchId: Long, lastMessageIndex: Int, secondBranchName: String): Long =
    withContext(Dispatchers.IO) {
      val entities = messageDao.getMessagesByBranch(currentBranchId)
      val toCopy = entities.filter { it.sortOrder <= lastMessageIndex }
      val newBranch = AgentBranchEntity(name = secondBranchName, checkpointAt = lastMessageIndex)
      val newId = branchDao.insert(newBranch)
      if (toCopy.isNotEmpty()) {
        val newEntities = toCopy.map { it.copy(id = 0, branchId = newId) }
        messageDao.insertAll(newEntities)
      }
      newId
    }

  suspend fun clear() = withContext(Dispatchers.IO) {
    messageDao.deleteAll()
    summaryDao.deleteAll()
    factsDao.deleteAll()
    branchDao.deleteAll()
    branchDao.insert(AgentBranchEntity(id = 1L, name = "Основная", checkpointAt = 0))
  }
}
