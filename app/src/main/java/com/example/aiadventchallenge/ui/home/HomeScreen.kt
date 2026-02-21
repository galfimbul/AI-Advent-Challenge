package com.example.aiadventchallenge.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.R

@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  onNavigate: (String) -> Unit = {}
) {
  var expandedSectionId by remember { mutableStateOf<String?>(null) }
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .padding(16.dp)
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color.Transparent),
      horizontalArrangement = Arrangement.Center
    ) {
      Image(
        painter = painterResource(R.drawable.home_ai_robot),
        contentDescription = "AI and robot",
        modifier = Modifier
          .fillMaxWidth(0.7f)
          .aspectRatio(1f)
          .background(Color.Transparent),
        contentScale = ContentScale.Fit
      )
    }
    Text(
      text = "Ai Advent Challenge With Love",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center
    )

    HOME_SECTIONS.forEach { section ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          expandedSectionId = if (expandedSectionId == section.id) null else section.id
        },
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = section.title,
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = if (expandedSectionId == section.id) " \u25B2" else " \u25BC",
              style = MaterialTheme.typography.labelMedium
            )
          }
          AnimatedVisibility(
            visible = expandedSectionId == section.id,
            enter = expandVertically(),
            exit = shrinkVertically()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              section.items.forEach { item ->
                Button(
                  onClick = { onNavigate(item.route) },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(item.label)
                }
              }
            }
          }
        }
      }
    }
  }
}
