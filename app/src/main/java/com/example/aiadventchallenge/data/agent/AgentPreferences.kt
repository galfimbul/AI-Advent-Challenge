package com.example.aiadventchallenge.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.agentDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_compression")

private val USE_COMPRESSION = booleanPreferencesKey("use_compression")
private val LAST_N_MESSAGES = intPreferencesKey("last_n_messages")
private val CONTEXT_STRATEGY = stringPreferencesKey("context_strategy")
private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
private val SHOW_CONTEXT_OVERFLOW_BUTTON = booleanPreferencesKey("show_context_overflow_button")
private val AGENT_INVARIANTS = stringPreferencesKey("agent_invariants")

data class AgentCompressionSettings(
  val useCompression: Boolean = false,
  val lastN: Int = 10,
  val contextStrategy: ContextStrategy = ContextStrategy.SlidingWindow
)

class AgentPreferences(private val context: Context) {

  val settingsFlow: Flow<AgentCompressionSettings> = context.agentDataStore.data.map { prefs ->
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
    context.agentDataStore.data.first().let { prefs ->
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
    context.agentDataStore.edit {
      it[USE_COMPRESSION] = value
      if (value) it[CONTEXT_STRATEGY] = ContextStrategy.Summary.name
    }
  }

  suspend fun setLastN(value: Int) {
    context.agentDataStore.edit { it[LAST_N_MESSAGES] = value.coerceIn(1, 100) }
  }

  suspend fun setContextStrategy(value: ContextStrategy) {
    context.agentDataStore.edit {
      it[CONTEXT_STRATEGY] = value.name
      it[USE_COMPRESSION] = (value == ContextStrategy.Summary)
    }
  }

  suspend fun getActiveProfileId(): Long? {
    val id = context.agentDataStore.data.first()[ACTIVE_PROFILE_ID]
    return if (id == null || id == 0L) null else id
  }

  suspend fun setActiveProfileId(id: Long?) {
    context.agentDataStore.edit {
      it[ACTIVE_PROFILE_ID] = id ?: 0L
    }
  }

  suspend fun getShowContextOverflowButton(): Boolean =
    context.agentDataStore.data.first()[SHOW_CONTEXT_OVERFLOW_BUTTON] ?: false

  suspend fun setShowContextOverflowButton(value: Boolean) {
    context.agentDataStore.edit { it[SHOW_CONTEXT_OVERFLOW_BUTTON] = value }
  }

  suspend fun getInvariantsText(): String =
    context.agentDataStore.data.first()[AGENT_INVARIANTS] ?: ""

  suspend fun setInvariantsText(value: String) {
    context.agentDataStore.edit { it[AGENT_INVARIANTS] = value }
  }
}
