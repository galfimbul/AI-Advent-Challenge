package com.example.aiadventchallenge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Полноэкранный оверлей с лоудером по центру.
 * Перекрывает контент и системные панели (статус-бар и навигационную панель).
 * Светлый фон для хорошей видимости индикатора.
 *
 * @param visible показывать оверлей
 * @param modifier модификатор для корневого Box (обычно не нужен при использовании внутри Box с fillMaxSize)
 */
@Composable
fun LoadingOverlay(
  visible: Boolean,
  modifier: Modifier = Modifier
) {
  if (!visible) return

  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val systemBars = WindowInsets.systemBars
  val insetsLeft = systemBars.getLeft(density, layoutDirection)
  val insetsTop = systemBars.getTop(density)
  val insetsRight = systemBars.getRight(density, layoutDirection)
  val insetsBottom = systemBars.getBottom(density)

  Box(
    modifier = modifier
      .fillMaxSize()
      .layout { measurable, constraints ->
        val expandedConstraints = constraints.copy(
          maxWidth = constraints.maxWidth + insetsLeft + insetsRight,
          maxHeight = constraints.maxHeight + insetsTop + insetsBottom
        )
        val placeable = measurable.measure(expandedConstraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
          placeable.placeRelative(-insetsLeft, -insetsTop)
        }
      }
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White.copy(alpha = 0.6f)),
      contentAlignment = Alignment.Center
    ) {
      CircularProgressIndicator()
    }
  }
}
