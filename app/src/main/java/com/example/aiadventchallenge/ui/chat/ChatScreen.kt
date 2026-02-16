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

@Composable
fun ChatScreen(
  modifier: Modifier = Modifier,
  viewModel: ChatViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "ChatGPT",
      style = MaterialTheme.typography.headlineMedium
    )

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

    if (uiState.error != null) {
      Text(
        text = uiState.error!!,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}
