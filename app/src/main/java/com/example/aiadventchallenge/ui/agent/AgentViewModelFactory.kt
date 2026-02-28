package com.example.aiadventchallenge.ui.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.AiAdventChallengeApplication
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentCompressionPreferences
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.domain.agent.SimpleAgent

class AgentViewModelFactory(
  private val context: Context
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val app = context.applicationContext as AiAdventChallengeApplication
    val db = app.database
    val storage = AgentDialogStorage(db.agentMessageDao(), db.agentSummaryDao())
    val repository = ChatRepository()
    val agent = SimpleAgent(repository)
    val compressionPreferences = AgentCompressionPreferences(context)
    return AgentViewModel(agent, storage, repository, compressionPreferences) as T
  }
}
