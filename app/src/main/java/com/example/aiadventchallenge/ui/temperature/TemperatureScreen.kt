package com.example.aiadventchallenge.ui.temperature

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadventchallenge.ui.components.LoadingOverlay
import com.example.aiadventchallenge.domain.TEMPERATURE_PRESETS
import com.example.aiadventchallenge.domain.labelForTemperature
import com.example.aiadventchallenge.domain.MAX_TEMP
import com.example.aiadventchallenge.domain.MIN_TEMP
import com.example.aiadventchallenge.domain.TEMP_STEP

@Composable
private fun TemperatureRunCard(
  title: String,
  response: String,
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
          text = title,
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          text = if (isExpanded) "▲ Свернуть" else "▼ Развернуть",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }
      AnimatedVisibility(visible = isExpanded) {
        Column {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = response,
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
fun TemperatureScreen(
  modifier: Modifier = Modifier,
  viewModel: TemperatureViewModel = viewModel(),
  onBack: () -> Unit = {}
) {
  val uiState by viewModel.uiState.collectAsState()
  val loading = uiState.loadingTemperature != null || uiState.isRunningAll || uiState.isComparing
  val canEdit = !loading
  val collapsedIndices = remember { mutableStateSetOf<Int>() }

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
        text = "Температура",
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
      placeholder = { Text("Введите один и тот же запрос для запуска с разной температурой...") },
      minLines = 2,
      maxLines = 4,
      enabled = canEdit
    )

    // Ползунок 0–2, шаг 0.1
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "Температура: ${"%.1f".format(uiState.sliderTemperature)}",
        style = MaterialTheme.typography.labelMedium
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Slider(
          value = uiState.sliderTemperature,
          onValueChange = { viewModel.updateSlider(it) },
          valueRange = MIN_TEMP..MAX_TEMP,
          steps = ((MAX_TEMP - MIN_TEMP) / TEMP_STEP).toInt() - 1,
          modifier = Modifier.weight(1f)
        )
        Button(
          onClick = viewModel::runWithSlider,
          enabled = canEdit && uiState.prompt.isNotBlank()
        ) {
          Text(if (uiState.loadingTemperature == uiState.sliderTemperature) "..." else "Запустить")
        }
      }
    }

    // Пресеты
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      TEMPERATURE_PRESETS.forEach { (temp, label) ->
        Button(
          onClick = { viewModel.runTemperature(temp) },
          modifier = Modifier.weight(1f),
          enabled = canEdit && uiState.prompt.isNotBlank()
        ) {
          Text(
            if (uiState.loadingTemperature == temp) "..." else label
          )
        }
      }
    }

    OutlinedButton(
      onClick = viewModel::runAll,
      modifier = Modifier.fillMaxWidth(),
      enabled = canEdit && uiState.prompt.isNotBlank()
    ) {
      Text(if (uiState.isRunningAll) "Выполняется..." else "Запустить все три")
    }

    Spacer(modifier = Modifier.height(8.dp))

    uiState.runs.forEachIndexed { index, run ->
      TemperatureRunCard(
        title = labelForTemperature(run.temp),
        response = run.response,
        isExpanded = index !in collapsedIndices,
        onToggle = {
          if (index in collapsedIndices) collapsedIndices.remove(index)
          else collapsedIndices.add(index)
        }
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = viewModel::compare,
        modifier = Modifier.weight(1f),
        enabled = uiState.hasAtLeastTwoRuns() && !loading
      ) {
        Text(if (uiState.isComparing) "Сравниваю..." else "Сравнить")
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

    LoadingOverlay(visible = loading)
  }
}
