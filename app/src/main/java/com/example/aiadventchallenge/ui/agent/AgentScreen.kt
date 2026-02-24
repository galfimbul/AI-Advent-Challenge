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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.domain.agent.AgentMessage
import com.example.aiadventchallenge.domain.agent.AgentRole
import com.example.aiadventchallenge.ui.components.LoadingOverlay

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
          horizontalArrangement = Arrangement.End
        ) {
          Button(
            onClick = viewModel::sendRequest,
            enabled = !uiState.isLoading && uiState.request.isNotBlank()
          ) {
            Text(if (uiState.isLoading) "Отправка..." else "Отправить")
          }
        }
        if (uiState.promptTokens != null || uiState.completionTokens != null || uiState.totalTokens != null) {
          Text(
            text = "Токенов: " + listOfNotNull(
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
