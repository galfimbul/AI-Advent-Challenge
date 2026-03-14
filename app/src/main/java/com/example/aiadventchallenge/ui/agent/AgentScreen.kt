package com.example.aiadventchallenge.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.data.agent.TaskMemoryItem
import com.example.aiadventchallenge.data.agent.UserProfileItem
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import com.example.aiadventchallenge.domain.agent.displayName
import com.example.aiadventchallenge.domain.agent.TaskStage
import com.example.aiadventchallenge.domain.agent.expectedActionText
import com.example.aiadventchallenge.ui.components.LoadingOverlay
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {}
) {
  val context = LocalContext.current
  val viewModel: AgentViewModel = viewModel(
    factory = AgentViewModelFactory(context.applicationContext)
  )
  val uiState by viewModel.uiState.collectAsState()
  val listState = rememberLazyListState()

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  LaunchedEffect(uiState.toastMessage) {
    uiState.toastMessage?.let { msg ->
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      viewModel.clearToastMessage()
    }
  }

  LaunchedEffect(Unit) {
    viewModel.onEnterScreen()
  }
  DisposableEffect(Unit) {
    onDispose { viewModel.onLeaveScreen() }
  }

  var showCommandsDialog by remember { mutableStateOf(false) }
  var showCommandArgDialog by remember { mutableStateOf(false) }
  var pendingCommand by remember { mutableStateOf("") }
  var commandArgText by remember { mutableStateOf("") }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .imePadding()
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Агент",
          style = MaterialTheme.typography.headlineMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = viewModel::openClearConfirmDialog,
            enabled = !uiState.isLoading && uiState.messages.isNotEmpty()
          ) {
            Text("Очистить историю")
          }
          Button(onClick = onBack) {
            Text("Назад")
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Стратегия: ${uiState.contextStrategy.displayName()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      val activeProfileName = uiState.activeProfileId?.let { id ->
        uiState.profiles.firstOrNull { it.id == id }?.name
      }
      Text(
        text = "Профиль: ${activeProfileName ?: "Без профиля"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      val longTermLabel = if (uiState.longTermMemory.isNotBlank()) "заполнена" else "пуста"
      val loadedTaskName = uiState.loadedTaskId?.let { id ->
        uiState.taskMemories.firstOrNull { it.id == id }?.name
      }
      Text(
        text = "Долговременная память: $longTermLabel",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = if (loadedTaskName != null) "Задача в диалоге: $loadedTaskName" else "Задача в диалоге: не загружена",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      uiState.loadedTaskState?.let { state ->
        Text(
          text = "Этап: ${state.stage.displayName()}" +
            (if (state.isPaused) ", на паузе" else ""),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Ожидаемое действие:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = state.stage.expectedActionText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
      }

      if (uiState.contextStrategy == ContextStrategy.StickyFacts) {
        Spacer(modifier = Modifier.height(4.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp)
        ) {
          Text(
            text = "Факты из диалога",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = uiState.facts.ifBlank { "(пока нет извлечённых фактов)" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
      }

      if (uiState.contextStrategy == ContextStrategy.Branching && uiState.branches.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          uiState.branches.forEach { branch ->
            val selected = branch.id == uiState.currentBranchId
            FilterChip(
              selected = selected,
              onClick = { viewModel.switchBranch(branch.id) },
              label = { Text(branch.name) }
            )
          }
          if (uiState.branches.size < 2) {
            OutlinedButton(
              onClick = viewModel::openCreateBranchDialog,
              enabled = !uiState.isLoading && uiState.messages.isNotEmpty()
            ) {
              Text("Создать ветку")
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
      }

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (uiState.messages.isEmpty()) {
          item {
            Text(
              text = "История диалога пуста. Напишите сообщение ниже.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        itemsIndexed(uiState.messages, key = { index, _ -> "msg_$index" }) { _, message ->
          ChatBubble(
            message = message,
            onLongPress = { viewModel.setLongTapMessage(message.text) }
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = uiState.request,
          onValueChange = viewModel::updateRequest,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Запрос") },
          placeholder = { Text("Введите сообщение...") },
          minLines = 2,
          maxLines = 4,
          enabled = !uiState.isLoading
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = viewModel::openSettingsSheet,
            enabled = !uiState.isLoading
          ) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки агента")
          }
          if (uiState.showContextOverflowButton) {
            OutlinedButton(
              onClick = viewModel::sendContextOverflowTest,
              enabled = !uiState.isLoading
            ) {
              Text("Превысить контекст")
            }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
          }
          OutlinedButton(
            onClick = { showCommandsDialog = true },
            enabled = !uiState.isLoading
          ) {
            Text("Команды")
          }
          Spacer(modifier = Modifier.padding(horizontal = 8.dp))
          Button(
            onClick = viewModel::sendRequest,
            enabled = !uiState.isLoading && uiState.request.isNotBlank()
          ) {
            Text(if (uiState.isLoading) "Отправка..." else "Отправить")
          }
        }
        if (uiState.promptTokens != null || uiState.completionTokens != null || uiState.totalTokens != null) {
          val modeLabel = when (uiState.lastTokensModeCompression) {
            true -> " (со сжатием)"
            false -> " (без сжатия)"
            null -> ""
          }
          Text(
            text = "Токенов$modeLabel: " + listOfNotNull(
              uiState.promptTokens?.let { "запрос $it" },
              uiState.completionTokens?.let { "ответ $it" },
              uiState.totalTokens?.let { "всего $it" }
            ).joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (uiState.error != null) {
          Text(
            text = uiState.error!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }

    if (showCommandsDialog) {
      AlertDialog(
        onDismissRequest = { showCommandsDialog = false },
        title = { Text("Команды") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
              viewModel.executeCommand("/start_task")
              showCommandsDialog = false
            }) { Text("/start_task — запустить задачу (цикл этапов)") }
            TextButton(onClick = {
              viewModel.executeCommand("/stop_task")
              showCommandsDialog = false
            }) { Text("/stop_task — остановить задачу") }
            TextButton(onClick = {
              viewModel.executeCommand("/confirm")
              showCommandsDialog = false
            }) { Text("/confirm — подтвердить результат, следующий этап") }
            TextButton(onClick = {
              viewModel.prepareReject()
              showCommandsDialog = false
            }) { Text("/reject — отклонить, добавить комментарий и отправить") }
            TextButton(onClick = {
              viewModel.executeCommand("/reset_planning")
              showCommandsDialog = false
            }) { Text("/reset_planning — сбросить задачу к планированию") }
            TextButton(onClick = {
              viewModel.executeCommand("/help")
              showCommandsDialog = false
            }) { Text("/help — подсказка по командам") }
            TextButton(onClick = {
              pendingCommand = "/add_long_term"
              commandArgText = ""
              showCommandsDialog = false
              showCommandArgDialog = true
            }) { Text("/add_long_term — добавить в долговременную память") }
            TextButton(onClick = {
              pendingCommand = "/add_task_memory"
              commandArgText = ""
              showCommandsDialog = false
              showCommandArgDialog = true
            }) { Text("/add_task_memory — добавить в память задачи") }
            TextButton(onClick = {
              viewModel.executeCommand("/tools")
              showCommandsDialog = false
            }) { Text("/tools — список MCP-инструментов") }
            TextButton(onClick = {
              pendingCommand = "/weather"
              commandArgText = ""
              showCommandsDialog = false
              showCommandArgDialog = true
            }) { Text("/weather — запросить погоду по городу") }
            TextButton(onClick = {
              pendingCommand = "/mock"
              commandArgText = ""
              showCommandsDialog = false
              showCommandArgDialog = true
            }) { Text("/mock — вызвать mock-инструмент MCP") }
          }
        },
        confirmButton = { TextButton(onClick = { showCommandsDialog = false }) { Text("Закрыть") } }
      )
    }
    if (showCommandArgDialog) {
      AlertDialog(
        onDismissRequest = { showCommandArgDialog = false; pendingCommand = ""; commandArgText = "" },
        title = {
          val titleText = when (pendingCommand) {
            "/add_long_term" -> "Текст для долговременной памяти"
            "/add_task_memory" -> "Текст для памяти задачи"
            "/weather" -> "Город для запроса погоды"
            "/mock" -> "Текст для mock (mock_echo)"
            else -> "Аргумент команды"
          }
          Text(titleText)
        },
        text = {
          OutlinedTextField(
            value = commandArgText,
            onValueChange = { commandArgText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Текст") },
            minLines = 2
          )
        },
        confirmButton = {
          Button(onClick = {
            viewModel.executeCommand("$pendingCommand $commandArgText".trim())
            showCommandArgDialog = false
            pendingCommand = ""
            commandArgText = ""
          }) { Text("Выполнить") }
        },
        dismissButton = {
          TextButton(onClick = { showCommandArgDialog = false; pendingCommand = ""; commandArgText = "" }) { Text("Отмена") }
        }
      )
    }

    LoadingOverlay(visible = uiState.isLoading || uiState.isMcpLoading)

    if (uiState.settingsSheetOpen) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
      ModalBottomSheet(
        onDismissRequest = viewModel::closeSettingsSheet,
        sheetState = sheetState
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .windowInsetsPadding(WindowInsets.systemBars)
        ) {
          AgentSettingsSheetContent(
            modifier = Modifier.fillMaxSize(),
            profiles = uiState.profiles,
            activeProfileId = uiState.activeProfileId,
            onSetActiveProfile = viewModel::setActiveProfile,
            profileEditorId = uiState.profileEditorId,
            profileEditorName = uiState.profileEditorName,
            profileEditorPreferences = uiState.profileEditorPreferences,
            onOpenProfileEditor = viewModel::openProfileEditor,
            onProfileEditorNameChange = viewModel::updateProfileEditorName,
            onProfileEditorPreferencesChange = viewModel::updateProfileEditorPreferences,
            onSaveProfileEditor = viewModel::saveProfileEditor,
            onCloseProfileEditor = viewModel::closeProfileEditor,
            onDeleteProfile = viewModel::deleteProfile,
            onAddProfile = viewModel::addProfile,
            contextStrategy = uiState.contextStrategy,
            lastN = uiState.lastN,
            longTermMemory = uiState.longTermMemory,
            onSaveLongTermMemory = viewModel::saveLongTermMemory,
            onClearLongTermMemory = viewModel::clearLongTermMemory,
            taskMemories = uiState.taskMemories,
            loadedTaskId = uiState.loadedTaskId,
            taskEditorId = uiState.taskEditorId,
            taskEditorName = uiState.taskEditorName,
            taskEditorContent = uiState.taskEditorContent,
            onOpenTaskEditor = viewModel::openTaskEditor,
            onTaskEditorNameChange = viewModel::updateTaskEditorName,
            onTaskEditorContentChange = viewModel::updateTaskEditorContent,
            onSaveTaskEditor = viewModel::saveTaskEditor,
            onDeleteTaskMemory = viewModel::deleteTaskMemory,
            onLoadTask = viewModel::loadTaskIntoDialog,
            onUnloadTask = viewModel::unloadTaskFromDialog,
            onClearTaskMemories = viewModel::clearTaskMemories,
            onAddTaskMemory = viewModel::saveTaskMemory,
            onStrategySelected = viewModel::setContextStrategy,
            onLastNSelected = viewModel::setLastN,
            showContextOverflowButton = uiState.showContextOverflowButton,
            onShowContextOverflowButtonChange = viewModel::setShowContextOverflowButton,
            invariantsText = uiState.invariantsText,
            onSaveInvariants = viewModel::saveInvariants
          )
        }
      }
    }

    if (uiState.showCreateBranchDialog) {
      AlertDialog(
        onDismissRequest = viewModel::dismissCreateBranchDialog,
        title = { Text("Создать ветку") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Введите имя новой ветки:",
              style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
              value = uiState.createBranchNameInput,
              onValueChange = viewModel::setCreateBranchNameInput,
              modifier = Modifier.fillMaxWidth(),
              label = { Text("Имя ветки") },
              singleLine = true
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { viewModel.createBranch(uiState.createBranchNameInput) }
          ) {
            Text("Создать")
          }
        },
        dismissButton = {
          TextButton(onClick = viewModel::dismissCreateBranchDialog) {
            Text("Отмена")
          }
        }
      )
    }

    if (uiState.longTapMessageText != null) {
      val messageText = uiState.longTapMessageText!!
      AlertDialog(
        onDismissRequest = { viewModel.setLongTapMessage(null) },
        confirmButton = { },
        title = { Text("Извлечь факты и сохранить") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Извлечь факты из сообщения и добавить в:",
              style = MaterialTheme.typography.bodyMedium
            )
            Button(
              onClick = { viewModel.addFactsToLongTermFromMessage(messageText) },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("В долговременную память")
            }
            for (task in uiState.taskMemories) {
              OutlinedButton(
                onClick = { viewModel.addFactsToTaskFromMessage(task.id, messageText) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("В задачу: ${task.name}")
              }
            }
            if (uiState.taskMemories.isEmpty()) {
              Text(
                text = "Нет задач. Добавьте в настройках.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { viewModel.setLongTapMessage(null) }) {
            Text("Отмена")
          }
        }
      )
    }

    if (uiState.showClearConfirmDialog) {
      AlertDialog(
        onDismissRequest = viewModel::dismissClearConfirmDialog,
        title = { Text("Очистить историю") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = "Удалить все сообщения текущего диалога? Долговременная память сохранится.",
              style = MaterialTheme.typography.bodyMedium
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = uiState.clearDialogAlsoTaskMemory,
                onCheckedChange = { viewModel.setClearDialogAlsoTaskMemory(it) }
              )
              Text(
                text = "Также очистить память задачи",
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        },
        confirmButton = {
          Button(onClick = viewModel::confirmClearDialog) {
            Text("Очистить")
          }
        },
        dismissButton = {
          TextButton(onClick = viewModel::dismissClearConfirmDialog) {
            Text("Отмена")
          }
        }
      )
    }
  }
}

@Composable
private fun CollapsibleSettingsBlock(
  title: String,
  expanded: Boolean,
  onToggle: () -> Unit,
  content: @Composable () -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = if (expanded) " \u25B2" else " \u25BC",
        style = MaterialTheme.typography.labelMedium
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        content()
      }
    }
  }
}

@Composable
private fun AgentSettingsSheetContent(
  modifier: Modifier = Modifier,
  profiles: List<UserProfileItem>,
  activeProfileId: Long?,
  onSetActiveProfile: (Long?) -> Unit,
  profileEditorId: Long?,
  profileEditorName: String,
  profileEditorPreferences: String,
  onOpenProfileEditor: (Long?) -> Unit,
  onProfileEditorNameChange: (String) -> Unit,
  onProfileEditorPreferencesChange: (String) -> Unit,
  onSaveProfileEditor: () -> Unit,
  onCloseProfileEditor: () -> Unit,
  onDeleteProfile: (Long) -> Unit,
  onAddProfile: (String, String) -> Unit,
  contextStrategy: ContextStrategy,
  lastN: Int,
  longTermMemory: String,
  onSaveLongTermMemory: (String) -> Unit,
  onClearLongTermMemory: () -> Unit,
  taskMemories: List<TaskMemoryItem>,
  loadedTaskId: Long?,
  taskEditorId: Long?,
  taskEditorName: String,
  taskEditorContent: String,
  onOpenTaskEditor: (Long) -> Unit,
  onTaskEditorNameChange: (String) -> Unit,
  onTaskEditorContentChange: (String) -> Unit,
  onSaveTaskEditor: () -> Unit,
  onDeleteTaskMemory: (Long) -> Unit,
  onLoadTask: (Long) -> Unit,
  onUnloadTask: () -> Unit,
  onClearTaskMemories: () -> Unit,
  onAddTaskMemory: (String, String) -> Unit,
  onStrategySelected: (ContextStrategy) -> Unit,
  onLastNSelected: (Int) -> Unit,
  showContextOverflowButton: Boolean = false,
  onShowContextOverflowButtonChange: (Boolean) -> Unit = {},
  invariantsText: String = "",
  onSaveInvariants: (String) -> Unit = {}
) {
  val focusManager = LocalFocusManager.current
  var longTermInput by remember { mutableStateOf(longTermMemory) }
  var newTaskName by remember { mutableStateOf("") }
  var newTaskContent by remember { mutableStateOf("") }
  var showAddProfileForm by remember { mutableStateOf(false) }
  var newProfileName by remember { mutableStateOf("") }
  var newProfilePreferences by remember { mutableStateOf("") }
  var profileIdToDelete by remember { mutableStateOf<Long?>(null) }
  var expandedSectionId by remember { mutableStateOf<String?>("profile") }
  var invariantsInput by remember { mutableStateOf(invariantsText) }
  LaunchedEffect(longTermMemory) { longTermInput = longTermMemory }
  LaunchedEffect(invariantsText) { invariantsInput = invariantsText }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(bottom = 32.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    CollapsibleSettingsBlock(
      title = "Профиль пользователя",
      expanded = expandedSectionId == "profile",
      onToggle = { expandedSectionId = if (expandedSectionId == "profile") null else "profile" }
    ) {
      Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      FilterChip(
        selected = activeProfileId == null,
        onClick = { onSetActiveProfile(null) },
        label = { Text("Без профиля") }
      )
    }
    if (profiles.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (profile in profiles) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            FilterChip(
              selected = activeProfileId == profile.id,
              onClick = { onSetActiveProfile(profile.id) },
              label = { Text(profile.name) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(onClick = { onOpenProfileEditor(profile.id) }) {
                Text("Изменить")
              }
              TextButton(onClick = { profileIdToDelete = profile.id }) {
                Text("Удалить")
              }
            }
          }
        }
      }
    }
    if (showAddProfileForm) {
      OutlinedTextField(
        value = newProfileName,
        onValueChange = { newProfileName = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Название профиля") },
        singleLine = true
      )
      OutlinedTextField(
        value = newProfilePreferences,
        onValueChange = { newProfilePreferences = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Предпочтения") },
        placeholder = { Text("Стиль, формат, ограничения…") },
        minLines = 2,
        maxLines = 5
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = {
            onAddProfile(newProfileName, newProfilePreferences)
            newProfileName = ""
            newProfilePreferences = ""
            showAddProfileForm = false
          }
        ) {
          Text("Сохранить")
        }
        TextButton(onClick = { showAddProfileForm = false; newProfileName = ""; newProfilePreferences = "" }) {
          Text("Отмена")
        }
      }
    } else if (profileEditorId != null) {
      Text(
        text = "Редактирование профиля",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      OutlinedTextField(
        value = profileEditorName,
        onValueChange = onProfileEditorNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Название") },
        singleLine = true
      )
      OutlinedTextField(
        value = profileEditorPreferences,
        onValueChange = onProfileEditorPreferencesChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Предпочтения") },
        placeholder = { Text("Стиль, формат, ограничения…") },
        minLines = 2,
        maxLines = 5
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(onClick = onSaveProfileEditor) {
          Text("Сохранить")
        }
        TextButton(onClick = onCloseProfileEditor) {
          Text("Отмена")
        }
        OutlinedButton(onClick = { profileIdToDelete = profileEditorId }) {
          Text("Удалить")
        }
      }
    } else {
      OutlinedButton(onClick = { showAddProfileForm = true }) {
        Text("Добавить профиль")
      }
    }
    }

    if (profileIdToDelete != null) {
      val id = profileIdToDelete!!
      val name = profiles.firstOrNull { it.id == id }?.name ?: "Профиль"
      AlertDialog(
        onDismissRequest = { profileIdToDelete = null },
        title = { Text("Удалить профиль?") },
        text = { Text("«$name» будет удалён.") },
        confirmButton = {
          Button(onClick = {
            onDeleteProfile(id)
            profileIdToDelete = null
          }) { Text("Удалить") }
        },
        dismissButton = {
          TextButton(onClick = { profileIdToDelete = null }) { Text("Отмена") }
        }
      )
    }

    CollapsibleSettingsBlock(
      title = "Долговременная память",
      expanded = expandedSectionId == "longTerm",
      onToggle = { expandedSectionId = if (expandedSectionId == "longTerm") null else "longTerm" }
    ) {
    OutlinedTextField(
      value = longTermInput,
      onValueChange = { longTermInput = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Профиль, решения, знания") },
      minLines = 2,
      maxLines = 5
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedButton(
        onClick = {
          onSaveLongTermMemory(longTermInput)
          focusManager.clearFocus()
        }
      ) {
        Text("Сохранить")
      }
      OutlinedButton(onClick = { onClearLongTermMemory(); focusManager.clearFocus() }) {
        Text("Очистить")
      }
    }
    }

    CollapsibleSettingsBlock(
      title = "Память задачи",
      expanded = expandedSectionId == "taskMemory",
      onToggle = { expandedSectionId = if (expandedSectionId == "taskMemory") null else "taskMemory" }
    ) {
    if (taskMemories.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (task in taskMemories) {
          FilterChip(
            selected = taskEditorId == task.id,
            onClick = { onOpenTaskEditor(task.id) },
            label = { Text(task.name) }
          )
        }
      }
    }
    if (taskEditorId != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Редактирование задачи",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      OutlinedTextField(
        value = taskEditorName,
        onValueChange = onTaskEditorNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Имя") },
        singleLine = true
      )
      OutlinedTextField(
        value = taskEditorContent,
        onValueChange = onTaskEditorContentChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Контент") },
        minLines = 2,
        maxLines = 6
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(onClick = onSaveTaskEditor) {
          Text("Сохранить изменения")
        }
        OutlinedButton(onClick = { onDeleteTaskMemory(taskEditorId) }) {
          Text("Удалить")
        }
      }
      if (loadedTaskId == taskEditorId) {
        OutlinedButton(onClick = onUnloadTask) {
          Text("Отключить задачу от диалога")
        }
      } else {
        OutlinedButton(onClick = { onLoadTask(taskEditorId) }) {
          Text("Загрузить задачу в диалог")
        }
      }
    }
    OutlinedTextField(
      value = newTaskName,
      onValueChange = { newTaskName = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Имя новой задачи") },
      singleLine = true
    )
    OutlinedTextField(
      value = newTaskContent,
      onValueChange = { newTaskContent = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Содержимое (опционально)") },
      minLines = 1,
      maxLines = 3
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedButton(
        onClick = {
          onAddTaskMemory(newTaskName, newTaskContent)
          newTaskName = ""
          newTaskContent = ""
        }
      ) {
        Text("Добавить задачу")
      }
      if (taskMemories.isNotEmpty()) {
        OutlinedButton(onClick = onClearTaskMemories) {
          Text("Очистить память задачи")
        }
      }
    }
    }

    CollapsibleSettingsBlock(
      title = "Стратегия контекста",
      expanded = expandedSectionId == "strategy",
      onToggle = { expandedSectionId = if (expandedSectionId == "strategy") null else "strategy" }
    ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (rowStrategies in ContextStrategy.values().toList().chunked(2)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          for (strategy in rowStrategies) {
            FilterChip(
              selected = contextStrategy == strategy,
              onClick = { onStrategySelected(strategy) },
              label = { Text(strategy.displayName()) }
            )
          }
        }
      }
    }
    Text(
      text = "Хранить полных сообщений (N)",
      style = MaterialTheme.typography.titleMedium
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf(5, 10, 20).forEach { n ->
        OutlinedButton(
          onClick = { onLastNSelected(n) }
        ) {
          Text(if (n == lastN) "$n ✓" else "$n")
        }
      }
    }
    }

    CollapsibleSettingsBlock(
      title = "Инварианты",
      expanded = expandedSectionId == "invariants",
      onToggle = { expandedSectionId = if (expandedSectionId == "invariants") null else "invariants" }
    ) {
    val invariantsCharLimit = 1500
    val invariantsOverLimit = invariantsInput.length > invariantsCharLimit
    OutlinedTextField(
      value = invariantsInput,
      onValueChange = { invariantsInput = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Правила, которые агент не должен нарушать") },
      placeholder = { Text("Правила, которые агент не должен нарушать (каждый с новой строки или произвольный текст)") },
      minLines = 3,
      maxLines = 20
    )
    if (invariantsOverLimit) {
      Text(
        text = "Большой объём текста увеличивает расход токенов. Рекомендуется до $invariantsCharLimit символов.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
      )
    }
    OutlinedButton(
      onClick = {
        onSaveInvariants(invariantsInput)
        focusManager.clearFocus()
      }
    ) {
      Text("Сохранить")
    }
    }

    CollapsibleSettingsBlock(
      title = "Отладка",
      expanded = expandedSectionId == "debug",
      onToggle = { expandedSectionId = if (expandedSectionId == "debug") null else "debug" }
    ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = showContextOverflowButton,
        onCheckedChange = onShowContextOverflowButtonChange
      )
      Text(
        text = "Показать кнопку «Превысить контекст»",
        style = MaterialTheme.typography.bodyMedium
      )
    }
    }
  }
}

@Composable
private fun ChatBubble(
  message: AgentMessage,
  onLongPress: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val isUser = message.role == AgentRole.User
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (onLongPress != null) Modifier.pointerInput(Unit) {
          detectTapGestures(onLongPress = { onLongPress() })
        } else Modifier
      ),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = 280.dp)
        .background(
          color = if (isUser) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
          shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
          )
        )
        .padding(12.dp)
    ) {
      Column {
        Text(
          text = if (isUser) "Вы" else "Агент",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium,
          color = if (isUser) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurface
          }
        )
      }
    }
  }
}
