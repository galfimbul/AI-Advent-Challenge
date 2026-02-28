package com.example.aiadventchallenge.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.agentCompressionDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_compression")

private val USE_COMPRESSION = booleanPreferencesKey("use_compression")
private val LAST_N_MESSAGES = intPreferencesKey("last_n_messages")

data class AgentCompressionSettings(
  val useCompression: Boolean = false,
  val lastN: Int = 10
)

class AgentCompressionPreferences(private val context: Context) {

  val settingsFlow: Flow<AgentCompressionSettings> = context.agentCompressionDataStore.data.map { prefs ->
    AgentCompressionSettings(
      useCompression = prefs[USE_COMPRESSION] ?: false,
      lastN = (prefs[LAST_N_MESSAGES] ?: 10).coerceIn(1, 100)
    )
  }

  suspend fun getSettings(): AgentCompressionSettings =
    context.agentCompressionDataStore.data.first().let { prefs ->
      AgentCompressionSettings(
        useCompression = prefs[USE_COMPRESSION] ?: false,
        lastN = (prefs[LAST_N_MESSAGES] ?: 10).coerceIn(1, 100)
      )
    }

  suspend fun setUseCompression(value: Boolean) {
    context.agentCompressionDataStore.edit { it[USE_COMPRESSION] = value }
  }

  suspend fun setLastN(value: Int) {
    context.agentCompressionDataStore.edit { it[LAST_N_MESSAGES] = value.coerceIn(1, 100) }
  }
}
