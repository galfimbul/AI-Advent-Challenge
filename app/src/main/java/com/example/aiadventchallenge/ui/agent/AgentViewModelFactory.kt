package com.example.aiadventchallenge.ui.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.AiAdventChallengeApplication
import com.example.aiadventchallenge.data.ChatRepository
import com.example.aiadventchallenge.data.agent.AgentDialogStorage
import com.example.aiadventchallenge.domain.agent.SimpleAgent

class AgentViewModelFactory(
  private val context: Context
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val app = context.applicationContext as AiAdventChallengeApplication
    val dao = app.database.agentMessageDao()
    val storage = AgentDialogStorage(dao)
    val agent = SimpleAgent(ChatRepository())
    return AgentViewModel(agent, storage) as T
  }
}
