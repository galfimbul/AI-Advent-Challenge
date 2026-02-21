package com.example.aiadventchallenge.ui.discussion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.domain.ReasoningMode

@Composable
private fun DiscussionAnswerCard(
  title: String,
  answer: String?
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = answer.orEmpty(),
        onValueChange = { },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        minLines = 2,
        maxLines = 8,
        placeholder = { Text("Ответ появится здесь") }
      )
    }
  }
}

@Composable
fun DiscussionScreen(
  modifier: Modifier = Modifier,
  viewModel: DiscussionViewModel = viewModel(),
  onBack: () -> Unit = {}
) {
  val uiState by viewModel.uiState.collectAsState()
  val selfPromptGenerated = uiState.selfPromptGenerated

  Column(
    modifier = modifier
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
        text = "Обсуждение",
        style = MaterialTheme.typography.headlineMedium
      )
      Button(onClick = onBack) {
        Text("Назад")
      }
    }

    OutlinedTextField(
      value = uiState.task,
      onValueChange = viewModel::updateTask,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Задача") },
      placeholder = { Text("Введите задачу для решения разными способами...") },
      minLines = 2,
      maxLines = 4,
      enabled = uiState.loadingReasoningMode == null && !uiState.isRunningAll && !uiState.isComparing
    )

    val loading = uiState.loadingReasoningMode != null || uiState.isRunningAll || uiState.isComparing

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.runMode(ReasoningMode.Direct) },
        modifier = Modifier.weight(1f),
        enabled = !loading && uiState.task.isNotBlank()
      ) {
        Text(if (uiState.loadingReasoningMode == ReasoningMode.Direct) "Выполняется" else "Прямой")
      }
      Button(
        onClick = { viewModel.runMode(ReasoningMode.StepByStep) },
        modifier = Modifier.weight(1f),
        enabled = !loading && uiState.task.isNotBlank()
      ) {
        Text(if (uiState.loadingReasoningMode == ReasoningMode.StepByStep) "Выполняется" else "Пошагово")
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.runMode(ReasoningMode.SelfPrompt) },
        modifier = Modifier.weight(1f),
        enabled = !loading && uiState.task.isNotBlank()
      ) {
        Text(if (uiState.loadingReasoningMode == ReasoningMode.SelfPrompt) "Выполняется" else "Свой промпт")
      }
      Button(
        onClick = { viewModel.runMode(ReasoningMode.Experts) },
        modifier = Modifier.weight(1f),
        enabled = !loading && uiState.task.isNotBlank()
      ) {
        Text(if (uiState.loadingReasoningMode == ReasoningMode.Experts) "Выполняется" else "Эксперты")
      }
    }

    OutlinedButton(
      onClick = viewModel::runAll,
      modifier = Modifier.fillMaxWidth(),
      enabled = !loading && uiState.task.isNotBlank()
    ) {
      Text(if (uiState.isRunningAll) "Выполняется..." else "Запустить все")
    }

    if (loading) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator()
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Прямой ответ
    DiscussionAnswerCard(
      title = "Прямой ответ",
      answer = uiState.responseDirect
    )

    // Пошагово
    DiscussionAnswerCard(
      title = "Пошагово",
      answer = uiState.responseStepByStep
    )

    // Свой промпт — показываем сгенерированный моделью промпт
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = "Свой промпт",
          style = MaterialTheme.typography.titleSmall
        )
        if (!selfPromptGenerated.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Промпт от модели:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = selfPromptGenerated,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            minLines = 2,
            maxLines = 4,
            placeholder = { Text("Промпт появится после запроса") }
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = uiState.responseSelfPrompt.orEmpty(),
          onValueChange = { },
          modifier = Modifier.fillMaxWidth(),
          readOnly = true,
          minLines = 2,
          maxLines = 8,
          placeholder = { Text("Ответ появится здесь") }
        )
      }
    }

    // Эксперты — показываем используемые роли
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = "Эксперты",
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          text = "Роли: Аналитик, Инженер, Критик",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = uiState.responseExperts.orEmpty(),
          onValueChange = { },
          modifier = Modifier.fillMaxWidth(),
          readOnly = true,
          minLines = 2,
          maxLines = 8,
          placeholder = { Text("Ответ появится здесь") }
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = viewModel::compare,
        modifier = Modifier.weight(1f),
        enabled = uiState.hasAtLeastTwoResponses() && !loading && !uiState.isComparing
      ) {
        Text(if (uiState.isComparing) "Сравниваю..." else "Сравнить")
      }
      if (uiState.isComparing) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
      }
    }

    if (uiState.comparisonText != null) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "Сравнение",
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = uiState.comparisonText!!,
            style = MaterialTheme.typography.bodyMedium
          )
        }
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
