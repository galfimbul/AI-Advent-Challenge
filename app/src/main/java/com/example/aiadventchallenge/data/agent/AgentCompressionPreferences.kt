package com.example.aiadventchallenge.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.agentCompressionDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_compression")

private val USE_COMPRESSION = booleanPreferencesKey("use_compression")
private val LAST_N_MESSAGES = intPreferencesKey("last_n_messages")
private val CONTEXT_STRATEGY = stringPreferencesKey("context_strategy")

data class AgentCompressionSettings(
  val useCompression: Boolean = false,
  val lastN: Int = 10,
  val contextStrategy: ContextStrategy = ContextStrategy.SlidingWindow
)

class AgentCompressionPreferences(private val context: Context) {

  val settingsFlow: Flow<AgentCompressionSettings> = context.agentCompressionDataStore.data.map { prefs ->
    val strategyName = prefs[CONTEXT_STRATEGY]
    val strategy = strategyName?.let { name ->
      try {
        ContextStrategy.valueOf(name)
      } catch (_: Exception) {
        ContextStrategy.SlidingWindow
      }
    } ?: (if (prefs[USE_COMPRESSION] == true) ContextStrategy.Summary else ContextStrategy.SlidingWindow)
    AgentCompressionSettings(
      useCompression = prefs[USE_COMPRESSION] ?: false,
      lastN = (prefs[LAST_N_MESSAGES] ?: 10).coerceIn(1, 100),
      contextStrategy = strategy
    )
  }

  suspend fun getSettings(): AgentCompressionSettings =
    context.agentCompressionDataStore.data.first().let { prefs ->
      val strategyName = prefs[CONTEXT_STRATEGY]
      val strategy = strategyName?.let { name ->
        try {
          ContextStrategy.valueOf(name)
        } catch (_: Exception) {
          ContextStrategy.SlidingWindow
        }
      } ?: (if (prefs[USE_COMPRESSION] == true) ContextStrategy.Summary else ContextStrategy.SlidingWindow)
      AgentCompressionSettings(
        useCompression = prefs[USE_COMPRESSION] ?: false,
        lastN = (prefs[LAST_N_MESSAGES] ?: 10).coerceIn(1, 100),
        contextStrategy = strategy
      )
    }

  suspend fun setUseCompression(value: Boolean) {
    context.agentCompressionDataStore.edit {
      it[USE_COMPRESSION] = value
      if (value) it[CONTEXT_STRATEGY] = ContextStrategy.Summary.name
    }
  }

  suspend fun setLastN(value: Int) {
    context.agentCompressionDataStore.edit { it[LAST_N_MESSAGES] = value.coerceIn(1, 100) }
  }

  suspend fun setContextStrategy(value: ContextStrategy) {
    context.agentCompressionDataStore.edit {
      it[CONTEXT_STRATEGY] = value.name
      it[USE_COMPRESSION] = (value == ContextStrategy.Summary)
    }
  }

}
