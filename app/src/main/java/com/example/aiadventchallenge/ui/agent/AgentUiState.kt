package com.example.aiadventchallenge.ui.agent

import com.example.aiadventchallenge.data.agent.TaskMemoryItem
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.BranchInfo
import com.example.aiadventchallenge.domain.agent.ContextStrategy

/**
 * Состояние экрана «Агент»: история диалога (чат), ввод запроса, загрузка, ошибка, токены,
 * стратегия контекста (contextStrategy), ветки, facts, слои памяти (долговременная, задача).
 */
data class AgentUiState(
  val messages: List<AgentMessage> = emptyList(),
  val request: String = "",
  val isLoading: Boolean = false,
  val error: String? = null,
  val promptTokens: Int? = null,
  val completionTokens: Int? = null,
  val totalTokens: Int? = null,
  val useCompression: Boolean = false,
  val lastN: Int = 10,
  val contextStrategy: ContextStrategy = ContextStrategy.SlidingWindow,
  val currentBranchId: Long = 1L,
  val branches: List<BranchInfo> = emptyList(),
  val facts: String = "",
  /** Показать диалог ввода имени при создании ветки. */
  val showCreateBranchDialog: Boolean = false,
  /** Ввод имени новой ветки в диалоге. */
  val createBranchNameInput: String = "Ветка 2",
  val lastTokensModeCompression: Boolean? = null,
  val toastMessage: String? = null,
  val settingsSheetOpen: Boolean = false,
  /** Долговременная память (профиль, знания). */
  val longTermMemory: String = "",
  /** Список задач для памяти задачи. */
  val taskMemories: List<TaskMemoryItem> = emptyList(),
  /** Id задачи, загруженной в диалог (её контент подставляется в промпт). null — не загружена. */
  val loadedTaskId: Long? = null,
  /** Id задачи, открытой в редакторе памяти задачи (в настройках). */
  val taskEditorId: Long? = null,
  /** Имя задачи в редакторе. */
  val taskEditorName: String = "",
  /** Контент задачи в редакторе. */
  val taskEditorContent: String = "",
  /** Показать диалог подтверждения «Очистить историю». */
  val showClearConfirmDialog: Boolean = false,
  /** В диалоге очистки: также очистить память задачи. */
  val clearDialogAlsoTaskMemory: Boolean = false,
  /** Текст сообщения для long-tap «Извлечь факты и сохранить» (если не null — показать диалог выбора слоя). */
  val longTapMessageText: String? = null
)
