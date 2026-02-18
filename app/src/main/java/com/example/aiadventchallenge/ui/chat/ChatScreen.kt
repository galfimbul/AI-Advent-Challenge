package com.example.aiadventchallenge.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.data.STOP_SEQUENCE

@Composable
fun ChatScreen(
  modifier: Modifier = Modifier,
  viewModel: ChatViewModel = viewModel(),
  onNavigateToDiscussion: () -> Unit = {}
) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "ChatGPT",
        style = MaterialTheme.typography.headlineMedium
      )
      Button(onClick = onNavigateToDiscussion) {
        Text("Обсуждение")
      }
    }

    OutlinedTextField(
      value = uiState.query,
      onValueChange = viewModel::updateQuery,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Ваш запрос") },
      placeholder = { Text("Введите сообщение...") },
      minLines = 2,
      maxLines = 4,
      enabled = !uiState.isLoading
    )

    Text(
      text = "Настройки запроса",
      style = MaterialTheme.typography.titleSmall
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = if (uiState.unlimitedTokens) "" else uiState.maxTokensInput,
        onValueChange = viewModel::updateMaxTokens,
        modifier = Modifier.weight(1f),
        label = { Text("Макс. токенов") },
        placeholder = { Text("256") },
        enabled = !uiState.isLoading && !uiState.unlimitedTokens,
        singleLine = true
      )
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = uiState.unlimitedTokens,
          onCheckedChange = viewModel::setUnlimitedTokens,
          enabled = !uiState.isLoading
        )
        Text(
          text = "Без ограничения",
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }

    if (uiState.isLoading) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator()
      }
    }
    Button(
      onClick = viewModel::sendRequest,
      modifier = Modifier.align(Alignment.End),
      enabled = !uiState.isLoading && uiState.query.isNotBlank()
    ) {
      Text(if (uiState.isLoading) "Отправка..." else "Отправить")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Ответ",
      style = MaterialTheme.typography.titleMedium
    )

    OutlinedTextField(
      value = uiState.response,
      onValueChange = { },
      modifier = Modifier.fillMaxWidth(),
      readOnly = true,
      minLines = 6,
      maxLines = 20,
      placeholder = { Text("Ответ от ChatGPT появится здесь") }
    )

    if (uiState.totalTokens != null || uiState.finishReason != null) {
      val parts = buildList<String> {
        if (uiState.promptTokens != null || uiState.completionTokens != null || uiState.totalTokens != null) {
          add(
            "Токенов: " + listOfNotNull(
              uiState.promptTokens?.let { "запрос $it" },
              uiState.completionTokens?.let { "ответ $it" },
              uiState.totalTokens?.let { "всего $it" }
            ).joinToString(", ")
          )
        }
        uiState.finishReason?.let { reason ->
          add("Причина завершения: ${when (reason) {
            "stop" -> "модель закончила сама или достигла stop sequence: $STOP_SEQUENCE"
            "length" -> "достигнут лимит токенов"
            else -> reason
          }}")
        }
      }
      if (parts.isNotEmpty()) {
        Text(
          text = parts.joinToString(" • "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
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
