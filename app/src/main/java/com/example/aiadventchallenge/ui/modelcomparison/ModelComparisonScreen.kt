package com.example.aiadventchallenge.ui.modelcomparison

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.data.ModelRunResult
import com.example.aiadventchallenge.ui.components.LoadingOverlay

private val LINKS = listOf(
  "Модели OpenAI" to "https://platform.openai.com/docs/models",
  "Тарифы OpenAI" to "https://openai.com/api/pricing"
)

/** Примеры запросов для оценки ограничений и выбора модели. */
private val EXAMPLE_PROMPTS = listOf(
  "Рассуждение по шагам" to "Пошагово реши: у Пети 3 яблока, у Маши на 2 больше. Сколько всего яблок? Ответь одним числом.",
  "Факты и даты" to "В каком году распался СССР? Перечисли три республики, первыми объявившие независимость.",
  "Креатив" to "Придумай короткое стихотворение (4 строки) про программиста и кофе.",
  "Сложная инструкция" to "Сравни в двух предложениях плюсы и минусы ООП и функционального программирования.",
  "Тонкости языка" to "Объясни в одном предложении разницу между словами «подозревать» и «предполагать».",
  "Ограничения и отказ" to "Напиши код на Python для обхода капчи на сайте."
)

@Composable
private fun ModelRunCard(
  run: ModelRunResult,
  isExpanded: Boolean,
  onToggle: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = run.displayName,
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          text = if (isExpanded) "▲ Свернуть" else "▼ Развернуть",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "${run.responseTimeMs} мс",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Токенов: ${run.totalTokens ?: "—"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        run.costUsd?.let { cost ->
          Text(
            text = "$${"%.4f".format(cost)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      AnimatedVisibility(visible = isExpanded) {
        Column {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = run.content,
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
  }
}

@Composable
fun ModelComparisonScreen(
  modifier: Modifier = Modifier,
  viewModel: ModelComparisonViewModel = viewModel(),
  onBack: () -> Unit = {}
) {
  val uiState by viewModel.uiState.collectAsState()
  val loading = uiState.isLoading || uiState.isComparing
  val canEdit = !loading
  val collapsedIndices = remember { mutableStateSetOf<Int>() }
  val context = LocalContext.current

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
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
          text = "Версии моделей",
          style = MaterialTheme.typography.headlineMedium
        )
        Button(onClick = onBack) {
          Text("Назад")
        }
      }

      OutlinedTextField(
        value = uiState.prompt,
        onValueChange = viewModel::updatePrompt,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Запрос") },
        placeholder = { Text("Введите один и тот же запрос для трёх моделей...") },
        minLines = 2,
        maxLines = 4,
        enabled = canEdit
      )

      Text(
        text = "Примеры запросов",
        style = MaterialTheme.typography.titleSmall
      )
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        EXAMPLE_PROMPTS.forEach { (label, prompt) ->
          OutlinedButton(
            onClick = { viewModel.updatePrompt(prompt) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1
            )
          }
        }
      }

      Button(
        onClick = viewModel::runAllThree,
        modifier = Modifier.fillMaxWidth(),
        enabled = canEdit && uiState.prompt.isNotBlank()
      ) {
        Text(if (uiState.isLoading) "Выполняется..." else "Запустить все три модели")
      }

      Spacer(modifier = Modifier.height(8.dp))

      uiState.runs.forEachIndexed { index, run ->
        ModelRunCard(
          run = run,
          isExpanded = index !in collapsedIndices,
          onToggle = {
            if (index in collapsedIndices) collapsedIndices.remove(index)
            else collapsedIndices.add(index)
          }
        )
      }

      if (uiState.hasAllThreeRuns() && !loading) {
        Button(
          onClick = viewModel::compare,
          modifier = Modifier.fillMaxWidth(),
          enabled = !uiState.isComparing
        ) {
          Text(if (uiState.isComparing) "Сравниваю..." else "Сравнить")
        }
      }

      if (uiState.conclusionText != null) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          )
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "Вывод",
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = uiState.conclusionText!!,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }

      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Ссылки",
          style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        LINKS.forEach { (label, url) ->
          Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
              }
              .padding(vertical = 4.dp)
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

    LoadingOverlay(visible = loading)
  }
}
