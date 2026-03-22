package com.example.aiadventchallenge.ui.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.AiAdventChallengeApplication
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentPreferences
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.reminder.AppReminderScheduler
import com.example.aiadventchallenge.data.rag.OllamaEmbeddingClient
import com.example.aiadventchallenge.data.rag.OllamaHostResolver
import com.example.aiadventchallenge.data.rag.RagContextBuilder
import com.example.aiadventchallenge.domain.agent.SimpleAgent

class AgentViewModelFactory(
  private val context: Context
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val app = context.applicationContext as AiAdventChallengeApplication
    val db = app.database
    val storage = AgentDialogStorage(
      db.agentMessageDao(),
      db.agentSummaryDao(),
      db.agentFactsDao(),
      db.agentBranchDao(),
      db.agentLongTermMemoryDao(),
      db.agentTaskMemoryDao(),
      db.agentUserProfileDao()
    )
    val repository = ChatRepository()
    val agent = SimpleAgent(repository)
    val agentPreferences = AgentPreferences(context)
    val reminderScheduler = AppReminderScheduler(context.applicationContext)
    val ragContextBuilder =
      if (BuildConfig.OLLAMA_HOST.isNotBlank()) {
        val url = OllamaHostResolver.normalize(BuildConfig.OLLAMA_HOST)
        if (url.isNotEmpty()) {
          RagContextBuilder(context.applicationContext, OllamaEmbeddingClient(url))
        } else {
          null
        }
      } else {
        null
      }
    return AgentViewModel(agent, storage, repository, agentPreferences, reminderScheduler, ragContextBuilder) as T
  }
}
