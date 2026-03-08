package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.domain.agent.AgentDialogState
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.domain.agent.BranchInfo
import com.example.aiadventchallenge.domain.agent.TaskStage
import com.example.aiadventchallenge.domain.agent.TaskState
import com.example.aiadventchallenge.domain.agent.asString
import com.example.aiadventchallenge.domain.agent.taskStageFromString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Элемент списка памяти задач (id + имя) для UI. */
data class TaskMemoryItem(val id: Long, val name: String)

/** Элемент списка профилей пользователя (id + имя) для UI. */
data class UserProfileItem(val id: Long, val name: String)

class AgentDialogStorage(
  private val messageDao: AgentMessageDao,
  private val summaryDao: AgentSummaryDao,
  private val factsDao: AgentFactsDao,
  private val branchDao: AgentBranchDao,
  private val longTermMemoryDao: AgentLongTermMemoryDao,
  private val taskMemoryDao: AgentTaskMemoryDao,
  private val userProfileDao: AgentUserProfileDao
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
    val loadedTaskId = branchDao.getBranchById(branchId)?.loadedTaskId
    AgentDialogState(
      summaries = summaries,
      messages = messages,
      facts = facts,
      currentBranchId = branchId,
      branches = branches.map { BranchInfo(it.id, it.name) },
      loadedTaskId = loadedTaskId
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

  // --- Долговременная память (не очищается при clear()) ---

  suspend fun getLongTermMemory(): String = withContext(Dispatchers.IO) {
    longTermMemoryDao.get()?.content ?: ""
  }

  suspend fun saveLongTermMemory(content: String) = withContext(Dispatchers.IO) {
    longTermMemoryDao.insert(AgentLongTermMemoryEntity(id = 1L, content = content))
  }

  suspend fun appendToLongTermMemory(additionalText: String) = withContext(Dispatchers.IO) {
    val current = longTermMemoryDao.get()?.content ?: ""
    val separator = if (current.isNotEmpty()) "\n\n" else ""
    longTermMemoryDao.insert(AgentLongTermMemoryEntity(id = 1L, content = current + separator + additionalText))
  }

  // --- Память задачи (очищается через clearTaskMemories()) ---

  suspend fun getTaskMemories(): List<TaskMemoryItem> = withContext(Dispatchers.IO) {
    taskMemoryDao.getAll().map { TaskMemoryItem(it.id, it.name) }
  }

  suspend fun getTaskMemoryContent(id: Long): String? = withContext(Dispatchers.IO) {
    taskMemoryDao.getById(id)?.content
  }

  /** Состояние задачи (этап, шаг, пауза). Ожидаемое действие вычисляется по этапу в коде. */
  suspend fun getTaskState(id: Long): TaskState? = withContext(Dispatchers.IO) {
    val entity = taskMemoryDao.getById(id) ?: return@withContext null
    TaskState(
      stage = taskStageFromString(entity.stage),
      currentStep = entity.currentStep,
      isPaused = entity.isPaused != 0
    )
  }

  suspend fun updateTaskState(id: Long, stage: TaskStage, currentStep: Int, isPaused: Boolean) = withContext(Dispatchers.IO) {
    taskMemoryDao.updateTaskState(id, stage.asString(), currentStep, if (isPaused) 1 else 0)
  }

  suspend fun saveTaskMemory(name: String, content: String): Long = withContext(Dispatchers.IO) {
    taskMemoryDao.insert(AgentTaskMemoryEntity(name = name, content = content))
  }

  suspend fun appendToTaskMemory(id: Long, additionalText: String) = withContext(Dispatchers.IO) {
    val existing = taskMemoryDao.getById(id) ?: return@withContext
    val separator = if (existing.content.isNotEmpty()) "\n\n" else ""
    taskMemoryDao.updateContent(id, existing.content + separator + additionalText)
  }

  suspend fun updateTaskMemory(id: Long, name: String, content: String) = withContext(Dispatchers.IO) {
    taskMemoryDao.updateNameAndContent(id, name, content)
  }

  suspend fun deleteTaskMemory(id: Long) = withContext(Dispatchers.IO) {
    taskMemoryDao.deleteById(id)
  }

  suspend fun clearTaskMemories() = withContext(Dispatchers.IO) {
    taskMemoryDao.deleteAll()
  }

  /** Сохраняет id подключённой к ветке задачи (null = отключить). */
  suspend fun saveLoadedTaskIdForBranch(branchId: Long, taskId: Long?) = withContext(Dispatchers.IO) {
    branchDao.setLoadedTaskId(branchId, taskId)
  }

  // --- Профили пользователя ---

  suspend fun getAllProfiles(): List<UserProfileItem> = withContext(Dispatchers.IO) {
    userProfileDao.getAll().map { UserProfileItem(it.id, it.name) }
  }

  suspend fun getProfileContent(id: Long): String? = withContext(Dispatchers.IO) {
    userProfileDao.getById(id)?.preferences
  }

  suspend fun saveProfile(id: Long?, name: String, preferences: String): Long = withContext(Dispatchers.IO) {
    if (id == null || id == 0L) {
      userProfileDao.insert(AgentUserProfileEntity(name = name, preferences = preferences))
    } else {
      userProfileDao.update(id, name, preferences)
      id
    }
  }

  suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
    userProfileDao.deleteById(id)
  }
}
