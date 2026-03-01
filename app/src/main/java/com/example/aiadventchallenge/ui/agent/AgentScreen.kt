package com.example.aiadventchallenge.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.domain.agent.ContextStrategy
import com.example.aiadventchallenge.domain.agent.displayName
import com.example.aiadventchallenge.ui.components.LoadingOverlay

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
            onClick = viewModel::clearDialog,
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
          ChatBubble(message = message)
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
          if (BuildConfig.DEBUG) {
            OutlinedButton(
              onClick = viewModel::sendContextOverflowTest,
              enabled = !uiState.isLoading
            ) {
              Text("Превысить контекст")
            }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
          }
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

    LoadingOverlay(visible = uiState.isLoading)

    if (uiState.settingsSheetOpen) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = viewModel::closeSettingsSheet,
        sheetState = sheetState
      ) {
        AgentSettingsSheetContent(
          contextStrategy = uiState.contextStrategy,
          lastN = uiState.lastN,
          onStrategySelected = viewModel::setContextStrategy,
          onLastNSelected = viewModel::setLastN,
          onSave = viewModel::closeSettingsSheet
        )
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
  }
}

@Composable
private fun AgentSettingsSheetContent(
  contextStrategy: ContextStrategy,
  lastN: Int,
  onStrategySelected: (ContextStrategy) -> Unit,
  onLastNSelected: (Int) -> Unit,
  onSave: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Стратегия контекста",
      style = MaterialTheme.typography.titleMedium
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ContextStrategy.values().toList().chunked(2).forEach { rowStrategies ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          rowStrategies.forEach { strategy ->
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
    Button(
      onClick = onSave,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Сохранить")
    }
  }
}

@Composable
private fun ChatBubble(
  message: AgentMessage,
  modifier: Modifier = Modifier
) {
  val isUser = message.role == AgentRole.User
  Row(
    modifier = modifier.fillMaxWidth(),
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
